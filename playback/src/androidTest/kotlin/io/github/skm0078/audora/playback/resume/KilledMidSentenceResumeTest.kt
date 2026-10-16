// File: playback/src/androidTest/kotlin/io/github/skm0078/audora/playback/resume/KilledMidSentenceResumeTest.kt
// Purpose: P1-06 done-when on a real database: a kill mid-sentence (no flush) resumes at that sentence, also
//   after PIPELINE_VERSION changes.

package io.github.skm0078.audora.playback.resume

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skm0078.audora.core.config.ResumeConfig
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.resume.AnchorMatch
import io.github.skm0078.audora.core.resume.ResumeLocator
import io.github.skm0078.audora.core.resume.ResumePoint
import io.github.skm0078.audora.core.resume.ResumeRecorder
import io.github.skm0078.audora.pdf.PIPELINE_VERSION
import io.github.skm0078.audora.playback.storage.AudoraStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KilledMidSentenceResumeTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val book = Book(BookId("book-k"), "content://test/k", "K", 1L, "v1:k", 50, 1L, null)
    private val config = ResumeConfig(minWriteIntervalMs = INTERVAL_MS)

    @Before
    fun setUp() {
        context.deleteDatabase(NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(NAME)
    }

    @Test
    fun resumesAtTheSentenceBeingReadWhenKilled() = runBlocking {
        val page = units(page = 23, startOffset = 0)
        readThenDie(page, readUpTo = 2)

        val point = AudoraStorage.open(context, NAME).use { storage ->
            ResumeLocator(storage.resumeAnchors, PIPELINE_VERSION).locate(book.id, pageCount = 50) { page }
        }

        assertEquals(ResumePoint(23, page[2].charStart, AnchorMatch.EXACT), point)
    }

    @Test
    fun resumesAtTheSameSentenceAfterAPipelineChange() = runBlocking {
        readThenDie(units(page = 23, startOffset = 0), readUpTo = 3)
        // The next pipeline drops a 25-character header from the page: every offset moves.
        val newPage = units(page = 23, startOffset = 25)

        val point = AudoraStorage.open(context, NAME).use { storage ->
            ResumeLocator(storage.resumeAnchors, PIPELINE_VERSION + 1).locate(book.id, pageCount = 50) { newPage }
        }

        assertEquals(ResumePoint(23, newPage[3].charStart, AnchorMatch.HEAD_FINGERPRINT), point)
    }

    /** Plays [page] up to unit [readUpTo], lets the throttled write land, then dies without a flush. */
    private suspend fun readThenDie(page: List<ReadingUnit>, readUpTo: Int) {
        val storage = AudoraStorage.open(context, NAME)
        storage.books.insert(book)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val recorder = ResumeRecorder(storage.resumeAnchors, scope, PIPELINE_VERSION, System::currentTimeMillis, config)
        for (unit in page.take(readUpTo + 1)) recorder.onUnitStarted(book.id, unit)
        delay(INTERVAL_MS * 3)
        // Process death: the scope and the database go away with no flush.
        scope.cancel()
        storage.close()
    }

    private fun units(page: Int, startOffset: Int): List<ReadingUnit> {
        var offset = startOffset
        return SENTENCES.mapIndexed { index, text ->
            ReadingUnit(page, index, offset, offset + text.length, text, Lang.EN, 0, null)
                .also { offset += text.length + 1 }
        }
    }

    private companion object {
        const val NAME = "resume-test.db"
        const val INTERVAL_MS = 200L
        val SENTENCES = listOf(
            "Call me Ishmael.",
            "Some years ago, never mind how long precisely, I thought I would sail about a little.",
            "It is a way I have of driving off the spleen and regulating the circulation.",
            "Whenever I find myself growing grim about the mouth, I account it high time to get to sea.",
            "This is my substitute for pistol and ball.",
        )
    }
}
