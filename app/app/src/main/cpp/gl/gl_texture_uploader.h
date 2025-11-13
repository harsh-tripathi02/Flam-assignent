#ifndef GL_TEXTURE_UPLOADER_H
#define GL_TEXTURE_UPLOADER_H

#include <jni.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include <EGL/egl.h>
#include <opencv2/opencv.hpp>
#include <android/log.h>

#define LOG_TAG "GLTextureUploader"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {
    JNIEXPORT void JNICALL
    Java_com_example_myapplication_gl_GLTextureUploader_uploadTexture(
            JNIEnv* env, jobject obj,
            jint textureId,
            jbyteArray data,
            jint width, jint height);
}

#endif // GL_TEXTURE_UPLOADER_H

