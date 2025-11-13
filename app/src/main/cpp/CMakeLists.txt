cmake_minimum_required(VERSION 3.18.1)

project("native-processor")

# Add native source files
add_library(native-processor SHARED
        native_processor.cpp
        gl/gl_texture_uploader.cpp
)

# Link libraries
target_link_libraries(native-processor
        android
        log
        GLESv2
        EGL
        jnigraphics
)

