// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/notes/BookContextTest.kt
// Purpose: A book opens with its own fixes already in place and its saved pitch inside today's range.

package io.github.skm0078.audora.playback.notes

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.StorageError
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.SpeechStyle
import io.github.skm0078.audora.core.notes.BookSettings
import io.github.skm0078.audora.core.notes.BookSettingsStore
import io.github.skm0078.audora.core.notes.PronunciationRule
import io.github.skm0078.audora.core.notes.PronunciationStore
import io.github.skm0078.audora.core.result.Outcome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BookContextTest {
    private val book = BookId("book")

    /** Emits nothing until [rules] is set, like a Room query still running on its own thread. */
    private class SlowPronunciations : PronunciationStore {
        val rules = MutableStateFlow<List<PronunciationRule>?>(null)

        override fun observe(bookId: BookId?): Flow<List<PronunciationRule>> = rules.filterNotNull()

        override suspend fun add(rule: PronunciationRule): Outcome<Long, StorageError> = Outcome.Success(rule.id)

        override suspend fun delete(id: Long): Outcome<Unit, StorageError> = Outcome.Success(Unit)
    }

    private class Settings(private val saved: BookSettings?) : BookSettingsStore {
        override suspend fun get(bookId: BookId): BookSettings? = saved

        override suspend fun put(settings: BookSettings): Outcome<Unit, StorageError> = Outcome.Success(Unit)

        override suspend fun clear(bookId: BookId): Outcome<Unit, StorageError> = Outcome.Success(Unit)
    }

    private val rule = PronunciationRule(1, book, Lang.EN, "Dr.", "Doctor")

    @Test
    fun openReturnsOnlyOnceTheBooksFixesAreInPlace() = runTest {
        val pronunciations = SlowPronunciations()
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        var changes = 0
        val context = BookContext(Settings(null), pronunciations, scope, onRulesChanged = { changes++ })
        var opened = false
        launch {
            context.open(book)
            opened = true
        }
        advanceUntilIdle()
        scope.advanceUntilIdle()
        assertFalse("open must wait for the fixes", opened)

        pronunciations.rules.value = listOf(rule)
        scope.advanceUntilIdle()
        advanceUntilIdle()

        assertTrue(opened)
        assertFalse(context.fixer.isEmpty)
        assertEquals("the first list is the book's own, not an edit", 0, changes)

        pronunciations.rules.value = emptyList()
        scope.advanceUntilIdle()
        assertEquals(1, changes)
        assertTrue(context.fixer.isEmpty)
        scope.cancel()
    }

    @Test
    fun aPitchSavedUnderTheOldWiderRangeIsHeardInsideTodaysRange() = runTest {
        val pronunciations = SlowPronunciations().apply { rules.value = emptyList() }
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val saved = BookSettings(book, pitch = 1.25f)
        val context = BookContext(Settings(saved), pronunciations, scope, clampPitch = { it.coerceIn(0.85f, 1.15f) })
        launch { context.open(book) }
        scope.advanceUntilIdle()
        advanceUntilIdle()

        assertEquals(1.15f, context.style(SpeechStyle()).pitch)
        scope.cancel()
    }
}
