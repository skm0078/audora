// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/voice/ReplayRender.kt
// Purpose: The one sentence "What did it say?" renders again, slower, and the style it renders with.

package io.github.skm0078.audora.playback.voice

import io.github.skm0078.audora.core.model.SpeechStyle
import io.github.skm0078.audora.playback.queue.UnitKey

/**
 * With smart speed stretching the voice, a replay renders its sentence again at the slower speed instead of slowing
 * the stretched audio with the player (ADR-049). The session sets the sentence and its style before it rebuilds from
 * it; [EngineRenderer] renders that sentence with it; the next sentence to start clears it, so every later render is
 * the listener's own style. Only sentences count: the session passes no placeholder (a rebuild's loading item starts
 * before the replayed sentence does). Written on the main thread, read on the render worker.
 */
class ReplayRender {
    private class Replay(val key: UnitKey, val style: SpeechStyle)

    @Volatile private var replay: Replay? = null

    fun set(key: UnitKey, style: SpeechStyle) {
        replay = Replay(key, style)
    }

    /** Ends any replay (the speech style changed, so every sentence renders again in the new one). */
    fun clear() {
        replay = null
    }

    /** The style to render [key] with, when it is the sentence replayed. */
    fun styleFor(key: UnitKey): SpeechStyle? = replay?.takeIf { it.key == key }?.style

    /**
     * A sentence started playing: any other than the one replayed ends the replay. Returns the sentence whose replay
     * just ended, if any: its slower audio is still in the render caches and must be dropped.
     */
    fun onSentence(key: UnitKey): UnitKey? {
        val ended = replay?.key?.takeIf { it != key } ?: return null
        replay = null
        return ended
    }
}
