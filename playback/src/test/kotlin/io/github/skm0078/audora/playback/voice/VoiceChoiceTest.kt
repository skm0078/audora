// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/voice/VoiceChoiceTest.kt
// Purpose: A book voice that is no longer installed is skipped, so the choice is the voice the listener hears.

package io.github.skm0078.audora.playback.voice

import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
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
        assertEquals(book, VoiceChoice.of(book, { true }) { settings })
    }

    @Test
    fun `a deleted book voice falls back to Settings', then the default`() {
        assertEquals(settings, VoiceChoice.of(book, { false }) { settings })
        assertNull(VoiceChoice.of(book, { false }) { null })
    }

    @Test
    fun `with no book voice the installed check is not asked`() {
        assertEquals(settings, VoiceChoice.of(null, { error("not asked") }) { settings })
    }

    @Test
    fun `a run's fixed choices hold when the live ones change, a language left to its default included`() {
        val live = HashMap<Lang, VoiceId?>(mapOf(Lang.EN to book))
        val choices = FixedChoices()
        assertEquals(book, choices.choiceFor(Lang.EN) { live[it] })

        val fixed = choices.fix { live[it] }
        live[Lang.EN] = session
        live[Lang.HI] = settings

        assertEquals(Lang.entries.toSet(), fixed.keys)
        assertEquals(book, choices.choiceFor(Lang.EN) { live[it] })
        assertNull(choices.choiceFor(Lang.HI) { live[it] })
    }

    @Test
    fun `fixing a second time keeps the first run's choices`() {
        val choices = FixedChoices()
        val first = choices.fix { book }

        assertEquals(first, choices.fix { session })
        assertEquals(book, choices.choiceFor(Lang.EN) { session })
    }
}
