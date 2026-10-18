// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/kws/CommandListener.kt
// Purpose: Reads the microphone while the reader is open and reports the voice commands heard (plan item 38).

package io.github.skm0078.audora.speech.kws

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.os.SystemClock
import io.github.skm0078.audora.core.config.VoiceCommandConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Why listening could not start; each is a state Settings or the reader can explain. */
enum class ListenFailure { NO_PERMISSION, NO_MODEL, MICROPHONE_BUSY }

/**
 * Listens only inside [listen], which the reader calls while it is open: Android lets an app read the microphone in
 * the foreground without a foreground service, and the privacy indicator shows meanwhile, as it should. With the
 * screen off (an opt-in) the reader keeps a `microphone` foreground service running meanwhile, started while it is
 * visible, as Android 14 requires.
 *
 * The source is VOICE_RECOGNITION (tuned for speech, no automatic gain that would pump the book's voice up between
 * words) with the platform's echo canceller where the phone has one, so the book's own audio is mostly removed
 * before the spotter hears it; the "Audora" prefix covers the rest.
 *
 * While another feature holds the microphone ([VoiceCommandPause]) the listener lets it go and waits, the model
 * still loaded, then opens it again.
 */
class CommandListener(
    context: Context,
    private val models: VoiceCommandModels,
    private val config: VoiceCommandConfig = VoiceCommandConfig(),
    private val echo: NarratorEcho = NarratorEcho.shared,
    private val pause: VoiceCommandPause = VoiceCommandPause.shared,
) {
    private val appContext = context.applicationContext

    /**
     * Blocks (off the main thread) until the calling coroutine is cancelled, handing each command heard to
     * [onCommand] on the listening thread. Returns a failure at once when it cannot start.
     */
    suspend fun listen(onCommand: (VoiceCommand) -> Unit): Outcome<Unit, ListenFailure> = withContext(Dispatchers.IO) {
        val granted = appContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        val spotter = if (granted) models.spotter(config) else null
        when {
            !granted -> Outcome.Failure(ListenFailure.NO_PERMISSION)

            spotter == null -> Outcome.Failure(ListenFailure.NO_MODEL)

            else -> try {
                listenWith(spotter, CommandRepeats(config.repeatGapMs), onCommand)
            } finally {
                spotter.release()
                AudoraLog.i(TAG, "Stopped listening for voice commands")
            }
        }
    }

    /** Opens the microphone whenever no feature holds it, until cancelled or the microphone fails. */
    private suspend fun listenWith(
        spotter: CommandSpotter,
        repeats: CommandRepeats,
        onCommand: (VoiceCommand) -> Unit,
    ): Outcome<Unit, ListenFailure> {
        var outcome: Outcome<Unit, ListenFailure>? = null
        while (outcome == null) {
            pause.awaitFree()
            val microphone = Microphone.open(config)
            if (microphone == null) {
                outcome = Outcome.Failure(ListenFailure.MICROPHONE_BUSY)
            } else {
                pause.microphone(open = true)
                val failed = try {
                    !readLoop(microphone.record, spotter, repeats, onCommand)
                } finally {
                    microphone.close()
                    pause.microphone(open = false)
                }
                if (failed) outcome = Outcome.Success(Unit) else AudoraLog.i(TAG, "Microphone let go for a pause")
            }
        }
        return outcome
    }

    /** Reads until a pause is asked for (true) or the microphone fails (false). */
    private suspend fun readLoop(
        record: AudioRecord,
        spotter: CommandSpotter,
        repeats: CommandRepeats,
        onCommand: (VoiceCommand) -> Unit,
    ): Boolean {
        val pcm = ShortArray(config.chunkSamples)
        val floats = FloatArray(config.chunkSamples)
        var read = 0
        while (read >= 0 && !pause.isPaused) {
            coroutineContext.ensureActive()
            read = record.read(pcm, 0, pcm.size)
            for (i in 0 until read) floats[i] = pcm[i] / PCM_FULL_SCALE
            val heard = if (read > 0) spotter.accept(if (read == floats.size) floats else floats.copyOf(read)) else null
            val now = SystemClock.elapsedRealtime()
            when {
                heard == null -> Unit
                echo.suppresses(now) -> AudoraLog.i(TAG, "Ignored ${heard.name}: the book itself says the app's name")
                repeats.accept(heard, now) -> onCommand(heard).also { AudoraLog.i(TAG, "Voice command ${heard.name}") }
            }
        }
        if (read < 0) AudoraLog.w(TAG, "Microphone read failed with code $read; stopped listening")
        return read >= 0
    }

    /** The started microphone and its echo canceller, released together. */
    private class Microphone(val record: AudioRecord, private val canceller: AcousticEchoCanceler?) {
        fun close() {
            canceller?.release()
            record.stop()
            record.release()
        }

        companion object {
            /** Started, or null (logged) when another app holds it or the phone refuses the format. */
            @Suppress("MissingPermission") // listen() checks RECORD_AUDIO before calling this
            fun open(config: VoiceCommandConfig): Microphone? {
                val minBuffer = AudioRecord.getMinBufferSize(config.sampleRate, CHANNEL, ENCODING)
                val bufferBytes = maxOf(minBuffer, config.chunkSamples * BYTES_PER_SAMPLE * BUFFERED_CHUNKS)
                val record = try {
                    AudioRecord(SOURCE, config.sampleRate, CHANNEL, ENCODING, bufferBytes)
                } catch (e: IllegalArgumentException) {
                    AudoraLog.e(TAG, "Could not open the microphone at ${config.sampleRate} Hz", e)
                    null
                } catch (e: SecurityException) {
                    AudoraLog.e(TAG, "Microphone permission was withdrawn", e)
                    null
                }
                return record?.let(::start)
            }

            private fun start(record: AudioRecord): Microphone? {
                val initialised = record.state == AudioRecord.STATE_INITIALIZED
                val canceller = if (initialised) echoCanceller(record.audioSessionId) else null
                if (initialised) record.startRecording()
                val recording = initialised && record.recordingState == AudioRecord.RECORDSTATE_RECORDING
                if (!recording) {
                    AudoraLog.w(TAG, "Microphone unavailable (state ${record.state}); another app may hold it")
                    canceller?.release()
                    record.release()
                }
                return if (recording) Microphone(record, canceller).also { AudoraLog.i(TAG, "Listening") } else null
            }

            /** Best effort: some phones have no canceller, or one that will not attach; listening works without. */
            private fun echoCanceller(sessionId: Int): AcousticEchoCanceler? {
                val canceller = if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(sessionId) else null
                canceller?.enabled = true
                AudoraLog.i(TAG, if (canceller != null) "Echo canceller on" else "No echo canceller on this phone")
                return canceller
            }
        }
    }

    private companion object {
        const val TAG = "Speech.Commands"
        const val SOURCE = MediaRecorder.AudioSource.VOICE_RECOGNITION
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val BYTES_PER_SAMPLE = 2
        const val BUFFERED_CHUNKS = 4
        const val PCM_FULL_SCALE = 32_768f
    }
}
