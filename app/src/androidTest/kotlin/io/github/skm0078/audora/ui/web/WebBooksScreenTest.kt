// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/web/WebBooksScreenTest.kt
// Purpose: The web dialogs (ADR 2026-10-26-web-books) without the network: Read is offered only for a web address, a
//          shared link asks first and names its site, and the catalogue list shows Project Gutenberg and Add.

package io.github.skm0078.audora.ui.web

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skm0078.audora.R
import io.github.skm0078.audora.ui.library.LibraryAction
import io.github.skm0078.audora.ui.theme.AudoraTheme
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebBooksScreenTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun text(id: Int, vararg args: Any): String = context.getString(id, *args)

    @Before
    fun show() {
        rule.setContent { AudoraTheme { WebBooksRoute(onBook = {}) } }
    }

    @After
    fun close() {
        rule.runOnUiThread {
            WebBooksUi.sheet = null
            WebBooksUi.sharedLink = null
        }
    }

    @Test
    fun theLibraryMenuOffersBothWebEntries() {
        val inMenu = LibraryAction.entries.filter { it.inMenu }
        assertTrue(LibraryAction.READ_WEB_PAGE in inMenu)
        assertTrue(LibraryAction.BOOK_CATALOGUES in inMenu)
    }

    @Test
    fun readIsOfferedOnlyForAWebAddress() {
        rule.runOnUiThread { WebBooksUi.sheet = WebSheet.ADDRESS }
        val read = rule.onNodeWithText(text(R.string.web_read))
        read.assertIsNotEnabled()

        val field = rule.onNode(hasSetTextAction())
        field.performTextReplacement("hello")
        rule.onNodeWithText(text(R.string.web_error_address)).assertExists()
        read.assertIsNotEnabled()

        field.performTextReplacement("example.org/story")
        read.assertIsEnabled()
        rule.onNodeWithText(text(R.string.web_cancel)).performClick()
        rule.waitForIdle()
        assertNull(WebBooksUi.sheet)
    }

    @Test
    fun aSharedLinkAsksFirstAndNamesItsSite() {
        rule.runOnUiThread { WebBooksUi.sharedLink = "https://www.example.org/news/1?token=secret" }

        rule.onNodeWithText(text(R.string.web_shared_link_title)).assertExists()
        rule.onNode(hasText(text(R.string.web_shared_link_body, "example.org"))).assertExists()
        rule.onNode(hasText("token=secret", substring = true)).assertDoesNotExist()

        rule.onNodeWithText(text(R.string.web_cancel)).performClick()
        rule.waitForIdle()
        assertNull(WebBooksUi.sharedLink)
        rule.onNodeWithText(text(R.string.web_shared_link_title)).assertDoesNotExist()
    }

    @Test
    fun theCatalogueListShowsGutenbergAndAdd() {
        rule.runOnUiThread { WebBooksUi.sheet = WebSheet.CATALOGUES }

        rule.waitUntil(UI_TIMEOUT_MS) {
            rule.onAllNodes(hasText("Project Gutenberg")).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(text(R.string.catalogues_explain)).assertExists()
        rule.onNodeWithText(text(R.string.catalogues_add)).assertIsNotEnabled()
        // Gutenberg is built in: it has no Remove.
        rule.onNodeWithText(text(R.string.catalogues_remove)).assertDoesNotExist()
    }

    private companion object {
        const val UI_TIMEOUT_MS = 5_000L
    }
}
