# OpenCV Setup Instructions

## Download OpenCV Android SDK

1. Download OpenCV 4.8.0 Android SDK from: https://opencv.org/releases/
2. Extract the downloaded file
3. Create directory: `app/src/main/jniLibs`
4. Copy the following directories from OpenCV SDK to your project:
   - Copy `sdk/native/libs/*` to `app/src/main/jniLibs/`
   - Copy `sdk/native/jni/include` to `app/src/main/cpp/include/opencv2`

## Alternative: Use Maven (Already configured in build.gradle.kts)

The project is configured to use OpenCV from Maven Central:
```
implementation("org.opencv:opencv:4.8.0")
```

## Directory Structure After Setup

```
app/
  src/
    main/
      cpp/
        include/
          opencv2/
            ...
      jniLibs/
        arm64-v8a/
          libopencv_java4.so
        armeabi-v7a/
          libopencv_java4.so
        x86/
          libopencv_java4.so
        x86_64/
          libopencv_java4.so
```

## Load OpenCV in Java/Kotlin

Add to MainActivity or Application class:
```kotlin
companion object {
    init {
        System.loadLibrary("opencv_java4")
        System.loadLibrary("native-processor")
    }
}
```

