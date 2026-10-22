// File: app/src/test/kotlin/io/github/skm0078/audora/ui/library/DuplicateNoticeTest.kt
// Purpose: Only a picked duplicate opens the dialog (plan item 27); every other message keeps the snackbar.

package io.github.skm0078.audora.ui.library

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.LibraryError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DuplicateNoticeTest {
    private val id = BookId("b1")

    @Test
    fun pickedDuplicatesOpenTheDialog() {
        assertEquals(
            DuplicateNotice(id, "Gita", moved = false),
            duplicateOf(LibraryMessage.AlreadyInLibrary("Gita", id)),
        )
        assertEquals(DuplicateNotice(id, "Gita", moved = true), duplicateOf(LibraryMessage.FoundAgain("Gita", id)))
    }

    @Test
    fun sharedDuplicatesAndOtherMessagesKeepTheSnackbar() {
        val snackbarOnly = listOf(
            LibraryMessage.AlreadyInLibrary("Gita"),
            LibraryMessage.FoundAgain("Gita"),
            LibraryMessage.Added("Gita"),
            LibraryMessage.Removed("Gita"),
            LibraryMessage.Failed(LibraryError.NotAPdf),
        )
        snackbarOnly.forEach { assertNull(it.toString(), duplicateOf(it)) }
    }
}
