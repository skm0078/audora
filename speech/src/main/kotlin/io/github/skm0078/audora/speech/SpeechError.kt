// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/SpeechError.kt
// Purpose: Typed reasons a voice cannot speak, each with a message clear enough to show and to log.

package io.github.skm0078.audora.speech

import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.VoiceId

/** Why rendering failed. [message] names the engine, voice and language, never the text being read. */
sealed interface SpeechError {
    val message: String

    /** The engine works, but this voice cannot speak offline right now. */
    data class VoiceUnavailable(
        val engine: EngineId,
        val reason: Reason,
        val voice: VoiceId? = null,
        val lang: Lang? = null,
        val detail: String = "",
    ) : SpeechError {
        enum class Reason {
            /** No voice with that id in this engine. */
            UNKNOWN_VOICE,

            /** The phone's TTS engine has no offline voice data for the language; the user can download it. */
            NOT_INSTALLED,

            /** The only voice for the language needs the internet, which Audora never uses for speech. */
            NETWORK_ONLY,

            /** A neural voice's model files are not where they should be (broken APK or deleted download). */
            MODEL_FILES_MISSING,
        }

        override val message: String
            get() {
                val who = voice?.toString() ?: "${engine.name.lowercase()} voice"
                val language = lang?.let { " for ${languageName(it)}" } ?: ""
                val what = when (reason) {
                    Reason.UNKNOWN_VOICE -> "$who does not exist"

                    Reason.NOT_INSTALLED ->
                        "No offline voice$language is installed in the phone's text-to-speech " +
                            "engine. Download it in Android Settings → Text-to-speech output"

                    Reason.NETWORK_ONLY ->
                        "The phone's only voice$language needs the internet; Audora reads offline. " +
                            "Download the offline voice in Android Settings → Text-to-speech output"

                    Reason.MODEL_FILES_MISSING -> "$who is missing model files"
                }
                return if (detail.isEmpty()) what else "$what ($detail)"
            }
    }

    /** The engine itself cannot run: no TTS engine on the phone, it did not start, or it was released. */
    data class EngineUnavailable(val engine: EngineId, val detail: String) : SpeechError {
        override val message: String get() = "${engine.name.lowercase()} engine unavailable: $detail"
    }

    /** The engine accepted the request and then failed. [cause] carries the underlying exception, if any. */
    data class SynthesisFailed(val voice: VoiceId, val detail: String, val cause: Throwable? = null) : SpeechError {
        override val message: String get() = "$voice failed to render: $detail"
    }
}

private fun languageName(lang: Lang): String = when (lang) {
    Lang.EN -> "English"
    Lang.HI -> "Hindi"
    Lang.BN -> "Bengali"
}
