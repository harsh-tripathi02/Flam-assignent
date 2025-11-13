package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.Log
import android.view.TextureView
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.myapplication.camera.CameraManager
import com.example.myapplication.gl.GLRenderer
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val CAMERA_PERMISSION_REQUEST = 100
    }

    private lateinit var textureView: TextureView
    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var processedImageView: ImageView
    private lateinit var fpsTextView: TextView
    private lateinit var processingTimeTextView: TextView
    private lateinit var resolutionTextView: TextView
    private lateinit var processingModeSpinner: Spinner

    private var cameraManager: CameraManager? = null
    private val nativeProcessor = NativeProcessor()
    private lateinit var glRenderer: GLRenderer

    private var currentMode = NativeProcessor.MODE_RAW
    private var frameCount = 0
    private var lastFpsTime = System.currentTimeMillis()
    private var currentFps = 0.0

    private val mainScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize views
        textureView = findViewById(R.id.textureView)
        glSurfaceView = findViewById(R.id.glSurfaceView)
        processedImageView = findViewById(R.id.processedImageView)
        fpsTextView = findViewById(R.id.fpsTextView)
        processingTimeTextView = findViewById(R.id.processingTimeTextView)
        resolutionTextView = findViewById(R.id.resolutionTextView)
        processingModeSpinner = findViewById(R.id.processingModeSpinner)

        // Setup GLSurfaceView
        setupGLSurfaceView()

        // Setup processing mode spinner
        setupProcessingModeSpinner()

        // Initialize native processor
        if (!nativeProcessor.initOpenCV()) {
            Toast.makeText(this, "Failed to initialize OpenCV", Toast.LENGTH_LONG).show()
            Log.e(TAG, "OpenCV initialization failed")
        } else {
            Log.i(TAG, "OpenCV initialized successfully")
        }

        // Request camera permission
        if (checkCameraPermission()) {
            setupCamera()
        } else {
            requestCameraPermission()
        }
    }

    private fun setupProcessingModeSpinner() {
        val modes = arrayOf("Raw", "Grayscale", "Canny Edge")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        processingModeSpinner.adapter = adapter

        processingModeSpinner.setSelection(0)
        processingModeSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                currentMode = position
                Log.d(TAG, "Processing mode changed to: ${modes[position]}")
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun setupGLSurfaceView() {
        // Setup OpenGL ES 2.0
        glSurfaceView.setEGLContextClientVersion(2)

        // Create renderer
        glRenderer = GLRenderer()
        glSurfaceView.setRenderer(glRenderer)

        // Render on demand (we'll call requestRender when frame is ready)
        glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY

        // TEMPORARY: Show raw camera until we debug GLSurfaceView rendering issue
        // GLSurfaceView is causing black screen - need to check why
        glSurfaceView.visibility = View.GONE
        textureView.visibility = View.VISIBLE

        Log.d(TAG, "GLSurfaceView setup complete - TextureView visible (debugging mode)")
    }

    private fun setupCamera() {
        cameraManager = CameraManager(
            context = this,
            textureView = textureView,
            onFrameAvailable = { frameData, width, height ->
                processFrame(frameData, width, height)
            }
        )
    }

    private fun processFrame(frameData: ByteArray, width: Int, height: Int) {
        mainScope.launch(Dispatchers.Default) {
            val startTime = System.currentTimeMillis()

            try {
                // Process frame using Kotlin processor
                val processedFrame = nativeProcessor.processFrame(
                    frameData,
                    width,
                    height,
                    currentMode
                )

                val processingTime = System.currentTimeMillis() - startTime

                // Display processed frame on ImageView
                if (processedFrame != null) {
                    // Convert RGB byte array to Bitmap
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val pixels = IntArray(width * height)

                    for (i in 0 until width * height) {
                        val r = processedFrame[i * 3].toInt() and 0xFF
                        val g = processedFrame[i * 3 + 1].toInt() and 0xFF
                        val b = processedFrame[i * 3 + 2].toInt() and 0xFF
                        pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                    }

                    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)

                    // Rotate bitmap 90 degrees for portrait display
                    val matrix = Matrix()
                    matrix.postRotate(90f)
                    val rotatedBitmap = Bitmap.createBitmap(
                        bitmap, 0, 0, width, height, matrix, true
                    )

                    // Update UI on main thread
                    withContext(Dispatchers.Main) {
                        processedImageView.setImageBitmap(rotatedBitmap)

                        // Show processed view for non-raw modes, hide for raw
                        if (currentMode == NativeProcessor.MODE_RAW) {
                            textureView.visibility = View.VISIBLE
                            processedImageView.visibility = View.GONE
                        } else {
                            textureView.visibility = View.GONE
                            processedImageView.visibility = View.VISIBLE
                        }
                    }

                    // Recycle original bitmap to free memory
                    bitmap.recycle()
                }

                // Update FPS
                frameCount++
                val currentTime = System.currentTimeMillis()
                val elapsedTime = currentTime - lastFpsTime
                if (elapsedTime >= 1000) {
                    currentFps = frameCount * 1000.0 / elapsedTime
                    frameCount = 0
                    lastFpsTime = currentTime

                    withContext(Dispatchers.Main) {
                        fpsTextView.text = "FPS: %.1f".format(currentFps)
                        processingTimeTextView.text = "Processing: %d ms".format(processingTime)
                        resolutionTextView.text = "Resolution: ${width}x${height}"
                    }
                }

                if (frameCount % 30 == 0) {
                    Log.d(TAG, "Frame rendered: ${width}x${height}, FPS: %.1f, Time: ${processingTime}ms".format(currentFps))
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error processing frame", e)
            }
        }
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.d(TAG, "onRequestPermissionsResult: requestCode=$requestCode")

        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "Camera permission GRANTED")
                Toast.makeText(this, "Camera permission granted, starting camera...", Toast.LENGTH_SHORT).show()
                try {
                    setupCamera()
                    cameraManager?.start()
                    Log.d(TAG, "Camera started successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Error starting camera after permission", e)
                    Toast.makeText(this, "Error starting camera: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } else {
                Log.w(TAG, "Camera permission DENIED")
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        glSurfaceView.onResume()
        if (checkCameraPermission()) {
            cameraManager?.start()
        }
    }

    override fun onPause() {
        super.onPause()
        glSurfaceView.onPause()
        cameraManager?.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        mainScope.cancel()
        cameraManager?.stop()
    }
}