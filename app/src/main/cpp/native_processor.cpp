#include "native_processor.h"
#include <chrono>

static bool isOpenCVInitialized = false;

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_myapplication_NativeProcessor_initOpenCV(JNIEnv* env, jobject obj) {
    try {
        LOGI("Initializing OpenCV Native Processor");
        isOpenCVInitialized = true;
        LOGI("OpenCV version: %s", CV_VERSION);
        return JNI_TRUE;
    } catch (const cv::Exception& e) {
        LOGE("OpenCV initialization error: %s", e.what());
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_example_myapplication_NativeProcessor_processFrame(
        JNIEnv* env, jobject obj,
        jbyteArray inputFrame,
        jint width, jint height,
        jint mode) {

    auto startTime = std::chrono::high_resolution_clock::now();

    if (!isOpenCVInitialized) {
        LOGE("OpenCV not initialized!");
        return nullptr;
    }

    try {
        // Get byte array from Java
        jbyte* frameData = env->GetByteArrayElements(inputFrame, nullptr);
        jsize frameSize = env->GetArrayLength(inputFrame);

        if (frameData == nullptr) {
            LOGE("Failed to get frame data");
            return nullptr;
        }

        // Convert YUV to Mat
        cv::Mat yuvMat(height + height / 2, width, CV_8UC1, (unsigned char*)frameData);
        cv::Mat rgbMat;
        cv::cvtColor(yuvMat, rgbMat, cv::COLOR_YUV2RGB_NV21);

        cv::Mat processedMat;

        // Process based on mode
        switch (mode) {
            case MODE_RAW:
                processedMat = rgbMat.clone();
                break;
            case MODE_GRAYSCALE:
                processedMat = processGrayscale(rgbMat);
                break;
            case MODE_CANNY:
                processedMat = processCanny(rgbMat);
                break;
            default:
                processedMat = rgbMat.clone();
                break;
        }

        // Convert back to byte array
        std::vector<uchar> buffer;
        int outputSize = processedMat.total() * processedMat.elemSize();
        buffer.resize(outputSize);
        std::memcpy(buffer.data(), processedMat.data, outputSize);

        // Create Java byte array
        jbyteArray result = env->NewByteArray(outputSize);
        env->SetByteArrayRegion(result, 0, outputSize, (jbyte*)buffer.data());

        // Release input array
        env->ReleaseByteArrayElements(inputFrame, frameData, JNI_ABORT);

        auto endTime = std::chrono::high_resolution_clock::now();
        auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(endTime - startTime);
        LOGD("Frame processed in %lld ms (Mode: %d)", duration.count(), mode);

        return result;

    } catch (const cv::Exception& e) {
        LOGE("OpenCV processing error: %s", e.what());
        return nullptr;
    } catch (const std::exception& e) {
        LOGE("Processing error: %s", e.what());
        return nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_myapplication_NativeProcessor_processFrameToTexture(
        JNIEnv* env, jobject obj,
        jbyteArray inputFrame,
        jint width, jint height,
        jint mode,
        jint textureId) {

    // This will be implemented in gl_texture_uploader.cpp
    // For now, just log
    LOGD("processFrameToTexture called with texture ID: %d", textureId);
}

// Helper function: Convert to grayscale
cv::Mat processGrayscale(const cv::Mat& input) {
    cv::Mat gray;
    cv::cvtColor(input, gray, cv::COLOR_RGB2GRAY);

    // Convert back to RGB for display
    cv::Mat output;
    cv::cvtColor(gray, output, cv::COLOR_GRAY2RGB);
    return output;
}

// Helper function: Canny edge detection
cv::Mat processCanny(const cv::Mat& input) {
    cv::Mat gray;
    cv::cvtColor(input, gray, cv::COLOR_RGB2GRAY);

    // Apply Gaussian blur to reduce noise
    cv::GaussianBlur(gray, gray, cv::Size(5, 5), 1.4);

    // Apply Canny edge detection
    cv::Mat edges;
    cv::Canny(gray, edges, 50, 150);

    // Convert edges to RGB for display
    cv::Mat output;
    cv::cvtColor(edges, output, cv::COLOR_GRAY2RGB);

    return output;
}

// Helper function: Convert YUV to Mat (for future use)
cv::Mat convertYUVtoMat(const unsigned char* yuv, int width, int height) {
    cv::Mat yuvMat(height + height / 2, width, CV_8UC1, (unsigned char*)yuv);
    cv::Mat rgbMat;
    cv::cvtColor(yuvMat, rgbMat, cv::COLOR_YUV2RGB_NV21);
    return rgbMat;
}

