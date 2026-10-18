// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/voice/ReplayRender.kt
// Purpose: The one sentence "What did it say?" renders again, slower, and the style it renders with.

package io.github.skm0078.audora.playback.voice

import io.github.skm0078.audora.core.model.SpeechStyle
import io.github.skm0078.audora.playback.queue.UnitKey

/**
 * With smart speed stretching the voice, a replay renders its sentence again at the slower speed instead of slowing
 * the stretched audio with the player (ADR-049). The session sets the sentence and its style before it rebuilds from
 * it; [EngineRenderer] renders that sentence with it; the next sentence to start clears it, so every later render is
 * the listener's own style. Written on the main thread, read on the render worker.
 */
class ReplayRender {
    private class Replay(val key: UnitKey, val style: SpeechStyle)

    @Volatile private var replay: Replay? = null

    fun set(key: UnitKey, style: SpeechStyle) {
        replay = Replay(key, style)
    }

    /** The style to render [key] with, when it is the sentence replayed. */
    fun styleFor(key: UnitKey): SpeechStyle? = replay?.takeIf { it.key == key }?.style

    /** A sentence started playing: any other than the one replayed ends the replay. */
    fun onSentence(key: UnitKey?) {
        if (replay?.key != key) replay = null
    }
}
