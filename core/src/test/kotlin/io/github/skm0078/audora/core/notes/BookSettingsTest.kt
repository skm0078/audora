// File: core/src/test/kotlin/io/github/skm0078/audora/core/notes/BookSettingsTest.kt
// Purpose: A book's own speed and pace override the global style, and its voices round-trip (plan item 4).

package io.github.skm0078.audora.core.notes

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.Pace
import io.github.skm0078.audora.core.model.SpeechStyle
import io.github.skm0078.audora.core.model.VoiceId
import org.junit.Assert.assertEquals
import org.junit.Test

class BookSettingsTest {
    private val book = BookId("book")

    @Test
    fun `the book's rate and pace win, the rest stays global`() {
        val global = SpeechStyle(rate = 1.2f, pace = Pace.NORMAL, clausePauses = false)
        assertEquals(
            SpeechStyle(rate = 0.9f, pace = Pace.NORMAL, clausePauses = false),
            BookSettings(book, rate = 0.9f).over(global),
        )
        assertEquals(global.copy(pace = Pace.RELAXED), BookSettings(book, pace = Pace.RELAXED).over(global))
    }

    @Test
    fun `voices round-trip and bad entries are dropped`() {
        val voices = mapOf(
            Lang.HI to VoiceId(EngineId.SHERPA, "hi-priyamvada"),
            Lang.EN to VoiceId(EngineId.SHERPA, "en/x"),
        )
        assertEquals(voices, BookSettings.decodeVoices(BookSettings.encodeVoices(voices)))
        assertEquals(
            mapOf(Lang.BN to VoiceId(EngineId.SHERPA, "bn")),
            BookSettings.decodeVoices("XX=SHERPA/a;EN=NOPE/b;HI=SHERPA/;BN=SHERPA/bn;junk"),
        )
        assertEquals(emptyMap<Lang, VoiceId>(), BookSettings.decodeVoices(null))
    }
}
