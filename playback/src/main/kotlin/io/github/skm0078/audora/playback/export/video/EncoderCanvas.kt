// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/export/video/EncoderCanvas.kt
// Purpose: Puts a drawn bitmap into the video encoder's input surface as one frame at an exact time, through OpenGL
//          ES 2 (ADR 2026-10-28-quote-video).

package io.github.skm0078.audora.playback.export.video

import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * The encoder's input [surface] as an EGL window. A canvas cannot stamp a frame's time; EGL can
 * (`eglPresentationTimeANDROID`), so frames are drawn as fast as the phone can and still play at their real times.
 * Each frame is one bitmap uploaded as a texture and drawn over the whole picture.
 *
 * Bound to the thread that makes it: every call must come from that thread. Failures are [IllegalStateException]s
 * naming the EGL or GL error.
 */
internal class EncoderCanvas(surface: Surface, private val widthPx: Int, private val heightPx: Int) : AutoCloseable {
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var window: EGLSurface = EGL14.EGL_NO_SURFACE
    private var program = 0
    private var texture = 0
    private var uploaded = false
    private val quad = floats(QUAD)

    init {
        try {
            open(surface)
            program = linkProgram()
            texture = newTexture()
        } catch (e: IllegalStateException) {
            close()
            throw e
        }
    }

    /** Uploads [bitmap] (the frame's whole picture, [widthPx] x [heightPx]) for the frames that follow. */
    fun upload(bitmap: Bitmap) {
        check(bitmap.width == widthPx && bitmap.height == heightPx) { "Frame is ${bitmap.width}x${bitmap.height}" }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        if (uploaded) {
            GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, bitmap)
        } else {
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            uploaded = true
        }
        checkGl("upload")
    }

    /** Draws the last upload as the frame shown at [timeNs] and hands it to the encoder (blocks while it is full). */
    fun present(timeNs: Long) {
        check(uploaded) { "No frame uploaded" }
        GLES20.glViewport(0, 0, widthPx, heightPx)
        GLES20.glUseProgram(program)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        val position = GLES20.glGetAttribLocation(program, "aPosition")
        val coordinate = GLES20.glGetAttribLocation(program, "aCoordinate")
        quad.position(0)
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, STRIDE_BYTES, quad)
        GLES20.glEnableVertexAttribArray(position)
        quad.position(2)
        GLES20.glVertexAttribPointer(coordinate, 2, GLES20.GL_FLOAT, false, STRIDE_BYTES, quad)
        GLES20.glEnableVertexAttribArray(coordinate)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, QUAD_VERTICES)
        checkGl("draw")
        EGLExt.eglPresentationTimeANDROID(display, window, timeNs)
        check(EGL14.eglSwapBuffers(display, window)) { "eglSwapBuffers failed: ${eglError()}" }
    }

    override fun close() {
        if (display == EGL14.EGL_NO_DISPLAY) return
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        if (window != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, window)
        if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
        EGL14.eglReleaseThread()
        EGL14.eglTerminate(display)
        display = EGL14.EGL_NO_DISPLAY
        context = EGL14.EGL_NO_CONTEXT
        window = EGL14.EGL_NO_SURFACE
    }

    private fun open(surface: Surface) {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != EGL14.EGL_NO_DISPLAY) { "No EGL display" }
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "eglInitialize failed: ${eglError()}" }
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        val chosen = EGL14.eglChooseConfig(display, CONFIG, 0, configs, 0, 1, count, 0)
        val config = configs[0]
        check(chosen && count[0] > 0 && config != null) { "No recordable EGL config: ${eglError()}" }
        context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, CONTEXT, 0)
        check(context != EGL14.EGL_NO_CONTEXT) { "eglCreateContext failed: ${eglError()}" }
        window = EGL14.eglCreateWindowSurface(display, config, surface, intArrayOf(EGL14.EGL_NONE), 0)
        check(window != EGL14.EGL_NO_SURFACE) { "eglCreateWindowSurface failed: ${eglError()}" }
        check(EGL14.eglMakeCurrent(display, window, window, context)) { "eglMakeCurrent failed: ${eglError()}" }
    }

    private fun linkProgram(): Int {
        val linked = GLES20.glCreateProgram()
        GLES20.glAttachShader(linked, shader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER))
        GLES20.glAttachShader(linked, shader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER))
        GLES20.glLinkProgram(linked)
        val status = IntArray(1)
        GLES20.glGetProgramiv(linked, GLES20.GL_LINK_STATUS, status, 0)
        check(status[0] == GLES20.GL_TRUE) { "Program did not link: ${GLES20.glGetProgramInfoLog(linked)}" }
        return linked
    }

    private fun shader(type: Int, source: String): Int {
        val compiled = GLES20.glCreateShader(type)
        GLES20.glShaderSource(compiled, source)
        GLES20.glCompileShader(compiled)
        val status = IntArray(1)
        GLES20.glGetShaderiv(compiled, GLES20.GL_COMPILE_STATUS, status, 0)
        check(status[0] == GLES20.GL_TRUE) { "Shader did not compile: ${GLES20.glGetShaderInfoLog(compiled)}" }
        return compiled
    }

    private fun newTexture(): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        // A frame is drawn at its own size: no mipmaps, and edges clamped (ES 2 needs both for a 720 x 1280 texture).
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        checkGl("texture")
        return ids[0]
    }

    private fun checkGl(step: String) {
        val error = GLES20.glGetError()
        check(error == GLES20.GL_NO_ERROR) { "GL error 0x${Integer.toHexString(error)} at $step" }
    }

    private fun eglError(): String = "0x${Integer.toHexString(EGL14.eglGetError())}"

    private companion object {
        /** EGL_RECORDABLE_ANDROID: not in EGL14's constants, though every Android since 4.3 has it. */
        const val EGL_RECORDABLE_ANDROID = 0x3142
        const val CHANNEL_BITS = 8
        const val QUAD_VERTICES = 4
        const val FLOATS_PER_VERTEX = 4
        const val STRIDE_BYTES = FLOATS_PER_VERTEX * Float.SIZE_BYTES

        val CONFIG = intArrayOf(
            EGL14.EGL_RED_SIZE, CHANNEL_BITS,
            EGL14.EGL_GREEN_SIZE, CHANNEL_BITS,
            EGL14.EGL_BLUE_SIZE, CHANNEL_BITS,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE,
        )
        val CONTEXT = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)

        /**
         * x, y on screen then u, v in the picture, as a strip. A bitmap's first row is the texture's v = 0 and the
         * top of the picture, so v runs 1 at the bottom (y = -1) to 0 at the top.
         */
        val QUAD = floatArrayOf(
            -1f, -1f, 0f, 1f,
            1f, -1f, 1f, 1f,
            -1f, 1f, 0f, 0f,
            1f, 1f, 1f, 0f,
        )

        const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec2 aCoordinate;
            varying vec2 vCoordinate;
            void main() {
                gl_Position = aPosition;
                vCoordinate = aCoordinate;
            }
        """
        const val FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vCoordinate;
            uniform sampler2D uPicture;
            void main() {
                gl_FragColor = texture2D(uPicture, vCoordinate);
            }
        """

        fun floats(values: FloatArray): FloatBuffer = ByteBuffer.allocateDirect(values.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(values)
                position(0)
            }
    }
}
