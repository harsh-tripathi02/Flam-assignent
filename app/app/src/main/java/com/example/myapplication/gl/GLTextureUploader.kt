package com.example.myapplication.gl

import android.util.Log

/**
 * Native wrapper for uploading textures directly from C++
 */
class GLTextureUploader {
    companion object {
        private const val TAG = "GLTextureUploader"

        init {
            try {
                System.loadLibrary("native-processor")
                Log.i(TAG, "GLTextureUploader native library loaded")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library", e)
            }
        }
    }

    /**
     * Upload texture data directly from native code
     * @param textureId OpenGL texture ID
     * @param data RGB byte array
     * @param width texture width
     * @param height texture height
     */
    external fun uploadTexture(textureId: Int, data: ByteArray, width: Int, height: Int)
}

