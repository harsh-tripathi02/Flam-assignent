package com.example.myapplication

import android.util.Log
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Processor using Kotlin implementation (native libs disabled temporarily)
 */
class NativeProcessor {

    companion object {
        private const val TAG = "NativeProcessor"

        // Processing modes
        const val MODE_RAW = 0
        const val MODE_GRAYSCALE = 1
        const val MODE_CANNY = 2
    }

    /**
     * Initialize processor
     */
    fun initOpenCV(): Boolean {
        Log.i(TAG, "Processor initialized (Kotlin implementation)")
        return true
    }

    /**
     * Process a camera frame in Kotlin
     */
    fun processFrame(
        inputFrame: ByteArray,
        width: Int,
        height: Int,
        mode: Int
    ): ByteArray? {
        return try {
            when (mode) {
                MODE_RAW -> processRaw(inputFrame, width, height)
                MODE_GRAYSCALE -> processGrayscale(inputFrame, width, height)
                MODE_CANNY -> processCanny(inputFrame, width, height)
                else -> processRaw(inputFrame, width, height)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame", e)
            null
        }
    }

    private fun processRaw(yuv: ByteArray, width: Int, height: Int): ByteArray {
        val rgb = ByteArray(width * height * 3)
        yuvToRgb(yuv, rgb, width, height)
        return rgb
    }

    private fun processGrayscale(yuv: ByteArray, width: Int, height: Int): ByteArray {
        val rgb = ByteArray(width * height * 3)
        yuvToRgb(yuv, rgb, width, height)

        // Convert to grayscale
        for (i in 0 until width * height) {
            val idx = i * 3
            val gray = (0.299f * (rgb[idx].toInt() and 0xFF) +
                       0.587f * (rgb[idx + 1].toInt() and 0xFF) +
                       0.114f * (rgb[idx + 2].toInt() and 0xFF)).toInt().toByte()
            rgb[idx] = gray
            rgb[idx + 1] = gray
            rgb[idx + 2] = gray
        }
        return rgb
    }

    private fun processCanny(yuv: ByteArray, width: Int, height: Int): ByteArray {
        val rgb = ByteArray(width * height * 3)
        yuvToRgb(yuv, rgb, width, height)

        // Convert to grayscale first
        val gray = ByteArray(width * height)
        for (i in 0 until width * height) {
            val idx = i * 3
            gray[i] = (0.299f * (rgb[idx].toInt() and 0xFF) +
                      0.587f * (rgb[idx + 1].toInt() and 0xFF) +
                      0.114f * (rgb[idx + 2].toInt() and 0xFF)).toInt().toByte()
        }

        // Simple edge detection (Sobel)
        val edges = ByteArray(width * height)
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x

                // Sobel X
                val gx = -(gray[(y - 1) * width + x - 1].toInt() and 0xFF) -
                         2 * (gray[y * width + x - 1].toInt() and 0xFF) -
                         (gray[(y + 1) * width + x - 1].toInt() and 0xFF) +
                         (gray[(y - 1) * width + x + 1].toInt() and 0xFF) +
                         2 * (gray[y * width + x + 1].toInt() and 0xFF) +
                         (gray[(y + 1) * width + x + 1].toInt() and 0xFF)

                // Sobel Y
                val gy = -(gray[(y - 1) * width + x - 1].toInt() and 0xFF) -
                         2 * (gray[(y - 1) * width + x].toInt() and 0xFF) -
                         (gray[(y - 1) * width + x + 1].toInt() and 0xFF) +
                         (gray[(y + 1) * width + x - 1].toInt() and 0xFF) +
                         2 * (gray[(y + 1) * width + x].toInt() and 0xFF) +
                         (gray[(y + 1) * width + x + 1].toInt() and 0xFF)

                val magnitude = sqrt((gx * gx + gy * gy).toFloat()).toInt()
                edges[idx] = min(255, magnitude).toByte()
            }
        }

        // Convert back to RGB
        for (i in 0 until width * height) {
            rgb[i * 3] = edges[i]
            rgb[i * 3 + 1] = edges[i]
            rgb[i * 3 + 2] = edges[i]
        }

        return rgb
    }

    private fun yuvToRgb(yuv: ByteArray, rgb: ByteArray, width: Int, height: Int) {
        for (j in 0 until height) {
            for (i in 0 until width) {
                val yIndex = j * width + i
                val uvIndex = (j / 2) * width + (i and 1.inv())

                val y = yuv[yIndex].toInt() and 0xFF
                val v = yuv[width * height + uvIndex].toInt() and 0xFF
                val u = yuv[width * height + uvIndex + 1].toInt() and 0xFF

                var r = (y + 1.370705 * (v - 128)).toInt()
                var g = (y - 0.698001 * (v - 128) - 0.337633 * (u - 128)).toInt()
                var b = (y + 1.732446 * (u - 128)).toInt()

                r = max(0, min(255, r))
                g = max(0, min(255, g))
                b = max(0, min(255, b))

                val rgbIndex = yIndex * 3
                rgb[rgbIndex] = r.toByte()
                rgb[rgbIndex + 1] = g.toByte()
                rgb[rgbIndex + 2] = b.toByte()
            }
        }
    }

    fun processFrameToTexture(
        inputFrame: ByteArray,
        width: Int,
        height: Int,
        mode: Int,
        textureId: Int
    ) {
        // Not implemented in Kotlin version
    }
}

