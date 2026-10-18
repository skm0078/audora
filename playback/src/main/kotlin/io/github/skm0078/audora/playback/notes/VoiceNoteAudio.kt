// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/notes/VoiceNoteAudio.kt
// Purpose: Records, plays and deletes voice-note files in the app's private voice-notes folder (plan item 32).

package io.github.skm0078.audora.playback.notes

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.SystemClock
import io.github.skm0078.audora.core.config.VoiceNoteConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import java.io.File
import java.io.IOException
import java.util.UUID

sealed interface VoiceNoteError {
    val message: String

    /** The microphone is busy (a call, another app) or refused to start. */
    data class Recorder(override val message: String) : VoiceNoteError

    /** The recording was shorter than a slip of the finger; nothing was kept. */
    data object TooShort : VoiceNoteError {
        override val message = "too short"
    }

    data class Player(override val message: String) : VoiceNoteError
}

/**
 * Files are `<random>.m4a` (AAC in MP4): the name says nothing about the book or the note. Main thread: Android's
 * recorder and player call back on it. The caller holds RECORD_AUDIO before [record].
 */
class VoiceNoteAudio(context: Context, private val config: VoiceNoteConfig = VoiceNoteConfig()) {
    private val appContext = context.applicationContext
    private val folder = File(appContext.filesDir, config.folder)

    fun file(fileName: String): File = File(folder, fileName)

    /** Starts recording to a new file; [onLimit] runs if the recording reaches the length limit by itself. */
    @Suppress("TooGenericExceptionCaught") // MediaRecorder reports a busy microphone as a bare RuntimeException.
    fun record(onLimit: () -> Unit): Outcome<Recording, VoiceNoteError> {
        if (!folder.isDirectory) folder.mkdirs()
        val name = "${UUID.randomUUID()}$EXTENSION"
        val recorder = MediaRecorder(appContext)
        return try {
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(1)
                setAudioEncodingBitRate(config.bitRate)
                setAudioSamplingRate(config.sampleRate)
                setMaxDuration(config.maxDurationMs)
                setOutputFile(file(name))
                setOnInfoListener { _, what, _ ->
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) onLimit()
                }
                prepare()
                start()
            }
            AudoraLog.i(TAG, "Voice note recording started")
            Outcome.Success(Recording(name, recorder, SystemClock.elapsedRealtime()))
        } catch (e: IOException) {
            recorder.release()
            file(name).delete()
            recorderFailed("start", e)
        } catch (e: RuntimeException) {
            // IllegalStateException, and the bare RuntimeException a busy microphone gives ("start failed.",
            // "setAudioSource failed."): a failed start, not a crash.
            recorder.release()
            file(name).delete()
            recorderFailed("start", e)
        }
    }

    /** One recording in progress. MediaRecorder.stop() throws a bare RuntimeException when no audio was written. */
    @Suppress("TooGenericExceptionCaught")
    inner class Recording internal constructor(
        val fileName: String,
        private val recorder: MediaRecorder,
        private val startedAt: Long,
    ) {
        fun elapsedMs(): Long = SystemClock.elapsedRealtime() - startedAt

        /** Stops and keeps the file; returns its length. A too-short one is deleted ([VoiceNoteError.TooShort]). */
        fun finish(): Outcome<Long, VoiceNoteError> {
            val duration = elapsedMs()
            val stopped = try {
                recorder.stop()
                true
            } catch (e: RuntimeException) {
                // stop() throws when nothing was written (stopped right after start).
                AudoraLog.w(TAG, "Voice note recorder stopped with no audio", e)
                false
            } finally {
                recorder.release()
            }
            return when {
                !stopped || duration < config.minDurationMs -> {
                    file(fileName).delete()
                    Outcome.Failure(VoiceNoteError.TooShort)
                }

                else -> Outcome.Success(duration.coerceAtMost(config.maxDurationMs.toLong()))
            }
        }

        /** Stops and deletes the file. */
        fun cancel() {
            try {
                recorder.stop()
            } catch (e: RuntimeException) {
                AudoraLog.d(TAG, "Voice note recorder cancelled before any audio: ${e.message}")
            } finally {
                recorder.release()
                file(fileName).delete()
            }
        }
    }

    /** Plays [fileName]; [onDone] runs when it ends or fails. Stop it with [Playing.stop]. */
    fun play(fileName: String, onDone: () -> Unit): Outcome<Playing, VoiceNoteError> {
        val player = MediaPlayer()
        return try {
            player.setDataSource(file(fileName).path)
            player.setOnCompletionListener { onDone() }
            player.setOnErrorListener { _, what, extra ->
                AudoraLog.w(TAG, "Voice note playback failed: what=$what extra=$extra")
                onDone()
                true
            }
            player.prepare()
            player.start()
            Outcome.Success(Playing(player))
        } catch (e: IOException) {
            player.release()
            AudoraLog.w(TAG, "Voice note not played", e)
            Outcome.Failure(VoiceNoteError.Player(e.message ?: "unreadable"))
        }
    }

    class Playing internal constructor(private val player: MediaPlayer) {
        fun stop() = player.release()
    }

    fun delete(fileName: String) {
        if (!file(fileName).delete()) AudoraLog.w(TAG, "Voice note file was already gone")
    }

    /** Deletes recordings no note points to (their book was removed), once older than the orphan age. */
    fun dropFilesNotIn(kept: Set<String>, now: Long = System.currentTimeMillis()) {
        val orphans = folder.listFiles().orEmpty()
            .filter { it.name !in kept && now - it.lastModified() > config.orphanAgeMs }
        orphans.forEach { it.delete() }
        if (orphans.isNotEmpty()) AudoraLog.i(TAG, "Voice note files dropped: ${orphans.size}")
    }

    private fun recorderFailed(what: String, e: Exception): Outcome<Recording, VoiceNoteError> {
        AudoraLog.e(TAG, "Voice note recorder failed to $what", e)
        return Outcome.Failure(VoiceNoteError.Recorder(e.message ?: what))
    }

    private companion object {
        const val TAG = "VoiceNotes"
        const val EXTENSION = ".m4a"
    }
}
