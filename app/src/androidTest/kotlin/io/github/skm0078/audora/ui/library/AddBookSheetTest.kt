// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/library/AddBookSheetTest.kt
// Purpose: The Add a book sheet (ADR 2026-10-29-add-a-book) names every way in with its line, and a row closes the
//          sheet before it runs, so a picker or another sheet never opens over it.

package io.github.skm0078.audora.ui.library

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skm0078.audora.R
import io.github.skm0078.audora.ui.theme.AudoraTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddBookSheetTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun text(id: Int): String = context.getString(id)

    private var open by mutableStateOf(true)
    private val calls = mutableListOf<String>()

    @Before
    fun show() {
        rule.setContent {
            AudoraTheme {
                if (open) {
                    AddBookSheet(
                        onFile = { calls += "file:open=$open" },
                        onTool = { calls += "${it.name}:open=$open" },
                        onDismiss = { open = false },
                    )
                }
            }
        }
    }

    @Test
    fun everyWayInShowsItsLine() {
        rule.onNodeWithTag(ADD_BOOK_SHEET_TAG).assertExists()
        AddBookSource.all.forEach { source ->
            rule.onNodeWithText(text(source.label)).performScrollTo().assertExists()
            rule.onNodeWithText(text(source.line)).assertExists()
        }
    }

    @Test
    fun chooseAFileClosesTheSheetFirst() {
        rule.onNodeWithText(text(R.string.add_book_file)).performClick()
        rule.waitForIdle()
        assertEquals(listOf("file:open=false"), calls)
        rule.onNodeWithTag(ADD_BOOK_SHEET_TAG).assertDoesNotExist()
    }

    @Test
    fun aToolClosesTheSheetFirst() {
        rule.onNodeWithText(text(R.string.library_book_catalogues)).performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(listOf("BOOK_CATALOGUES:open=false"), calls)
    }
}
