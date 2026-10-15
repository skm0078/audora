// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/sherpa/SherpaStopCallback.kt
// Purpose: The per-sentence callback handed to sherpa-onnx, shaped the way its JNI code looks it up.

package io.github.skm0078.audora.speech.sherpa

/**
 * Tells sherpa-onnx after each sentence whether to keep going.
 *
 * sherpa-onnx's JNI finds the callback method by name and signature, `invoke([F)Ljava/lang/Integer;`. A Kotlin
 * lambda compiles to an invokedynamic object that only has the erased `invoke(Object)Object`, so the lookup fails and
 * ART aborts the whole process. A real class gets the typed method. consumer-rules.pro keeps it from R8, and
 * SherpaStopCallbackTest pins the signature.
 */
internal class SherpaStopCallback(private val keepGoing: () -> Boolean) : (FloatArray) -> Int {
    override fun invoke(samples: FloatArray): Int = if (keepGoing()) CONTINUE else STOP

    internal companion object {
        const val CONTINUE = 1
        const val STOP = 0
    }
}
