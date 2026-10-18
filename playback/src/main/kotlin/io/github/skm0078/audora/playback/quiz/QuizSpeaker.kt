// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/quiz/QuizSpeaker.kt
// Purpose: Reads a quiz card aloud in the listener's own voices and style, outside the book player (plan item 70).

package io.github.skm0078.audora.playback.quiz

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.playback.voice.EngineRenderer
import io.github.skm0078.audora.playback.voice.SpeechStyleStore
import io.github.skm0078.audora.playback.voice.SpeechVoices
import io.github.skm0078.audora.playback.voice.VoiceEngines
import io.github.skm0078.audora.speech.audio.PcmAudio
import java.io.Closeable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders one line with the same voices, speed and style playback uses (the language's chosen voice, no book's own)
 * and plays it once. It asks for short audio focus, so a playing book pauses for the question and goes on after.
 * One per quiz screen; [close] frees the voices.
 */
class QuizSpeaker(context: Context) : Closeable {
    private val appContext = context.applicationContext
    private val style = SpeechStyleStore.forContext(appContext)
    private val voices = SpeechVoices(VoiceEngines(appContext))
    private val renderer = EngineRenderer(voices, style = { style.current }, styleConfig = style.config)
    private val audio = appContext.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(attributes)
        .build()

    @Volatile
    private var track: AudioTrack? = null

    /** Renders and starts [text] in [lang], stopping anything still playing; false (logged) if the voice failed. */
    suspend fun speak(text: String, lang: Lang): Boolean = withContext(Dispatchers.IO) {
        stop()
        val unit = ReadingUnit(0, 0, 0, text.length, text, lang, pauseAfterMs = 0, bounds = null)
        when (val rendered = renderer.render(unit)) {
            is Outcome.Success -> play(rendered.value)

            is Outcome.Failure -> {
                AudoraLog.w(TAG, "Quiz line not spoken, lang=${lang.name}: ${rendered.error.message}")
                false
            }
        }
    }

    @Synchronized
    fun stop() {
        track?.let { playing ->
            track = null
            runCatching { playing.stop() }
            playing.release()
            audio.abandonAudioFocusRequest(focus)
        }
    }

    override fun close() {
        stop()
        voices.release()
    }

    private fun play(pcm: PcmAudio): Boolean {
        if (pcm.samples.isEmpty()) return false
        val bytes = pcm.samples.size * BYTES_PER_SAMPLE
        val made = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(pcm.sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(bytes)
            .build()
        made.write(pcm.samples, 0, pcm.samples.size)
        audio.requestAudioFocus(focus)
        made.notificationMarkerPosition = pcm.samples.size
        made.setPlaybackPositionUpdateListener(
            object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(finished: AudioTrack) {
                    if (track === finished) stop()
                }

                override fun onPeriodicNotification(playing: AudioTrack) = Unit
            },
        )
        track = made
        made.play()
        return true
    }

    private companion object {
        const val TAG = "Quiz.Speaker"
        const val BYTES_PER_SAMPLE = 2
    }
}
