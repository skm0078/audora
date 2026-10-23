// File: app/src/test/kotlin/io/github/skm0078/audora/ui/quiz/QuizModelTest.kt
// Purpose: The quiz model tells no cards from nothing due, practises the soonest cards and saves only known ones.

package io.github.skm0078.audora.ui.quiz

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.StorageError
import io.github.skm0078.audora.core.notes.Highlight
import io.github.skm0078.audora.core.notes.HighlightStore
import io.github.skm0078.audora.core.quiz.CardProgress
import io.github.skm0078.audora.core.quiz.QuizProgressStore
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.resume.ResumeAnchor
import java.nio.file.Files
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class QuizModelTest {
    private val folder = Files.createTempDirectory("quiz-model").toFile()
    private val store = QuizProgressStore(folder.resolve(QuizProgressStore.FILE_NAME))
    private val spoken = mutableListOf<String>()

    @After
    fun cleanUp() {
        folder.deleteRecursively()
    }

    @Test
    fun `no highlights means no cards, all scheduled later means nothing due`() = runTest {
        assertEquals(QuizState.NoCards, opened(emptyList()).state.value)

        store.save(mapOf(1L to CardProgress(1, NOW + 1_000)), known = setOf(1L))
        assertEquals(QuizState.NothingDue, opened(listOf(highlight(1, "Photosynthesis needs sunlight."))).state.value)
    }

    @Test
    fun `practise asks the soonest first, and a right answer there is saved without promoting the card`() = runTest {
        val later = CardProgress(2, NOW + 9_000)
        val sooner = CardProgress(1, NOW + 2_000)
        store.save(mapOf(1L to later, 2L to sooner, 99L to CardProgress(0, 0)), known = setOf(1L, 2L, 99L))
        val model =
            opened(listOf(highlight(1, "Photosynthesis needs sunlight."), highlight(2, "Chlorophyll is green.")))

        model.practise()
        advanceUntilIdle()
        val round = (model.state.value as QuizState.Asking).round
        assertEquals(listOf(2L, 1L), round.cards.map { it.highlightId })

        // Two highlights are too few for choices, so the card is revealed and graded.
        model.reveal()
        model.grade(knewIt = true)
        advanceUntilIdle()
        assertEquals("the deleted highlight's card is dropped", mapOf(1L to later, 2L to sooner), store.load())
    }

    private fun TestScope.opened(highlights: List<Highlight>): QuizModel {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val model = QuizModel(
            FakeHighlights(highlights),
            store,
            this,
            speak = { text, _ -> spoken.add(text) },
            now = { NOW },
            work = dispatcher,
            io = dispatcher,
        )
        model.start()
        advanceUntilIdle()
        return model
    }

    private fun highlight(id: Long, quote: String) = Highlight(
        id = id,
        anchor = ResumeAnchor(BookId("book"), 0, 0, fingerprint = 0, tailFingerprint = 0, 1, savedAtMillis = 0),
        endPage = 0,
        endChar = quote.length,
        quote = quote,
    )

    private class FakeHighlights(private val all: List<Highlight>) : HighlightStore {
        override fun observe(bookId: BookId): Flow<List<Highlight>> = flowOf(all)

        override fun observeAll(): Flow<List<Highlight>> = flowOf(all)

        override suspend fun add(highlight: Highlight): Outcome<Long, StorageError> = Outcome.Success(highlight.id)

        override suspend fun updateNote(id: Long, note: String): Outcome<Unit, StorageError> = Outcome.Success(Unit)

        override suspend fun delete(id: Long): Outcome<Unit, StorageError> = Outcome.Success(Unit)
    }

    private companion object {
        const val NOW = 1_000_000L
    }
}
