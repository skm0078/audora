// File: app/src/test/kotlin/io/github/skm0078/audora/ui/guide/CharacterGuideModelTest.kt
// Purpose: The guide model reads the listener's place from the resume point and hides entries met after it.

package io.github.skm0078.audora.ui.guide

import io.github.skm0078.audora.core.guide.CharacterGuideStore
import io.github.skm0078.audora.core.guide.GuideEntry
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.StorageError
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.resume.ResumeAnchor
import io.github.skm0078.audora.core.resume.ResumeAnchorStore
import java.nio.file.Files
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterGuideModelTest {
    private val folder = Files.createTempDirectory("guide-model").toFile()
    private val store = CharacterGuideStore(folder)
    private val book = BookId("book-1")

    @After
    fun cleanUp() {
        folder.deleteRecursively()
    }

    @Test
    fun `the place comes from the resume point, the start without one`() = runTest {
        assertEquals(4 to 120, opened(anchor(page = 4, charStart = 120)).state.value!!.let { it.page to it.charStart })
        assertEquals(0 to 0, opened(null).state.value!!.let { it.page to it.charStart })
    }

    @Test
    fun `a new entry is met at the listener's place, one typed for a later page joins the hidden count`() = runTest {
        val model = opened(anchor(page = 4, charStart = 120))

        assertTrue(model.save(GuideEntry(0, "Here", "", page = 4)))
        assertTrue(model.save(GuideEntry(0, "Ahead", "", page = 9)))
        advanceUntilIdle()

        val state = model.state.value!!
        assertEquals(listOf("Here"), state.view.shown.map { it.name })
        assertEquals(1, state.view.later)
        assertEquals(120, store.load(book.value).single { it.name == "Here" }.charStart)
    }

    @Test
    fun `rapid saves end with the latest content, and a delete can be undone`() = runTest {
        val model = opened(null)
        repeat(5) { model.save(GuideEntry(0, "Name $it", "", 0)) }
        model.delete(1)
        model.undoDelete()
        advanceUntilIdle()

        assertEquals((0 until 5).map { "Name $it" }.toSet(), store.load(book.value).map { it.name }.toSet())
    }

    private fun TestScope.opened(anchor: ResumeAnchor?): CharacterGuideModel {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val model = CharacterGuideModel(book, store, FakeAnchors(anchor), this, io = dispatcher).apply { start() }
        advanceUntilIdle()
        return model
    }

    private fun anchor(page: Int, charStart: Int) =
        ResumeAnchor(book, page, charStart, fingerprint = 0, tailFingerprint = 0, 1, savedAtMillis = 0)

    private class FakeAnchors(private val anchor: ResumeAnchor?) : ResumeAnchorStore {
        override suspend fun get(bookId: BookId): ResumeAnchor? = anchor

        override fun observeAll(): Flow<List<ResumeAnchor>> = flowOf(listOfNotNull(anchor))

        override suspend fun latest(): ResumeAnchor? = anchor

        override suspend fun put(anchor: ResumeAnchor): Outcome<Unit, StorageError> = Outcome.Success(Unit)
    }
}
