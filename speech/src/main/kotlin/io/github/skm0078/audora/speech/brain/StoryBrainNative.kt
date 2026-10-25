// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/brain/StoryBrainNative.kt
// Purpose: The JNI side of libaudora_brain.so: the story brain's language model on llama.cpp (ADR-078).

package io.github.skm0078.audora.speech.brain

import io.github.skm0078.audora.core.logging.AudoraLog
import java.io.File

/** A loaded language model that answers one prompt at a time; a fake stands in for it in tests. */
interface BrainModel : AutoCloseable {
    /** The answer to [prompt] under [grammar], or null when it did not finish (cancelled, too long, or failed). */
    fun answer(prompt: String, grammar: String, maxTokens: Int): String?

    /** Stops the answer in progress, from any thread. */
    fun cancel()
}

/**
 * One loaded model (2.7 GB of weights, mapped from the file rather than read into memory). Not thread-safe except
 * [cancel]: the story brain calls it from its one thread. [close] frees it; later calls fail.
 */
internal class StoryBrainNative private constructor(private var handle: Long) : BrainModel {
    override fun answer(prompt: String, grammar: String, maxTokens: Int): String? =
        nativeAnswer(live(), prompt.toByteArray(Charsets.UTF_8), grammar.toByteArray(Charsets.UTF_8), maxTokens)
            ?.toString(Charsets.UTF_8)

    override fun cancel() {
        val current = handle
        if (current != 0L) nativeCancel(current)
    }

    override fun close() {
        val current = handle
        handle = 0L
        if (current != 0L) nativeFree(current)
    }

    private fun live(): Long = handle.also { check(it != 0L) { "story brain model was released" } }

    companion object {
        private const val TAG = "Speech.Brain"

        /** Whether the library and a ggml CPU variant for these cores loaded: null until first asked. */
        @Volatile
        private var ready: Boolean? = null

        /**
         * Whether this build and phone can run the brain at all: the library ships for arm64 only (an emulator's
         * x86_64 build has none) and ggml needs a CPU variant the cores can run. Asked once per process.
         */
        @Synchronized
        fun available(): Boolean = ready ?: try {
            System.loadLibrary("audora_brain")
            nativeInit()
        } catch (e: UnsatisfiedLinkError) {
            AudoraLog.i(TAG, "Story brain library not in this build: ${e.message}")
            false
        }.also { ready = it }

        /** Null when the model does not load (the reason is in logcat) or [available] is false. */
        fun load(model: File, contextTokens: Int, threads: Int): StoryBrainNative? {
            if (!available()) return null
            val handle = nativeLoad(model.absolutePath.toByteArray(Charsets.UTF_8), contextTokens, threads)
            return if (handle == 0L) null else StoryBrainNative(handle)
        }

        @JvmStatic
        private external fun nativeInit(): Boolean

        @JvmStatic
        private external fun nativeLoad(path: ByteArray, contextTokens: Int, threads: Int): Long

        @JvmStatic
        private external fun nativeAnswer(
            handle: Long,
            prompt: ByteArray,
            grammar: ByteArray,
            maxTokens: Int,
        ): ByteArray?

        @JvmStatic
        private external fun nativeCancel(handle: Long)

        @JvmStatic
        private external fun nativeFree(handle: Long)
    }
}
