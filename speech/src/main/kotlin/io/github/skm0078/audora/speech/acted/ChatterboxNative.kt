// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/acted/ChatterboxNative.kt
// Purpose: The JNI side of libaudora_acted.so: Chatterbox on the ONNX Runtime sherpa-onnx ships (ADR-059).

package io.github.skm0078.audora.speech.acted

import java.io.File

/**
 * One loaded model (about 2.9 GB of weights, about 4.5 GB in use; ONNX Runtime keeps them while it lives). Not
 * thread-safe except [cancel]: the engine calls it from its one render thread. [close] frees it; later calls fail.
 */
internal class ChatterboxNative private constructor(private var handle: Long) : ActedModel {
    override fun speak(textTokens: LongArray, strength: Float, maxTokens: Int, repetitionPenalty: Float): ActedSpeech? {
        val out = nativeSpeak(live(), textTokens, strength, maxTokens, repetitionPenalty) ?: return null
        return ActedSpeech(out.copyOfRange(1, out.size), stopped = out[0] == 1L)
    }

    override fun decode(speech: LongArray): FloatArray = nativeDecode(live(), speech)

    override fun cancel() {
        val current = handle
        if (current != 0L) nativeCancel(current)
    }

    override fun close() {
        val current = handle
        handle = 0L
        if (current != 0L) nativeRelease(current)
    }

    private fun live(): Long = handle.also { check(it != 0L) { "acted voice model was released" } }

    companion object {
        /**
         * Loads the model files in [dir] and the language model in [lmDir] (a download of its own) with [threads]
         * ONNX Runtime threads. sherpa-onnx's library must be loaded
         * first (it brings libonnxruntime.so); any SherpaSpeechEngine use has done so, and [load] asks for it again.
         * Throws [IllegalStateException] (missing runtime, unreadable files) or [UnsatisfiedLinkError].
         */
        fun load(dir: File, lmDir: File, threads: Int): ChatterboxNative {
            System.loadLibrary("onnxruntime")
            System.loadLibrary("audora_acted")
            return ChatterboxNative(nativeLoad(dir.absolutePath, lmDir.absolutePath, threads))
        }

        @JvmStatic
        private external fun nativeLoad(dir: String, lmDir: String, threads: Int): Long

        @JvmStatic
        private external fun nativeSpeak(
            handle: Long,
            textTokens: LongArray,
            strength: Float,
            maxTokens: Int,
            repetitionPenalty: Float,
        ): LongArray?

        @JvmStatic
        private external fun nativeDecode(handle: Long, speech: LongArray): FloatArray

        @JvmStatic
        private external fun nativeCancel(handle: Long)

        @JvmStatic
        private external fun nativeRelease(handle: Long)
    }
}
