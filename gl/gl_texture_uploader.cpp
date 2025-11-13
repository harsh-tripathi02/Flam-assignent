#include "gl_texture_uploader.h"

extern "C" JNIEXPORT void JNICALL
Java_com_example_myapplication_gl_GLTextureUploader_uploadTexture(
        JNIEnv* env, jobject obj,
        jint textureId,
        jbyteArray data,
        jint width, jint height) {

    if (data == nullptr) {
        LOGE("Null data array provided");
        return;
    }

    try {
        // Get byte array from Java
        jbyte* pixelData = env->GetByteArrayElements(data, nullptr);
        jsize dataSize = env->GetArrayLength(data);

        if (pixelData == nullptr) {
            LOGE("Failed to get pixel data");
            return;
        }

        // Bind the texture
        glBindTexture(GL_TEXTURE_2D, textureId);

        // Set texture parameters
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        // Upload texture data (RGB format)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, width, height, 0,
                     GL_RGB, GL_UNSIGNED_BYTE, pixelData);

        // Check for OpenGL errors
        GLenum error = glGetError();
        if (error != GL_NO_ERROR) {
            LOGE("OpenGL error during texture upload: 0x%x", error);
        } else {
            LOGD("Texture uploaded successfully: %dx%d to texture ID %d", width, height, textureId);
        }

        // Release byte array
        env->ReleaseByteArrayElements(data, pixelData, JNI_ABORT);

        // Unbind texture
        glBindTexture(GL_TEXTURE_2D, 0);

    } catch (const std::exception& e) {
        LOGE("Exception during texture upload: %s", e.what());
    }
}

