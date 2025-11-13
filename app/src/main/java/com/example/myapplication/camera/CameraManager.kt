package com.example.myapplication.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.core.content.ContextCompat

/**
 * Camera2 API manager for capturing frames
 */
class CameraManager(
    private val context: Context,
    private val textureView: TextureView,
    private val onFrameAvailable: (ByteArray, Int, Int) -> Unit
) {
    companion object {
        private const val TAG = "CameraManager"
        private const val IMAGE_WIDTH = 640
        private const val IMAGE_HEIGHT = 480
    }

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager

    // TextureView listener
    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            Log.d(TAG, "Surface texture available: ${width}x${height}")
            openCamera()
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
            Log.d(TAG, "Surface texture size changed: ${width}x${height}")
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            Log.d(TAG, "Surface texture destroyed")
            return true
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
            // Called when texture is updated
        }
    }

    // Camera device state callback
    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            Log.d(TAG, "Camera opened successfully")
            cameraDevice = camera
            createCameraPreviewSession()
        }

        override fun onDisconnected(camera: CameraDevice) {
            Log.d(TAG, "Camera disconnected")
            camera.close()
            cameraDevice = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            Log.e(TAG, "Camera error: $error")
            camera.close()
            cameraDevice = null
        }
    }

    // ImageReader listener for frame processing
    private val imageReaderListener = ImageReader.OnImageAvailableListener { reader ->
        val image = reader.acquireLatestImage() ?: return@OnImageAvailableListener

        try {
            // Get YUV planes
            val planes = image.planes
            val yPlane = planes[0]
            val uPlane = planes[1]
            val vPlane = planes[2]

            val yBuffer = yPlane.buffer
            val uBuffer = uPlane.buffer
            val vBuffer = vPlane.buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            // Combine into NV21 format
            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)

            // Convert UV to VU (NV21 format)
            val pixelStride = uPlane.pixelStride
            var pos = ySize
            for (i in 0 until uSize step pixelStride) {
                nv21[pos++] = vBuffer.get(i)
                nv21[pos++] = uBuffer.get(i)
            }

            // Send to processing callback
            onFrameAvailable(nv21, image.width, image.height)

        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame", e)
        } finally {
            image.close()
        }
    }

    fun start() {
        Log.d(TAG, "CameraManager.start() called")
        try {
            startBackgroundThread()
            Log.d(TAG, "TextureView available: ${textureView.isAvailable}")
            if (textureView.isAvailable) {
                Log.d(TAG, "TextureView already available, opening camera")
                openCamera()
            } else {
                Log.d(TAG, "TextureView not available, setting listener")
                textureView.surfaceTextureListener = surfaceTextureListener
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in start()", e)
        }
    }

    fun stop() {
        closeCamera()
        stopBackgroundThread()
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also {
            it.start()
            backgroundHandler = Handler(it.looper)
        }
        Log.d(TAG, "Background thread started")
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, "Error stopping background thread", e)
        }
    }

    private fun openCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Camera permission not granted")
            return
        }

        try {
            val cameraId = getBackCameraId() ?: run {
                Log.e(TAG, "No back camera found")
                return
            }

            cameraManager.openCamera(cameraId, stateCallback, backgroundHandler)
            Log.d(TAG, "Opening camera: $cameraId")

        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to open camera", e)
        }
    }

    private fun closeCamera() {
        captureSession?.close()
        captureSession = null

        cameraDevice?.close()
        cameraDevice = null

        imageReader?.close()
        imageReader = null

        Log.d(TAG, "Camera closed")
    }

    private fun getBackCameraId(): String? {
        return cameraManager.cameraIdList.firstOrNull { id ->
            val characteristics = cameraManager.getCameraCharacteristics(id)
            characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
    }

    private fun createCameraPreviewSession() {
        val camera = cameraDevice ?: return

        try {
            val surfaceTexture = textureView.surfaceTexture ?: return
            surfaceTexture.setDefaultBufferSize(IMAGE_WIDTH, IMAGE_HEIGHT)

            val previewSurface = Surface(surfaceTexture)

            // Create ImageReader for frame processing
            imageReader = ImageReader.newInstance(
                IMAGE_WIDTH, IMAGE_HEIGHT,
                ImageFormat.YUV_420_888, 2
            ).also {
                it.setOnImageAvailableListener(imageReaderListener, backgroundHandler)
            }

            val surfaces = listOf(previewSurface, imageReader!!.surface)

            // Create capture request
            val captureRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(previewSurface)
                addTarget(imageReader!!.surface)
                // Auto focus
                set(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                )
            }

            // Create capture session using modern API
            val sessionCallback = object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    if (cameraDevice == null) return

                    captureSession = session
                    try {
                        // Start capturing
                        session.setRepeatingRequest(
                            captureRequestBuilder.build(),
                            null,
                            backgroundHandler
                        )
                        Log.d(TAG, "Camera preview started")
                    } catch (e: CameraAccessException) {
                        Log.e(TAG, "Failed to start preview", e)
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(TAG, "Camera configuration failed")
                }
            }

            // Use newer API if available (API 28+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val outputConfigs = surfaces.map {
                    android.hardware.camera2.params.OutputConfiguration(it)
                }
                val sessionConfig = android.hardware.camera2.params.SessionConfiguration(
                    android.hardware.camera2.params.SessionConfiguration.SESSION_REGULAR,
                    outputConfigs,
                    { it.run() },
                    sessionCallback
                )
                camera.createCaptureSession(sessionConfig)
            } else {
                @Suppress("DEPRECATION")
                camera.createCaptureSession(surfaces, sessionCallback, backgroundHandler)
            }

        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to create preview session", e)
        }
    }

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
    }
}

