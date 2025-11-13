#include "native_processor.h"
#include <chrono>
#include <cstring>
#include <algorithm>
#include <cmath>
#include <cstdint>

static bool isOpenCVInitialized = false;

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_myapplication_NativeProcessor_initOpenCV(JNIEnv* env, jobject obj) {
    try {
        LOGI("Initializing Native Processor");
        isOpenCVInitialized = true;
        LOGI("Native Processor initialized successfully");
        return JNI_TRUE;
    } catch (const std::exception& e) {
        LOGE("Initialization error: %s", e.what());
        return JNI_FALSE;
    }
}

// YUV NV21 to RGB conversion
void yuvToRgb(const uint8_t* yuv, uint8_t* rgb, int width, int height) {
    const uint8_t* yData = yuv;
    const uint8_t* vuData = yuv + width * height;

    for (int j = 0; j < height; j++) {
        for (int i = 0; i < width; i++) {
            int yIndex = j * width + i;
            int uvIndex = (j / 2) * width + (i & ~1);

            int y = yData[yIndex] & 0xFF;
            int v = vuData[uvIndex] & 0xFF;
            int u = vuData[uvIndex + 1] & 0xFF;

            // YUV to RGB conversion
            int r = (int)(y + 1.370705 * (v - 128));
            int g = (int)(y - 0.698001 * (v - 128) - 0.337633 * (u - 128));
            int b = (int)(y + 1.732446 * (u - 128));

            // Clamp values
            r = std::max(0, std::min(255, r));
            g = std::max(0, std::min(255, g));
            b = std::max(0, std::min(255, b));

            int rgbIndex = yIndex * 3;
            rgb[rgbIndex] = (uint8_t)r;
            rgb[rgbIndex + 1] = (uint8_t)g;
            rgb[rgbIndex + 2] = (uint8_t)b;
        }
    }
}

// Convert RGB to Grayscale
void convertToGrayscale(const uint8_t* rgb, uint8_t* gray, int width, int height) {
    for (int i = 0; i < width * height; i++) {
        int idx = i * 3;
        gray[i] = (uint8_t)(0.299f * rgb[idx] + 0.587f * rgb[idx + 1] + 0.114f * rgb[idx + 2]);
    }
}

// Simple edge detection using Sobel
void detectEdges(const uint8_t* gray, uint8_t* edges, int width, int height) {
    std::memset(edges, 0, width * height);

    for (int y = 1; y < height - 1; y++) {
        for (int x = 1; x < width - 1; x++) {
            int idx = y * width + x;

            // Sobel X kernel
            int gx = -gray[(y - 1) * width + (x - 1)] - 2 * gray[y * width + (x - 1)] - gray[(y + 1) * width + (x - 1)]
                    + gray[(y - 1) * width + (x + 1)] + 2 * gray[y * width + (x + 1)] + gray[(y + 1) * width + (x + 1)];

            // Sobel Y kernel
            int gy = -gray[(y - 1) * width + (x - 1)] - 2 * gray[(y - 1) * width + x] - gray[(y - 1) * width + (x + 1)]
                    + gray[(y + 1) * width + (x - 1)] + 2 * gray[(y + 1) * width + x] + gray[(y + 1) * width + (x + 1)];

            // Calculate magnitude
            int magnitude = (int)std::sqrt((float)(gx * gx + gy * gy));
            edges[idx] = (uint8_t)std::min(255, magnitude);
        }
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
        LOGE("Native processor not initialized!");
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

        int pixelCount = width * height;
        int rgbSize = pixelCount * 3;

        // Allocate RGB output
        uint8_t* rgbData = new uint8_t[rgbSize];

        // Convert YUV to RGB
        yuvToRgb((uint8_t*)frameData, rgbData, width, height);

        jbyteArray result;

        if (mode == MODE_RAW) {
            // Return RGB directly
            result = env->NewByteArray(rgbSize);
            env->SetByteArrayRegion(result, 0, rgbSize, (jbyte*)rgbData);
        }
        else if (mode == MODE_GRAYSCALE) {
            // Convert to grayscale
            uint8_t* grayData = new uint8_t[pixelCount];
            convertToGrayscale(rgbData, grayData, width, height);

            // Convert back to RGB for display
            uint8_t* grayRgb = new uint8_t[rgbSize];
            for (int i = 0; i < pixelCount; i++) {
                grayRgb[i * 3] = grayData[i];
                grayRgb[i * 3 + 1] = grayData[i];
                grayRgb[i * 3 + 2] = grayData[i];
            }

            result = env->NewByteArray(rgbSize);
            env->SetByteArrayRegion(result, 0, rgbSize, (jbyte*)grayRgb);

            delete[] grayData;
            delete[] grayRgb;
        }
        else { // MODE_CANNY
            // Edge detection
            uint8_t* grayData = new uint8_t[pixelCount];
            uint8_t* edgeData = new uint8_t[pixelCount];

            convertToGrayscale(rgbData, grayData, width, height);
            detectEdges(grayData, edgeData, width, height);

            // Convert edges to RGB (white edges on black background)
            uint8_t* edgeRgb = new uint8_t[rgbSize];
            for (int i = 0; i < pixelCount; i++) {
                edgeRgb[i * 3] = edgeData[i];
                edgeRgb[i * 3 + 1] = edgeData[i];
                edgeRgb[i * 3 + 2] = edgeData[i];
            }

            result = env->NewByteArray(rgbSize);
            env->SetByteArrayRegion(result, 0, rgbSize, (jbyte*)edgeRgb);

            delete[] grayData;
            delete[] edgeData;
            delete[] edgeRgb;
        }

        // Cleanup
        delete[] rgbData;
        env->ReleaseByteArrayElements(inputFrame, frameData, JNI_ABORT);

        auto endTime = std::chrono::high_resolution_clock::now();
        auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(endTime - startTime);
        LOGD("Frame processed in %lld ms (Mode: %d)", (long long)duration.count(), mode);

        return result;

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

    LOGD("processFrameToTexture called with texture ID: %d", textureId);
}


