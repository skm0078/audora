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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Listens only inside [listen], which the reader calls while it is open: Android lets an app read the microphone in
 * the foreground without a foreground service, and the privacy indicator shows meanwhile, as it should. With the
 * screen off (an opt-in) the reader keeps a `microphone` foreground service running meanwhile, started while it is
 * visible, as Android 14 requires (ADR-051).
 *
 * The source is VOICE_RECOGNITION (tuned for speech, no automatic gain that would pump the book's voice up between
 * words) with the platform's echo canceller where the phone has one; the "Audora" prefix and [NarratorEcho] cover
 * the book's own voice. Quiet chunks are not decoded ([EnergyGate]).
 *
 * While another feature holds the microphone ([VoiceCommandPause]) the listener lets it go and waits, the model
 * still loaded, then opens it again ([ListenLoop]).
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
     * Blocks (off the main thread) until the calling coroutine is cancelled, reporting to [events] on the listening
     * thread. Returns a failure at once when it cannot start, or later when the microphone stops working.
     */
    suspend fun listen(events: ListenEvents): Outcome<Unit, ListenFailure> = withContext(Dispatchers.IO) {
        val granted = appContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        val spotter = if (granted) models.spotter(config) else null
        when {
            !granted -> Outcome.Failure(ListenFailure.NO_PERMISSION)

            spotter == null -> Outcome.Failure(ListenFailure.NO_MODEL)

            else -> try {
                val open = { AudioRecordMicrophone.open(config) }
                ListenLoop(spotter, open, config, echo, pause, SystemClock::elapsedRealtime).run(events)
            } finally {
                spotter.release()
                AudoraLog.i(TAG, "Stopped listening for voice commands")
            }
        }
    }

    private companion object {
        const val TAG = "Speech.Commands"
    }
}

/** The started recorder and its echo canceller, released together. */
private class AudioRecordMicrophone(private val record: AudioRecord, private val canceller: AcousticEchoCanceler?) :
    CommandMicrophone {
    override fun read(pcm: ShortArray): Int = record.read(pcm, 0, pcm.size)

    override fun silenced(): Boolean = record.activeRecordingConfiguration?.isClientSilenced == true

    override fun close() {
        canceller?.release()
        try {
            record.stop()
        } catch (e: IllegalStateException) {
            AudoraLog.w(TAG, "Microphone was already stopped", e)
        } finally {
            record.release()
        }
    }

    companion object {
        private const val TAG = "Speech.Commands"
        private const val SOURCE = MediaRecorder.AudioSource.VOICE_RECOGNITION
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val BYTES_PER_SAMPLE = 2
        private const val BUFFERED_CHUNKS = 4

        /** Started, or null (logged) when another app holds it or the phone refuses the format. */
        @Suppress("MissingPermission") // listen() checks RECORD_AUDIO before calling this
        fun open(config: VoiceCommandConfig): CommandMicrophone? {
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

        private fun start(record: AudioRecord): CommandMicrophone? {
            val initialised = record.state == AudioRecord.STATE_INITIALIZED
            val canceller = if (initialised) echoCanceller(record.audioSessionId) else null
            if (initialised) record.startRecording()
            val recording = initialised && record.recordingState == AudioRecord.RECORDSTATE_RECORDING
            if (!recording) {
                AudoraLog.w(TAG, "Microphone unavailable (state ${record.state}); another app may hold it")
                canceller?.release()
                record.release()
            }
            return if (recording) {
                AudioRecordMicrophone(record, canceller).also {
                    AudoraLog.i(TAG, "Listening")
                }
            } else {
                null
            }
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
