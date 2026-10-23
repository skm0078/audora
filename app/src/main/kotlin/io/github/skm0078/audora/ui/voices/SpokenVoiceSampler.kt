// File: app/src/main/kotlin/io/github/skm0078/audora/ui/voices/SpokenVoiceSampler.kt
// Purpose: The Voices screen's "Play sample": one line in the row's own language and voice, outside the book player.

package io.github.skm0078.audora.ui.voices

import android.content.Context
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.playback.quiz.QuizSpeaker
import io.github.skm0078.audora.speech.voices.VoiceRow

/**
 * Speaks with the playback voices and the listener's speed and style, so the sample sounds like reading will. The
 * speaker frees its voice models on its own after a quiet while; a playing book pauses for the sample and goes on.
 */
class SpokenVoiceSampler(private val context: Context) : VoiceSampler {
    /** Made on the first sample, so leaving the screen without playing one costs nothing. */
    private var speaker: QuizSpeaker? = null

    override suspend fun play(row: VoiceRow): Long? {
        val made = speaker ?: QuizSpeaker(context).also { speaker = it }
        return made.speakFor(sampleLine(row.lang), row.lang, row.id)
    }

    override fun stop() {
        speaker?.stop()
    }
}

/**
 * In the voice's language, not the app's: a Hindi voice is heard reading Hindi on an English phone. Worded so it fits
 * any speaker (no gendered verb in Hindi).
 */
internal fun sampleLine(lang: Lang): String = when (lang) {
    Lang.EN -> "Hello! This is how your books will sound when I read them aloud."
    Lang.HI -> "नमस्ते! आपकी किताबें इस आवाज़ में कुछ ऐसी सुनाई देंगी।"
    Lang.BN -> "নমস্কার! আপনার বই এই ভয়েসে ঠিক এভাবেই পড়ে শোনানো হবে।"
}
