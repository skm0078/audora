// File: app/src/test/kotlin/io/github/skm0078/audora/ui/reader/ReaderHintsTest.kt
// Purpose: The reader's first-use hints come from what the reader already holds, and each hint's button goes where
//          it says (ADR 2026-10-30-first-use-hints).

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.hints.FirstUseHint
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.ContentIntent
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.model.VoiceShaping
import io.github.skm0078.audora.core.notes.Highlight
import io.github.skm0078.audora.core.resume.ResumeAnchor
import io.github.skm0078.audora.ui.library.LibraryAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderHintsTest {
    private val book = BookId("book-1")
    private val links = ReaderHintLinks(onLibraryAction = {})

    private fun state(
        speed: Float = 1f,
        shaping: VoiceShaping = VoiceShaping(),
        intent: ContentIntent? = null,
        readingPage: Int? = null,
        pageCount: Int = PAGES,
    ) = ReaderState(
        bookId = book,
        document = DocumentState.Open(pageCount),
        playback = PlaybackSnapshot(
            PlaybackSnapshot.Connection.CONNECTED,
            position = readingPage?.let { ReadingPosition(book, it, 0) },
            styleKnown = true,
            speed = speed,
            voiceShaping = shaping,
            readingIntent = intent,
        ),
    )

    private fun facts(state: ReaderState, notes: NotesState = NotesState(), pluggedIn: Boolean = false) =
        readerHintFacts(state, notes, links, pluggedIn)

    @Test
    fun `a plain book at the usual speed calls for nothing`() {
        assertEquals(emptyList<FirstUseHint>(), facts(state()).candidates())
    }

    @Test
    fun `fast speech without smart speed calls for it, and stops once it is on`() {
        assertTrue(facts(state(speed = 2f)).fastWithoutSmartSpeed)
        assertFalse(facts(state(speed = 2f, shaping = VoiceShaping(smartSpeed = true))).fastWithoutSmartSpeed)
        assertFalse(facts(state(speed = 1.25f)).fastWithoutSmartSpeed)
    }

    @Test
    fun `a story with no cast calls for voices, one with a dialogue voice does not`() {
        assertTrue(facts(state(intent = ContentIntent.STORY)).storyWithoutCast)
        val cast = VoiceShaping(dialogueVoices = mapOf(Lang.EN to VoiceId(EngineId.SHERPA, "bruno")))
        assertFalse(facts(state(intent = ContentIntent.STORY, shaping = cast)).storyWithoutCast)
        assertFalse(facts(state(intent = ContentIntent.POETRY)).storyWithoutCast)
    }

    @Test
    fun `a highlight and the last page call for the library's tools, only when they can be reached`() {
        val notes = NotesState(highlights = listOf(highlight()))
        assertTrue(facts(state(), notes).hasHighlight)
        assertTrue(facts(state(readingPage = PAGES - 1)).onLastPage)
        val noLibrary = readerHintFacts(state(readingPage = PAGES - 1), notes, ReaderHintLinks.None, pluggedIn = false)
        assertFalse(noLibrary.hasHighlight)
        assertFalse(noLibrary.onLastPage)
    }

    @Test
    fun `a long book half read calls for studio mode only on a charger`() {
        val halfway = state(readingPage = PAGES / 2)
        assertTrue(facts(halfway, pluggedIn = true).longHalfReadWhileCharging)
        assertFalse(facts(halfway, pluggedIn = false).longHalfReadWhileCharging)
        assertFalse(facts(state(readingPage = 5, pageCount = 10), pluggedIn = true).longHalfReadWhileCharging)
    }

    @Test
    fun `a cloud book is the one that can be kept`() {
        assertTrue(readerHintFacts(state(), NotesState(), ReaderHintLinks(onKeepOnPhone = {}), false).cloudBook)
        assertFalse(facts(state()).cloudBook)
    }

    @Test
    fun `each button goes where it says`() {
        val asked = mutableListOf<Any>()
        val routes = ReaderHintLinks(onLibraryAction = { asked += it }, onKeepOnPhone = { asked += "keep" })
        FirstUseHint.entries.forEach { hint ->
            actOnHint(hint, routes, onPrepareAhead = { asked += "studio" }) { spot -> asked += spot }
        }
        val expected = listOf(
            "keep",
            SpeechSpot.SOUND,
            SpeechSpot.CAST,
            LibraryAction.QUIZ,
            LibraryAction.LISTENING_STATS,
            "studio",
        )
        assertEquals(expected, asked)
    }

    private fun highlight() =
        Highlight(1, ResumeAnchor(book, 0, 0, 1L, 1L, 1, 0L), endPage = 0, endChar = 4, quote = "Once")

    private companion object {
        const val PAGES = 200
    }
}
