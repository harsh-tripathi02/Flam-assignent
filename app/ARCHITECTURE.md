# System Architecture

## High-Level Architecture

```
┌────────────────────────────────────────────────────────────────┐
│                         USER DEVICE                            │
│  ┌───────────────────────────────────────────────────────────┐ │
│  │                    ANDROID APPLICATION                    │ │
│  │                                                           │ │
│  │  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐ │ │
│  │  │  UI Layer    │    │ Camera Layer │    │   GL Layer   │ │ │
│  │  │  (Kotlin)    │◄──►│   (Camera2)  │◄──►│  (OpenGL ES) │ │ │
│  │  │              │    │              │    │              │ │ │
│  │  │ MainActivity │    │CameraManager │    │  GLRenderer  │ │ │
│  │  │   Spinner    │    │ ImageReader  │    │ GLSurfaceView│ │ │
│  │  │ FPS Counter  │    │ TextureView  │    │   Shaders    │ │ │
│  │  └──────┬───────┘    └──────┬───────┘    └──────▲───────┘ │ │
│  │         │                   │                   │         │ │
│  │         │ JNI Call          │ Frame Data        │ Texture │ │
│  │         │                   │                   │ Update  │ │
│  │         ▼                   ▼                   │         │ │
│  │  ┌──────────────────────────────────────────────┴───────┐ │ │
│  │  │            JNI BRIDGE LAYER (Kotlin/Java)            │ │ │
│  │  │                                                      │ │ │
│  │  │  NativeProcessor.kt   |   GLTextureUploader.kt       │ │ │
│  │  │  - initOpenCV()       |   - uploadTexture()          │ │ │
│  │  │  - processFrame()     |                              │ │ │
│  │  └────────────────────────┬───────────────────────────────┘ │
│  │                           │                                 │
│  │          ═══════════════════════════════════                │
│  │                    JNI Boundary                             │
│  │          ═══════════════════════════════════                │
│  │                           │                                 │
│  │  ┌────────────────────────▼───────────────────────────────┐ │
│  │  │         NATIVE LAYER (C++17 + NDK)                     │ │
│  │  │                                                        │ │
│  │  │  native_processor.cpp        gl_texture_uploader.cpp   │ │
│  │  │  ┌──────────────────┐        ┌───────────────────┐     │ │
│  │  │  │ processFrame()   │        │ uploadTexture()   │     │ │
│  │  │  │  ├─ YUV→RGB      │        │  ├─ glBindTexture │     │ │
│  │  │  │  ├─ Mode Switch  │        │  ├─ glTexImage2D  │     │ │
│  │  │  │  │  ├─ RAW       │        │  └─ Sync Upload   │     │ │
│  │  │  │  │  ├─ Grayscale │        └───────────────────┘     │ │
│  │  │  │  │  └─ Canny     │                                  │ │
│  │  │  │  └─ Return RGB   │                                  │ │
│  │  │  └────────┬─────────┘                                  │ │
│  │  │           │                                            │ │
│  │  │           ▼                                            │ │
│  │  │  ┌─────────────────────────────────┐                   │ │
│  │  │  │      OpenCV 4.8.0 Library       │                   │ │
│  │  │  │  ┌──────────────────────────┐   │                   │ │
│  │  │  │  │ cv::cvtColor()           │   │                   │ │
│  │  │  │  │ cv::GaussianBlur()       │   │                   │ │
│  │  │  │  │ cv::Canny()              │   │                   │ │
│  │  │  │  │ cv::Mat operations       │   │                   │ │
│  │  │  │  └──────────────────────────┘   │                   │ │
│  │  │  └─────────────────────────────────┘                   │ │
│  │  └────────────────────────────────────────────────────────┘ │
│  └───────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────┘

                              │
                              │ Base64 Export
                              ▼

┌────────────────────────────────────────────────────────────────┐
│                      WEB BROWSER                               │
│  ┌───────────────────────────────────────────────────────────┐ │
│  │              TypeScript Web Viewer                        │ │
│  │                                                           │ │
│  │  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐ │ │
│  │  │   app.ts     │───►│  index.html  │───►│    CSS3      │ │ │
│  │  │              │    │              │    │              │ │ │
│  │  │ FrameViewer  │    │ Canvas/Image │    │Glassmorphism │ │ │
│  │  │ loadFrame()  │    │ Stats Grid   │    │  Gradients   │ │ │
│  │  │ updateStats()│    │ Tech Info    │    │  Animations  │ │ │
│  │  └──────────────┘    └──────────────┘    └──────────────┘ │ │
│  └───────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────┘
```

---

## Data Flow Diagram

```
┌─────────────┐
│   CAMERA    │  Physical Device Camera
└──────┬──────┘
       │
       │ YUV_420_888 @ 30fps
       ▼
┌─────────────────────┐
│   ImageReader       │  Android Camera2 API
│   640x480           │
└──────┬──────────────┘
       │
       │ YUV Planes (Y, U, V)
       ▼
┌─────────────────────┐
│  YUV → NV21         │  Kotlin: CameraManager
│  Conversion         │
└──────┬──────────────┘
       │
       │ NV21 ByteArray
       ▼
┌─────────────────────┐
│  JNI Call           │  nativeProcessor.processFrame()
│  Java → C++         │
└──────┬──────────────┘
       │
       │ jbyteArray
       ▼
┌─────────────────────┐
│  NV21 → cv::Mat     │  C++: native_processor.cpp
│  YUV → RGB          │  cv::cvtColor(COLOR_YUV2RGB_NV21)
└──────┬──────────────┘
       │
       │ cv::Mat (RGB)
       ▼
┌─────────────────────┐
│  Mode Selection     │  Switch (mode)
│  ┌───────────────┐  │
│  │ MODE_RAW      │  │  → Clone original
│  │ MODE_GRAYSCALE│  │  → cv::cvtColor(COLOR_RGB2GRAY)
│  │ MODE_CANNY    │  │  → cv::Canny(50, 150)
│  └───────────────┘  │
└──────┬──────────────┘
       │
       │ Processed cv::Mat
       ▼
┌─────────────────────┐
│  Mat → ByteArray    │  C++: memcpy to std::vector
│  RGB data           │
└──────┬──────────────┘
       │
       │ jbyteArray (RGB)
       ▼
┌─────────────────────┐
│  JNI Return         │  Return to Kotlin
│  C++ → Java         │
└──────┬──────────────┘
       │
       │ ByteArray (RGB)
       ▼
┌─────────────────────┐
│  GLRenderer         │  Kotlin: glRenderer.updateTexture()
│  updateTexture()    │
└──────┬──────────────┘
       │
       │ Synchronized update
       ▼
┌─────────────────────┐
│  OpenGL ES 2.0      │  GLES20.glTexImage2D()
│  Texture Upload     │  GL_RGB, GL_UNSIGNED_BYTE
└──────┬──────────────┘
       │
       │ Texture ID bound
       ▼
┌─────────────────────┐
│  Vertex Shader      │  Transform vertices
│  Fragment Shader    │  Sample texture
└──────┬──────────────┘
       │
       │ Rendered pixels
       ▼
┌─────────────────────┐
│  GLSurfaceView      │  Display on screen
│  onDrawFrame()      │  @ 10-15 FPS
└─────────────────────┘
```

---

## Component Interaction Diagram

```
┌─────────────┐
│ MainActivity│
└─────┬───────┘
      │ onCreate()
      ├──►┌─────────────────┐
      │   │ NativeProcessor │
      │   │  .initOpenCV()  │
      │   └─────────────────┘
      │
      ├──►┌─────────────────┐
      │   │  GLRenderer     │
      │   │  .constructor() │
      │   └─────────────────┘
      │
      └──►┌─────────────────┐
          │ CameraManager   │
          │  .constructor() │
          └────────┬────────┘
                   │
      ┌────────────┴────────────┐
      │ onFrameAvailable        │
      │ (frameData, w, h)       │
      └────────┬────────────────┘
               │
               ▼
      ┌────────────────────────┐
      │ MainActivity           │
      │  .processFrame()       │
      └───────┬────────────────┘
              │
              ├──►┌──────────────────────┐
              │   │ Coroutine Launch     │
              │   │ Dispatchers.Default  │
              │   └──────┬───────────────┘
              │          │
              │          ▼
              │   ┌──────────────────────┐
              │   │ nativeProcessor      │
              │   │  .processFrame()     │
              │   │  (JNI → C++)        │
              │   └──────┬───────────────┘
              │          │
              │          ▼
              │   ┌──────────────────────┐
              │   │ OpenCV Processing    │
              │   │  (C++ Native)        │
              │   └──────┬───────────────┘
              │          │
              │          ▼
              │   ┌──────────────────────┐
              │   │ Return RGB ByteArray │
              │   │  (JNI → Kotlin)     │
              │   └──────┬───────────────┘
              │          │
              │          ▼
              └──►┌──────────────────────┐
                  │ glRenderer           │
                  │  .updateTexture()    │
                  └──────┬───────────────┘
                         │
                         ▼
                  ┌──────────────────────┐
                  │ GLSurfaceView        │
                  │  .requestRender()    │
                  └──────┬───────────────┘
                         │
                         ▼
                  ┌──────────────────────┐
                  │ onDrawFrame()        │
                  │  → Display           │
                  └──────────────────────┘
```

---

## Threading Model

```
┌──────────────────────────────────────────────────────────┐
│                     Main Thread                           │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐         │
│  │ UI Updates │  │ Camera Init│  │  GL Setup  │         │
│  │ FPS Display│  │ Permission │  │ Renderer   │         │
│  └────────────┘  └────────────┘  └────────────┘         │
└───────────────────────────┬──────────────────────────────┘
                            │
        ┌───────────────────┼───────────────────┐
        │                   │                   │
        ▼                   ▼                   ▼
┌───────────────┐  ┌────────────────┐  ┌──────────────┐
│ Camera Thread │  │Default Dispatch│  │  GL Thread   │
│ (Background)  │  │  (Coroutine)   │  │ (Dedicated)  │
├───────────────┤  ├────────────────┤  ├──────────────┤
│ ImageReader   │  │ processFrame() │  │ onDrawFrame()│
│  Callback     │  │                │  │              │
│               │  │ JNI Call       │  │ Texture      │
│ YUV→NV21      │─►│ OpenCV Process │─►│  Upload      │
│               │  │                │  │              │
│ Frame @ 30fps │  │ RGB Output     │  │ Render       │
└───────────────┘  └────────────────┘  └──────────────┘
```

---

## Memory Management

```
┌─────────────────────────────────────────────┐
│           Memory Lifecycle                   │
└─────────────────────────────────────────────┘

Camera Capture:
┌─────────────┐
│ ImageReader │  Allocates: 2 buffers (640x480 YUV)
│ Buffer Pool │  ~600KB each → Total: ~1.2 MB
└─────────────┘

JNI Transfer:
┌─────────────┐
│  jbyteArray │  Copies: YUV data to JNI
│             │  ~600KB temporary allocation
└─────────────┘

Native Processing:
┌─────────────┐
│  cv::Mat    │  Allocates: Input + Output matrices
│             │  Input: 640x480x3 (RGB) = ~900KB
│             │  Output: 640x480x3 (RGB) = ~900KB
│             │  Total: ~1.8 MB
└─────────────┘

OpenGL Texture:
┌─────────────┐
│ GL_TEXTURE  │  GPU Memory: 640x480x3 = ~900KB
│             │  Persistent texture storage
└─────────────┘

Total Peak Memory: ~5 MB per frame
```

---

## Build System Architecture

```
┌────────────────────────────────────────────┐
│         Gradle Build System                 │
└────────────┬───────────────────────────────┘
             │
    ┌────────┴────────┐
    │                 │
    ▼                 ▼
┌──────────┐    ┌──────────┐
│  Kotlin  │    │   C++    │
│ Compiler │    │ Compiler │
└────┬─────┘    └────┬─────┘
     │               │
     │               ▼
     │          ┌─────────┐
     │          │  CMake  │
     │          └────┬────┘
     │               │
     │               ├──► Android NDK (Clang)
     │               ├──► OpenCV Linking
     │               └──► .so Generation
     │                    (arm64-v8a, armeabi-v7a, etc.)
     │
     ▼
┌────────────────────────┐
│   DEX Files (.dex)     │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│   APK Packaging        │
│  ┌──────────────────┐  │
│  │ classes.dex      │  │
│  │ lib/             │  │
│  │  ├─arm64-v8a/    │  │
│  │  │  └─*.so       │  │
│  │  └─armeabi-v7a/  │  │
│  │     └─*.so       │  │
│  │ res/             │  │
│  │ AndroidManifest  │  │
│  └──────────────────┘  │
└────────────────────────┘
```

---

## Dependency Graph

```
MainActivity
    ├── NativeProcessor (JNI)
    │   └── libnative-processor.so
    │       ├── libopencv_java4.so
    │       ├── liblog.so (Android)
    │       ├── libGLESv2.so (Android)
    │       └── libEGL.so (Android)
    │
    ├── CameraManager
    │   └── androidx.camera:camera-camera2
    │
    ├── GLRenderer
    │   └── android.opengl (Built-in)
    │
    └── Coroutines
        └── kotlinx.coroutines-android

TypeScript Viewer
    └── TypeScript Compiler (tsc)
        └── DOM APIs (Built-in)
```

---