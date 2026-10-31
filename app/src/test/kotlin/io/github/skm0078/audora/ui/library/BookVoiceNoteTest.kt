// File: app/src/test/kotlin/io/github/skm0078/audora/ui/library/BookVoiceNoteTest.kt
// Purpose: The book menu names the voice and speed a book keeps for itself, and says nothing when it keeps neither.

package io.github.skm0078.audora.ui.library

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.notes.BookSettings
import io.github.skm0078.audora.speech.voices.VoiceKind
import io.github.skm0078.audora.speech.voices.VoiceRow
import io.github.skm0078.audora.speech.voices.VoiceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookVoiceNoteTest {
    private val book = BookId("b1")
    private val bruno = VoiceId(EngineId.SHERPA, "bruno")
    private val tara = VoiceId(EngineId.SHERPA, "tara")
    private val rows = listOf(row(bruno, "Bruno", Lang.EN), row(tara, "Tara", Lang.HI))

    private fun row(id: VoiceId, label: String, lang: Lang) =
        VoiceRow(id, label, lang, "licence", 0, VoiceKind.CATALOG, VoiceStatus.Installed, readsLanguage = false)

    @Test
    fun `the voice for the book's own language is named, with its speed`() {
        val settings = BookSettings(book, rate = 1.1f, voices = mapOf(Lang.EN to bruno, Lang.HI to tara))

        assertEquals(BookVoiceNote("Tara", 1.1f), BookVoiceNote.of(settings, Lang.HI, rows))
    }

    @Test
    fun `a book in another language names the first voice it keeps`() {
        val settings = BookSettings(book, voices = mapOf(Lang.EN to bruno))

        assertEquals(BookVoiceNote("Bruno", null), BookVoiceNote.of(settings, Lang.BN, rows))
    }

    @Test
    fun `a speed alone is said alone`() {
        assertEquals(BookVoiceNote(null, 0.9f), BookVoiceNote.of(BookSettings(book, rate = 0.9f), Lang.EN, rows))
    }

    @Test
    fun `nothing kept, or only a voice no longer on the phone, says nothing`() {
        assertNull(BookVoiceNote.of(null, Lang.EN, rows))
        assertNull(BookVoiceNote.of(BookSettings(book), Lang.EN, rows))
        val gone = BookSettings(book, voices = mapOf(Lang.EN to VoiceId(EngineId.SHERPA, "gone")))
        assertNull(BookVoiceNote.of(gone, Lang.EN, rows))
    }
}
