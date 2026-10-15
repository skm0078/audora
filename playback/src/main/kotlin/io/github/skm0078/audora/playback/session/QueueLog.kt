// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/session/QueueLog.kt
// Purpose: Logs render results with book and sentence position, never the sentence text (AGENTS.md).

package io.github.skm0078.audora.playback.session

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.playback.queue.RenderListener
import io.github.skm0078.audora.playback.queue.UnitKey
import io.github.skm0078.audora.speech.SpeechError
import io.github.skm0078.audora.speech.audio.PcmAudio
import java.util.concurrent.TimeUnit

/** Called on the render worker thread. */
class QueueLog(private val id: BookId) : RenderListener {
    override fun onRendered(key: UnitKey, renderNanos: Long, audio: PcmAudio) {
        val renderMs = TimeUnit.NANOSECONDS.toMillis(renderNanos)
        AudoraLog.d(TAG, "Rendered book=${id.logLabel} $key audioMs=${audio.durationMs} renderMs=$renderMs")
    }

    override fun onRenderFailed(key: UnitKey, error: SpeechError) {
        AudoraLog.w(TAG, "Render failed book=${id.logLabel} $key: ${error.message}")
    }

    private companion object {
        const val TAG = "Playback.Render"
    }
}
