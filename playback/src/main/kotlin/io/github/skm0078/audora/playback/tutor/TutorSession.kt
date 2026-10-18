// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/tutor/TutorSession.kt
// Purpose: One listening run of the reading tutor: microphone → speech model → read-along marks (plan item 67).

package io.github.skm0078.audora.playback.tutor

import io.github.skm0078.audora.core.config.TutorConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.tutor.PageWords
import io.github.skm0078.audora.core.tutor.ReadAlong
import io.github.skm0078.audora.speech.listen.Heard
import io.github.skm0078.audora.speech.listen.Listener
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Why listening stopped early. */
enum class TutorError {
    /** The speech model is installed but would not load (a damaged file); deleting and downloading again helps. */
    MODEL_FAILED,

    /** RECORD_AUDIO is not granted. */
    NO_PERMISSION,

    /** The microphone would not open or stopped (another app holding it, a hardware error). */
    MICROPHONE_FAILED,
}

/** Audio in: 16 kHz mono floats in [-1, 1], a chunk at a time, until cancelled. */
fun interface Microphone {
    suspend fun record(onChunk: suspend (FloatArray) -> Unit): Outcome<Unit, TutorError>
}

/**
 * One run, started by [listen] and ended by the reader reaching the page's end, by [stop] (the stretch under way is
 * finished and counted), or by cancelling the caller (nothing more is counted). The microphone is read on its own
 * coroutine into a queue of `micBufferMs`, so a phrase being decoded never loses audio; decoding runs on [work].
 * What is heard is the reader's voice: never logged, never stored.
 */
class TutorSession(
    private val microphone: Microphone,
    private val gate: MicrophoneGate,
    private val config: TutorConfig,
    private val work: CoroutineDispatcher = Dispatchers.Default,
) {
    private val recording = AtomicReference<Job?>(null)
    private val stopped = AtomicBoolean(false)

    /**
     * Listens from [start] until the run ends, reporting each change to [onChange] (on [work]). [openListener] loads
     * the speech model (a second or two); the model is closed when the run ends, however it ends, even if the caller
     * is cancelled while it loads. The microphone is held through [gate] only while recording.
     */
    @Suppress("TooGenericExceptionCaught") // sherpa-onnx reports native decode errors as plain RuntimeException.
    suspend fun listen(
        start: ReadAlong,
        openListener: () -> Listener?,
        onChange: (ReadAlong) -> Unit,
    ): Outcome<ReadAlong, TutorError> {
        var listener: Listener? = null
        return try {
            withContext(work) { listener = openListener() }
            val opened = listener
            if (opened == null) {
                Outcome.Failure(TutorError.MODEL_FAILED)
            } else {
                // Taken where cancelling cannot drop it: a hold lost between taking and use would keep voice
                // commands paused until the process dies.
                withContext(NonCancellable) { gate.hold(REASON) }.use { run(opened, start, onChange) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            AudoraLog.e(TAG, "Speech model failed while listening: ${e.javaClass.simpleName}")
            Outcome.Failure(TutorError.MODEL_FAILED)
        } finally {
            // Also when the caller is cancelled while the model loads: the loaded model is native memory.
            listener?.let { withContext(NonCancellable + work) { it.close() } }
        }
    }

    /** Ends the run after the stretch under way; safe from any thread, before or during [listen]. */
    fun stop() {
        stopped.set(true)
        recording.get()?.cancel()
    }

    private suspend fun run(
        listener: Listener,
        start: ReadAlong,
        onChange: (ReadAlong) -> Unit,
    ): Outcome<ReadAlong, TutorError> = coroutineScope {
        val chunks = Channel<FloatArray>(config.micQueueChunks)
        var micOutcome: Outcome<Unit, TutorError> = Outcome.Success(Unit)
        val job = launch { micOutcome = microphone.record { chunks.send(it) } }
        job.invokeOnCompletion { chunks.close() }
        recording.set(job)
        if (stopped.get()) job.cancel()
        AudoraLog.i(TAG, "Listening, words=${start.words.size}")
        val along = withContext(work) { follow(listener, chunks, start, onChange) }
        job.cancel()
        job.join()
        val failure = micOutcome as? Outcome.Failure
        AudoraLog.i(TAG, "Stopped listening, finished=${along.finished}, failed=${failure?.error}")
        failure ?: Outcome.Success(along)
    }

    private suspend fun follow(
        listener: Listener,
        chunks: ReceiveChannel<FloatArray>,
        start: ReadAlong,
        onChange: (ReadAlong) -> Unit,
    ): ReadAlong {
        var along = start
        for (chunk in chunks) {
            along = mark(along, listener.accept(chunk), onChange)
            if (along.finished) break
        }
        return if (along.finished) along else mark(along, listener.finish(), onChange)
    }

    private fun mark(along: ReadAlong, heard: List<Heard>, onChange: (ReadAlong) -> Unit): ReadAlong =
        heard.fold(along) { current, stretch ->
            val next = current.heard(PageWords.heard(stretch.text), stretch.final)
            if (next.marks != current.marks || next.next != current.next) onChange(next)
            next
        }

    private companion object {
        const val TAG = "Playback.Tutor"
        const val REASON = "reading tutor"
    }
}
