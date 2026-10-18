// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/voice/VoiceChoiceTest.kt
// Purpose: A book voice that is no longer installed is skipped, so the choice is the voice the listener hears.

package io.github.skm0078.audora.playback.voice

import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.VoiceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceChoiceTest {
    private val book = VoiceId(EngineId.SHERPA, "book-voice")
    private val session = VoiceId(EngineId.SHERPA, "session-voice")
    private val settings = VoiceId(EngineId.SYSTEM, "settings-voice")

    @Test
    fun `the book's voice wins while it is installed`() {
        assertEquals(book, VoiceChoice.of(book, { true }, session) { settings })
    }

    @Test
    fun `a deleted book voice falls back to the session's, then Settings', then the default`() {
        assertEquals(session, VoiceChoice.of(book, { false }, session) { settings })
        assertEquals(settings, VoiceChoice.of(book, { false }, null) { settings })
        assertNull(VoiceChoice.of(book, { false }, null) { null })
    }

    @Test
    fun `with no book voice the installed check is not asked`() {
        assertEquals(settings, VoiceChoice.of(null, { error("not asked") }, null) { settings })
    }
}
