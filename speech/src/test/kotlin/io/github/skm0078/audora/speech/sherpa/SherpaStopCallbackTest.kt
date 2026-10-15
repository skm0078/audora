// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/sherpa/SherpaStopCallbackTest.kt
// Purpose: Pins the callback's JVM signature that sherpa-onnx's JNI looks up, and its continue/stop values.

package io.github.skm0078.audora.speech.sherpa

import org.junit.Assert.assertEquals
import org.junit.Test

class SherpaStopCallbackTest {
    @Test
    fun hasTheTypedInvokeThatSherpaJniLooksUp() {
        val method = SherpaStopCallback::class.java.getDeclaredMethod("invoke", FloatArray::class.java)
        assertEquals(Integer::class.java, method.returnType)
    }

    @Test
    fun returnsContinueWhileWantedAndStopAfter() {
        var wanted = true
        val callback = SherpaStopCallback { wanted }
        assertEquals(SherpaStopCallback.CONTINUE, callback(FloatArray(0)))
        wanted = false
        assertEquals(SherpaStopCallback.STOP, callback(FloatArray(0)))
    }
}
