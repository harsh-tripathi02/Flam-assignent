#ifndef NATIVE_PROCESSOR_H
#define NATIVE_PROCESSOR_H

#include <jni.h>
#include <android/log.h>

#define LOG_TAG "NativeProcessor"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

// Processing modes
enum ProcessingMode {
    MODE_RAW = 0,
    MODE_GRAYSCALE = 1,
    MODE_CANNY = 2
};

// Function declarations
extern "C" {
    JNIEXPORT jboolean JNICALL
    Java_com_example_myapplication_NativeProcessor_initOpenCV(JNIEnv* env, jobject obj);

    JNIEXPORT jbyteArray JNICALL
    Java_com_example_myapplication_NativeProcessor_processFrame(
            JNIEnv* env, jobject obj,
            jbyteArray inputFrame,
            jint width, jint height,
            jint mode);

    JNIEXPORT void JNICALL
    Java_com_example_myapplication_NativeProcessor_processFrameToTexture(
            JNIEnv* env, jobject obj,
            jbyteArray inputFrame,
            jint width, jint height,
            jint mode,
            jint textureId);
}


#endif // NATIVE_PROCESSOR_H

