// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/voice/VoiceChoice.kt
// Purpose: Which chosen voice reads a language: the book's while still installed, then the session's, then Settings'.

package io.github.skm0078.audora.playback.voice

import io.github.skm0078.audora.core.model.VoiceId

/**
 * First match wins: the open book's own voice (plan item 4) only while [canSpeak] says it is still installed, then
 * the session's (SET_VOICE), then Settings → Voices. Null means the language reads with its default. Playback and the
 * studio's audio keys both resolve through this, so a deleted book voice is neither heard nor named in a key.
 */
internal object VoiceChoice {
    fun of(book: VoiceId?, canSpeak: (VoiceId) -> Boolean, session: VoiceId?, settings: () -> VoiceId?): VoiceId? =
        book?.takeIf(canSpeak) ?: session ?: settings()
}
