// File: app/src/test/kotlin/io/github/skm0078/audora/ui/reader/ReaderPageTextTest.kt
// Purpose: The page text a "Whole page" card gets (ADR 2026-10-30-whole-page-cards): the shown page's sentences in
//          order, and nothing for another page or one still loading.

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderPageTextTest {
    private val units = listOf(
        ReadingUnit(2, 0, 0, 4, "One.", Lang.EN, 0, null),
        ReadingUnit(2, 1, 5, 5, " ", Lang.EN, 0, null),
        ReadingUnit(2, 2, 6, 10, "Two.", Lang.EN, 0, null),
    )
    private val shown = ReaderState(BookId("book"), page = 2, content = PageContent.Ready(units))

    @Test
    fun theShownPagesSentencesAreJoinedInOrderWithoutBlankOnes() {
        assertEquals("One. Two.", shown.pageText(2))
    }

    @Test
    fun anotherPageOrOneStillLoadingOrEmptyHasNoText() {
        assertNull(shown.pageText(3))
        assertNull(shown.copy(content = PageContent.Loading).pageText(2))
        assertNull(shown.copy(content = PageContent.Ready(emptyList())).pageText(2))
    }
}
