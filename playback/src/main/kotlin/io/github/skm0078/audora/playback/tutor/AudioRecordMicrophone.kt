// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/tutor/AudioRecordMicrophone.kt
// Purpose: The reading tutor's microphone: AudioRecord at 16 kHz, float, tuned for speech recognition (plan item 67).

package io.github.skm0078.audora.playback.tutor

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import io.github.skm0078.audora.core.config.TutorConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * VOICE_RECOGNITION asks the phone for its speech-recognition tuning (no automatic gain or noise shaping where it
 * honours that), which is what the models were trained on. The buffer holds `micBufferMs`, so a slow read never
 * overruns. Reads block on [Dispatchers.IO]; cancelling the caller stops within one chunk and frees the recorder.
 */
class AudioRecordMicrophone(context: Context, private val config: TutorConfig) : Microphone {
    private val appContext = context.applicationContext

    // Without RECORD_AUDIO, AudioRecord comes back uninitialised rather than throwing, which would read as a busy
    // microphone; so the permission is checked first (a SecurityException is still caught in create()).
    override suspend fun record(onChunk: suspend (FloatArray) -> Unit): Outcome<Unit, TutorError> =
        withContext(Dispatchers.IO) {
            when (val opened = open()) {
                is Outcome.Failure -> opened

                is Outcome.Success -> opened.value.let { recorder ->
                    try {
                        readAll(recorder, onChunk)
                    } finally {
                        stop(recorder)
                    }
                }
            }
        }

    private fun open(): Outcome<AudioRecord, TutorError> {
        val held = ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        val minBytes = if (held) AudioRecord.getMinBufferSize(config.sampleRateHz, CHANNEL, ENCODING) else 0
        return when {
            !held -> Outcome.Failure(TutorError.NO_PERMISSION).also {
                AudoraLog.w(TAG, "Microphone permission missing")
            }

            minBytes <= 0 -> Outcome.Failure(TutorError.MICROPHONE_FAILED)
                .also { AudoraLog.e(TAG, "Microphone format not supported, code=$minBytes") }

            else -> create(maxOf(minBytes, config.micBufferSamples * Float.SIZE_BYTES))
        }
    }

    @SuppressLint("MissingPermission")
    private fun create(bytes: Int): Outcome<AudioRecord, TutorError> = try {
        val recorder =
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, config.sampleRateHz, CHANNEL, ENCODING, bytes)
        if (recorder.state == AudioRecord.STATE_INITIALIZED) {
            Outcome.Success(recorder)
        } else {
            AudoraLog.e(TAG, "Microphone not initialised")
            recorder.release()
            Outcome.Failure(TutorError.MICROPHONE_FAILED)
        }
    } catch (e: SecurityException) {
        AudoraLog.w(TAG, "Microphone permission missing: ${e.javaClass.simpleName}")
        Outcome.Failure(TutorError.NO_PERMISSION)
    } catch (e: IllegalArgumentException) {
        AudoraLog.e(TAG, "Microphone not opened: ${e.javaClass.simpleName}")
        Outcome.Failure(TutorError.MICROPHONE_FAILED)
    }

    private suspend fun readAll(
        recorder: AudioRecord,
        onChunk: suspend (FloatArray) -> Unit,
    ): Outcome<Unit, TutorError> {
        val started = try {
            recorder.startRecording()
            recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING
        } catch (e: IllegalStateException) {
            AudoraLog.e(TAG, "Microphone did not start: ${e.javaClass.simpleName}")
            false
        }
        // Android 10+ gives a busy microphone silence instead of an error; that would read as a reader saying nothing.
        val silenced = started && recorder.activeRecordingConfiguration?.isClientSilenced == true
        if (!started || silenced) {
            AudoraLog.e(TAG, "Microphone did not start, silenced=$silenced (another app may hold it)")
            return Outcome.Failure(TutorError.MICROPHONE_FAILED)
        }
        val buffer = FloatArray(config.micChunkSamples)
        var failure: Int? = null
        while (failure == null && currentCoroutineContext().isActive) {
            val read = recorder.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
            when {
                read < 0 -> failure = read
                read > 0 -> onChunk(buffer.copyOf(read))
            }
        }
        if (failure != null) AudoraLog.e(TAG, "Microphone read failed, code=$failure")
        return if (failure == null) Outcome.Success(Unit) else Outcome.Failure(TutorError.MICROPHONE_FAILED)
    }

    private fun stop(recorder: AudioRecord) {
        try {
            recorder.stop()
        } catch (e: IllegalStateException) {
            // It never started recording; releasing is all that is left.
            AudoraLog.d(TAG, "Microphone was not recording: ${e.javaClass.simpleName}")
        }
        recorder.release()
    }

    private companion object {
        const val TAG = "Playback.TutorMic"
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_FLOAT
    }
}
