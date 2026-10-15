// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/session/PlayerFaults.kt
// Purpose: Classifies a Media3 PlaybackException into the PlayerFault recovery understands.

package io.github.skm0078.audora.playback.session

import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.StuckPlayerException
import androidx.media3.common.util.UnstableApi
import io.github.skm0078.audora.playback.queue.RenderFailedException
import io.github.skm0078.audora.playback.queue.UnitKey
import io.github.skm0078.audora.playback.recovery.PlayerFault

@OptIn(UnstableApi::class)
object PlayerFaults {
    /** Walks the cause chain: a render failure arrives wrapped in a source error, a stuck player as a cause. */
    fun classify(error: PlaybackException, currentKey: UnitKey?): PlayerFault {
        val causes = generateSequence<Throwable>(error) { it.cause }.take(MAX_CAUSE_DEPTH)
        val render = causes.filterIsInstance<RenderFailedException>().firstOrNull()
        val stuck = causes.filterIsInstance<StuckPlayerException>().firstOrNull()
        return when {
            render != null -> PlayerFault.RenderFailed(render.key, render.error)

            stuck != null -> PlayerFault.Stuck(
                currentKey,
                suppressed =
                    stuck.stuckType == StuckPlayerException.STUCK_SUPPRESSED,
            )

            else -> PlayerFault.Other(error.errorCodeName)
        }
    }

    fun keyOf(fault: PlayerFault): UnitKey? = when (fault) {
        is PlayerFault.RenderFailed -> fault.key
        is PlayerFault.Stuck -> fault.key
        is PlayerFault.Other -> null
    }

    /** Guards against a cause cycle. */
    private const val MAX_CAUSE_DEPTH = 16
}
