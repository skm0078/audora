// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/library/ShelfSuggestionsTest.kt
// Purpose: "Shelves you might like" under the collections: each says why it is offered, and Make shelf makes it.

package io.github.skm0078.audora.ui.library

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.ShelfSuggestion
import io.github.skm0078.audora.core.library.SuggestionKind
import io.github.skm0078.audora.ui.theme.AudoraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShelfSuggestionsTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val made = mutableListOf<ShelfSuggestion>()
    private val tagore = ShelfSuggestion(SuggestionKind.AUTHOR, "Rabindranath Tagore", listOf(BookId("a"), BookId("b")))

    @Test
    fun aSuggestionSaysWhyAndMakeShelfMakesIt() {
        rule.setContent {
            AudoraTheme {
                CollectionsSheet(
                    collections = emptyList(),
                    counts = emptyMap(),
                    looks = emptyMap(),
                    onCreate = { _, _ -> },
                    onEdit = { _, _, _, _ -> },
                    onDelete = {},
                    onDismiss = {},
                    suggestions = listOf(tagore),
                    onMake = { made += it },
                )
            }
        }
        val res = rule.activity.resources

        rule.onNodeWithText(rule.activity.getString(R.string.smart_shelf_title)).performScrollTo().assertExists()
        rule.onNodeWithText(res.getQuantityString(R.plurals.smart_shelf_author, 2, 2)).assertExists()
        val make = rule.activity.getString(R.string.smart_shelf_make_named, "Rabindranath Tagore")
        rule.onNode(hasContentDescription(make)).performScrollTo().performClick()

        assertEquals(listOf(tagore), made)
    }
}
