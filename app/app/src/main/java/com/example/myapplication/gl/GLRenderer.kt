package com.example.myapplication.gl

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * OpenGL ES 2.0 Renderer for displaying processed frames
 */
class GLRenderer : GLSurfaceView.Renderer {
    companion object {
        private const val TAG = "GLRenderer"

        // Vertex shader code
        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = aTexCoord;
            }
        """

        // Fragment shader code
        private const val FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D uTexture;
            void main() {
                gl_FragColor = texture2D(uTexture, vTexCoord);
            }
        """

        // Full-screen quad vertices (2 triangles)
        private val VERTICES = floatArrayOf(
            // Position (x, y)   // TexCoord (u, v)
            -1.0f,  1.0f,        0.0f, 0.0f,  // Top-left
            -1.0f, -1.0f,        0.0f, 1.0f,  // Bottom-left
             1.0f, -1.0f,        1.0f, 1.0f,  // Bottom-right
             1.0f,  1.0f,        1.0f, 0.0f   // Top-right
        )

        private val INDICES = shortArrayOf(
            0, 1, 2,  // First triangle
            0, 2, 3   // Second triangle
        )
    }

    private var program: Int = 0
    private var textureId: Int = 0

    private var positionHandle: Int = 0
    private var texCoordHandle: Int = 0
    private var textureHandle: Int = 0

    private val vertexBuffer: FloatBuffer

    private var frameWidth: Int = 0
    private var frameHeight: Int = 0
    private var frameData: ByteArray? = null
    private val textureLock = Any()

    init {
        // Create vertex buffer
        vertexBuffer = ByteBuffer.allocateDirect(VERTICES.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(VERTICES)
        vertexBuffer.position(0)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.d(TAG, "Surface created")

        // Set clear color
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)

        // Create shader program
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        if (program == 0) {
            Log.e(TAG, "Failed to create shader program")
            return
        }

        // Get attribute/uniform locations
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        texCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
        textureHandle = GLES20.glGetUniformLocation(program, "uTexture")

        // Generate texture
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]

        Log.d(TAG, "Texture ID: $textureId, Program: $program")
        Log.d(TAG, "Position handle: $positionHandle, TexCoord handle: $texCoordHandle")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        Log.d(TAG, "Surface changed: ${width}x${height}")
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        // Clear screen
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        synchronized(textureLock) {
            if (frameData == null || frameWidth == 0 || frameHeight == 0) {
                return
            }

            // Use shader program
            GLES20.glUseProgram(program)

            // Bind texture
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)

            // Set texture parameters
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            // Upload texture data
            val buffer = ByteBuffer.wrap(frameData!!)
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGB,
                frameWidth, frameHeight, 0,
                GLES20.GL_RGB, GLES20.GL_UNSIGNED_BYTE, buffer
            )

            // Set texture uniform
            GLES20.glUniform1i(textureHandle, 0)

            // Set vertex attributes
            vertexBuffer.position(0)
            GLES20.glEnableVertexAttribArray(positionHandle)
            GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 16, vertexBuffer)

            vertexBuffer.position(2)
            GLES20.glEnableVertexAttribArray(texCoordHandle)
            GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 16, vertexBuffer)

            // Draw quad
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, 4)

            // Disable attributes
            GLES20.glDisableVertexAttribArray(positionHandle)
            GLES20.glDisableVertexAttribArray(texCoordHandle)
        }

        checkGLError("onDrawFrame")
    }

    /**
     * Update texture with new frame data
     */
    fun updateTexture(data: ByteArray, width: Int, height: Int) {
        synchronized(textureLock) {
            frameData = data
            frameWidth = width
            frameHeight = height
        }
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        if (vertexShader == 0) {
            return 0
        }

        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        if (fragmentShader == 0) {
            return 0
        }

        var program = GLES20.glCreateProgram()
        if (program == 0) {
            Log.e(TAG, "Failed to create program")
            return 0
        }

        GLES20.glAttachShader(program, vertexShader)
        checkGLError("glAttachShader(vertex)")

        GLES20.glAttachShader(program, fragmentShader)
        checkGLError("glAttachShader(fragment)")

        GLES20.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)

        if (linkStatus[0] != GLES20.GL_TRUE) {
            Log.e(TAG, "Could not link program: ${GLES20.glGetProgramInfoLog(program)}")
            GLES20.glDeleteProgram(program)
            program = 0
        }

        return program
    }

    private fun loadShader(type: Int, source: String): Int {
        var shader = GLES20.glCreateShader(type)
        if (shader == 0) {
            Log.e(TAG, "Failed to create shader")
            return 0
        }

        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)

        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)

        if (compiled[0] == 0) {
            Log.e(TAG, "Could not compile shader $type: ${GLES20.glGetShaderInfoLog(shader)}")
            GLES20.glDeleteShader(shader)
            shader = 0
        }

        return shader
    }

    private fun checkGLError(op: String) {
        val error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) {
            Log.e(TAG, "$op: glError 0x${Integer.toHexString(error)}")
        }
    }
}

