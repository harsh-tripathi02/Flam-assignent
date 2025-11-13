cmake_minimum_required(VERSION 3.22.1)

project("native-processor")

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# OpenCV configuration
set(OpenCV_DIR ${CMAKE_SOURCE_DIR}/../jniLibs)
find_package(OpenCV REQUIRED)

# Add native source files
add_library(${CMAKE_PROJECT_NAME} SHARED
        native_processor.cpp
        gl/gl_texture_uploader.cpp
)

# Include directories
target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
        ${OpenCV_INCLUDE_DIRS}
)

# Link libraries
target_link_libraries(${CMAKE_PROJECT_NAME}
        ${OpenCV_LIBS}
        android
        log
        GLESv2
        EGL
)

