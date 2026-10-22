// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/quiz/QuizSpeaker.kt
// Purpose: Reads a quiz card aloud in the listener's own voices and style, outside the book player (plan item 70).

package io.github.skm0078.audora.playback.quiz

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import io.github.skm0078.audora.core.config.QuizConfig
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
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Renders one line with the same voices, speed and style playback uses (the language's chosen voice, no book's own;
 * the listener's expressiveness; the player's share of the speed, ADR-033) and plays it once. It asks for short audio
 * focus, so a playing book pauses for the question and goes on after.
 *
 * One line at a time: a new [speak] supersedes the one before, which is stopped if playing and dropped if still
 * rendering, and nothing plays after [close]. Its voices are a second copy of the playback service's (a neural model
 * is 60-150 MB), so they are freed after [idleMs] without a line and loaded again (a second or two) on the next one.
 */
class QuizSpeaker(context: Context, private val idleMs: Long = QuizConfig().speakerIdleMs) : Closeable {
    private val appContext = context.applicationContext
    private val style = SpeechStyleStore.forContext(appContext)
    private val audio = appContext.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(attributes)
        .build()
    private val generation = AtomicLong()
    private val rendering = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var idleRelease: Job? = null

    /** Created on the first line, freed when idle; guarded by [rendering]. */
    private var voices: SpeechVoices? = null
    private var renderer: EngineRenderer? = null

    @Volatile
    private var closed = false

    @Volatile
    private var track: AudioTrack? = null

    /** Renders and starts [text] in [lang], superseding any line before; false (logged) if not spoken. */
    suspend fun speak(text: String, lang: Lang): Boolean = withContext(Dispatchers.IO) {
        val mine = generation.incrementAndGet()
        stop()
        val rendered = rendering.withLock {
            if (closed || mine != generation.get()) return@withContext false
            idleRelease?.cancel()
            val unit = ReadingUnit(0, 0, 0, text.length, text, lang, pauseAfterMs = 0, bounds = null)
            rendererLocked().render(unit).also { scheduleRelease() }
        }
        when (rendered) {
            is Outcome.Success -> play(rendered.value, mine)

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

    /**
     * Stops any line and frees the voices now instead of after the idle wait (the reading tutor loads its speech model
     * next); the next line loads them again.
     */
    fun release() {
        generation.incrementAndGet()
        stop()
        idleRelease?.cancel()
        scope.launch { rendering.withLock { releaseLocked() } }
    }

    override fun close() {
        closed = true
        generation.incrementAndGet()
        stop()
        scope.launch { rendering.withLock { releaseLocked() } }
    }

    private fun rendererLocked(): EngineRenderer = renderer ?: run {
        val engines = VoiceEngines(appContext) { style.current.shaping.expressiveness }
        val made = SpeechVoices(engines)
        voices = made
        EngineRenderer(made, style = { style.current }, styleConfig = style.config).also { renderer = it }
    }

    private fun scheduleRelease() {
        idleRelease = scope.launch {
            delay(idleMs)
            rendering.withLock { releaseLocked() }
        }
    }

    private fun releaseLocked() {
        voices?.release()
        voices = null
        renderer = null
        if (closed) scope.cancel() else AudoraLog.i(TAG, "Quiz voices freed")
    }

    /** Plays [pcm] unless a newer line or [close] came first; false (logged) if the audio output refused it. */
    @Suppress("TooGenericExceptionCaught") // AudioTrack throws several unchecked types; none may crash the quiz.
    @Synchronized
    private fun play(pcm: PcmAudio, mine: Long): Boolean {
        if (pcm.samples.isEmpty() || closed || mine != generation.get()) return false
        var made: AudioTrack? = null
        return try {
            made = build(pcm)
            made.write(pcm.samples, 0, pcm.samples.size)
            setSpeed(made)
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
            true
        } catch (e: RuntimeException) {
            AudoraLog.w(TAG, "Quiz line not played: ${e.javaClass.simpleName}")
            if (track === made) track = null
            made?.release()
            audio.abandonAudioFocusRequest(focus)
            false
        }
    }

    /** The player's share of the speed, as the book player applies it; a track that refuses plays at 1x (logged). */
    private fun setSpeed(made: AudioTrack) {
        try {
            made.playbackParams = made.playbackParams.setSpeed(style.config.playerSpeed(style.current.rate))
        } catch (e: IllegalArgumentException) {
            AudoraLog.w(TAG, "Quiz line speed not set: ${e.javaClass.simpleName}")
        } catch (e: IllegalStateException) {
            AudoraLog.w(TAG, "Quiz line speed not set: ${e.javaClass.simpleName}")
        }
    }

    private fun build(pcm: PcmAudio): AudioTrack = AudioTrack.Builder()
        .setAudioAttributes(attributes)
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(pcm.sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
        )
        .setTransferMode(AudioTrack.MODE_STATIC)
        .setBufferSizeInBytes(pcm.samples.size * BYTES_PER_SAMPLE)
        .build()

    private companion object {
        const val TAG = "Quiz.Speaker"
        const val BYTES_PER_SAMPLE = 2
    }
}
