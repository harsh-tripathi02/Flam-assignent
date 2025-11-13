package com.example.myapplication

import android.util.Log

/**
 * Native processor using OpenCV for image processing
 */
class NativeProcessor {

    companion object {
        private const val TAG = "NativeProcessor"

        // Processing modes
        const val MODE_RAW = 0
        const val MODE_GRAYSCALE = 1
        const val MODE_CANNY = 2

        init {
            try {
                System.loadLibrary("opencv_java4")
                System.loadLibrary("native-processor")
                Log.i(TAG, "Native libraries loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native libraries", e)
            }
        }
    }

    /**
     * Initialize OpenCV
     * @return true if initialization successful
     */
    external fun initOpenCV(): Boolean

    /**
     * Process a camera frame
     * @param inputFrame YUV frame data from camera
     * @param width frame width
     * @param height frame height
     * @param mode processing mode (RAW, GRAYSCALE, CANNY)
     * @return processed frame as RGB byte array
     */
    external fun processFrame(
        inputFrame: ByteArray,
        width: Int,
        height: Int,
        mode: Int
    ): ByteArray?

    /**
     * Process frame and upload directly to OpenGL texture
     * @param inputFrame YUV frame data from camera
     * @param width frame width
     * @param height frame height
     * @param mode processing mode
     * @param textureId OpenGL texture ID
     */
    external fun processFrameToTexture(
        inputFrame: ByteArray,
        width: Int,
        height: Int,
        mode: Int,
        textureId: Int
    )
}

