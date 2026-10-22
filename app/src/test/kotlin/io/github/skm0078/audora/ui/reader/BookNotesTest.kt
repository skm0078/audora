// File: app/src/test/kotlin/io/github/skm0078/audora/ui/reader/BookNotesTest.kt
// Purpose: Bookmarks with notes (item 29), pronunciation fixes (item 1), per-book settings and voices (item 4).

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.config.ReaderConfig
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.StorageError
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.notes.Bookmark
import io.github.skm0078.audora.core.notes.BookmarkStore
import io.github.skm0078.audora.core.notes.PronunciationRule
import io.github.skm0078.audora.core.notes.PronunciationStore
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.speech.voices.VoiceKind
import io.github.skm0078.audora.speech.voices.VoiceRow
import io.github.skm0078.audora.speech.voices.VoiceStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BookNotesTest {
    private class MemoryBookmarks(var failing: Boolean = false) : BookmarkStore {
        val rows = MutableStateFlow<List<Bookmark>>(emptyList())
        private var nextId = 1L

        override fun observe(bookId: BookId): Flow<List<Bookmark>> =
            rows.map { list -> list.filter { it.bookId == bookId }.sortedBy { it.anchor.page } }

        override suspend fun add(bookmark: Bookmark): Outcome<Long, StorageError> {
            if (failing) return Outcome.Failure(StorageError("disk full"))
            val id = nextId++
            rows.value = rows.value + bookmark.copy(id = id)
            return Outcome.Success(id)
        }

        override suspend fun updateNote(id: Long, note: String): Outcome<Unit, StorageError> {
            rows.value = rows.value.map { if (it.id == id) it.copy(note = note) else it }
            return Outcome.Success(Unit)
        }

        override suspend fun delete(id: Long): Outcome<Unit, StorageError> {
            rows.value = rows.value.filterNot { it.id == id }
            return Outcome.Success(Unit)
        }
    }

    private class MemoryRules : PronunciationStore {
        val rows = MutableStateFlow<List<PronunciationRule>>(emptyList())
        private var nextId = 1L

        override fun observe(bookId: BookId?): Flow<List<PronunciationRule>> =
            rows.map { list -> list.filter { it.bookId == null || it.bookId == bookId } }

        override suspend fun add(rule: PronunciationRule): Outcome<Long, StorageError> {
            val id = nextId++
            rows.value = rows.value + rule.copy(id = id)
            return Outcome.Success(id)
        }

        override suspend fun delete(id: Long): Outcome<Unit, StorageError> {
            rows.value = rows.value.filterNot { it.id == id }
            return Outcome.Success(Unit)
        }
    }

    private val bookmarks = MemoryBookmarks()
    private val rules = MemoryRules()
    private val port = FakePlaybackPort()

    private fun TestScope.notes() =
        BookNotesModel(BOOK, bookmarks, rules, backgroundScope, PIPELINE, clock = { NOW }).also { it.start() }

    private fun TestScope.reader() = ReaderModel(
        BOOK,
        FakeBooks(),
        FakeOpener(),
        port,
        FakeSpeeds(),
        ReaderConfig(),
        backgroundScope,
        pipelineVersion = PIPELINE,
    )
        .also { it.start() }

    @Test
    fun `a bookmarked sentence opens its note, and the note is saved`() = runTest(UnconfinedTestDispatcher()) {
        val notes = notes()
        val unit = unitsOf(page = 3)[1]
        notes.openMenu(unit)
        notes.bookmark(unit)
        val saved = notes.state.value.bookmarks.single()
        assertNull("the menu closes", notes.state.value.menuFor)
        assertEquals(3, saved.anchor.page)
        assertEquals(unit.charStart, saved.anchor.charStart)
        assertEquals(PIPELINE, saved.anchor.pipelineVersion)
        assertEquals(saved.id, notes.state.value.editing?.id)

        notes.saveNote("  Remember this  ")
        assertEquals("Remember this", notes.state.value.bookmarks.single().note)
        assertNull(notes.state.value.editing)

        notes.delete(notes.state.value.bookmarks.single())
        assertTrue(notes.state.value.bookmarks.isEmpty())
    }

    @Test
    fun `a failed save is said once`() = runTest(UnconfinedTestDispatcher()) {
        bookmarks.failing = true
        val notes = notes()
        notes.bookmark(unitsOf(0)[0])
        assertTrue(notes.state.value.failed)
        notes.clearFailure()
        assertEquals(false, notes.state.value.failed)
    }

    @Test
    fun `a fix takes the sentence's language and this book or every book`() = runTest(UnconfinedTestDispatcher()) {
        val notes = notes()
        notes.fix(unitsOf(0)[0].copy(lang = Lang.HI))
        notes.addRule("गुरु", "गुरू", wholeWord = true, thisBookOnly = true)
        notes.addRule("US", "you ess", wholeWord = true, thisBookOnly = false)
        notes.addRule(" ", "nothing", wholeWord = true, thisBookOnly = true)
        val added = notes.state.value.rules
        assertEquals(listOf(Lang.HI, Lang.HI), added.map { it.lang })
        assertEquals(listOf(BOOK, null), added.map { it.bookId })

        notes.deleteRule(added.first())
        assertEquals(listOf("US"), notes.state.value.rules.map { it.written })
    }

    @Test
    fun `a bookmark is found again and read from while reading`() = runTest(UnconfinedTestDispatcher()) {
        val notes = notes()
        val reader = reader()
        port.connect()
        val unit = unitsOf(page = 5)[2]
        notes.bookmark(unit)
        val bookmark = notes.state.value.bookmarks.single()

        reader.goToBookmark(bookmark)
        assertEquals(5, reader.state.value.page)
        assertTrue("idle: shown, not read", port.starts.isEmpty())

        port.readingAt(ReadingPosition(BOOK, 1, 0))
        reader.goToBookmark(bookmark)
        assertEquals(listOf(ReadingPosition(BOOK, 5, unit.charStart) to null), port.starts)
    }

    @Test
    fun `bookmark here marks the sentence being read on the shown page`() = runTest(UnconfinedTestDispatcher()) {
        val reader = reader()
        port.connect()
        assertEquals(unitsOf(0)[0], reader.sentenceHere())
        port.readingAt(ReadingPosition(BOOK, 0, 40))
        assertEquals(unitsOf(0)[2], reader.sentenceHere())
    }

    @Test
    fun `own settings keep the speed off the global preference`() = runTest(UnconfinedTestDispatcher()) {
        val speeds = FakeSpeeds()
        val reader = ReaderModel(BOOK, FakeBooks(), FakeOpener(), port, speeds, ReaderConfig(), backgroundScope)
            .also { it.start() }
        port.state.value = PlaybackSnapshot(PlaybackSnapshot.Connection.CONNECTED, ownSettings = true)
        assertTrue("no saved speed pushed onto the book", port.calls.none { it.startsWith("speed") })
        reader.setSpeed(1.5f)
        assertNull(speeds.saved)
        reader.setOwnSettings(false)
        reader.setBookVoice(Lang.HI, HINDI)
        assertEquals(listOf("speed 1.5", "own settings false", "book voice HI $HINDI"), port.calls)
    }

    @Test
    fun `book voice choices are the installed ones, by language`() {
        fun row(
            name: String,
            lang: Lang,
            status: VoiceStatus,
            kind: VoiceKind = VoiceKind.CATALOG,
            slow: Boolean = false,
        ) = VoiceRow(VoiceId(EngineId.SHERPA, name), name, lang, "MIT", 0, kind, status, false, tooSlow = slow)
        val rows = listOf(
            row("kitten", Lang.EN, VoiceStatus.Installed, VoiceKind.BUILT_IN),
            row("cori", Lang.EN, VoiceStatus.Installed),
            row("amy", Lang.EN, VoiceStatus.NotDownloaded),
            row("pratham", Lang.HI, VoiceStatus.UpdateAvailable),
            row("slow", Lang.HI, VoiceStatus.Installed, slow = true),
        )
        val choices = bookVoiceChoices(rows)
        assertEquals(listOf("kitten", "cori"), choices.getValue(Lang.EN).map { it.label })
        assertEquals(listOf("pratham"), choices.getValue(Lang.HI).map { it.label })
    }

    private companion object {
        const val PIPELINE = 10
        const val NOW = 1_700_000_000_000L
        val HINDI = VoiceId(EngineId.SHERPA, "hi-pratham")
    }
}
