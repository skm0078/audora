// File: app/src/test/kotlin/io/github/skm0078/audora/ui/library/AddBookFoldingTest.kt
// Purpose: Add book folds while the books scroll down, opens on the way back up, ignores a resting finger, and never
//          takes any of the scroll for itself.

package io.github.skm0078.audora.ui.library

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddBookFoldingTest {
    private val folding = AddBookFolding()

    private fun scroll(dy: Float): Offset = folding.onPreScroll(Offset(0f, dy), NestedScrollSource.UserInput)

    @Test
    fun startsOpen() = assertTrue(folding.expanded)

    @Test
    fun foldsReadingDownAndOpensGoingBackUp() {
        scroll(-READ_DOWN)
        assertFalse(folding.expanded)
        scroll(READ_DOWN)
        assertTrue(folding.expanded)
    }

    @Test
    fun aRestingFingerChangesNothing() {
        scroll(-READ_DOWN)
        scroll(JITTER)
        assertFalse(folding.expanded)
    }

    @Test
    fun theListKeepsAllOfItsScroll() = assertEquals(Offset.Zero, scroll(-READ_DOWN))

    private companion object {
        const val READ_DOWN = 40f
        const val JITTER = 0.5f
    }
}
