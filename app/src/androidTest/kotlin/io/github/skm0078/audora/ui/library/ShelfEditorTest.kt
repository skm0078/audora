// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/library/ShelfEditorTest.kt
// Purpose: A collection's colour and emoji ("Make it yours", touch 9): Edit opens a page in the same Collections
//          sheet, Save hands back the name and look, Back returns to the list, and TalkBack hears each colour's name.

package io.github.skm0078.audora.ui.library

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.BookCollection
import io.github.skm0078.audora.core.library.ShelfColour
import io.github.skm0078.audora.core.library.ShelfEmoji
import io.github.skm0078.audora.core.library.ShelfLook
import io.github.skm0078.audora.release.JourneyDevice
import io.github.skm0078.audora.ui.theme.AudoraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShelfEditorTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val edits = mutableListOf<Pair<String, ShelfLook>>()
    private val made = mutableListOf<Pair<String, ShelfLook>>()

    private fun text(id: Int, vararg args: Any): String = rule.activity.getString(id, *args)

    private fun show(looks: Map<String, ShelfLook> = emptyMap()) = rule.setContent {
        AudoraTheme {
            CollectionsSheet(
                collections = listOf(BookCollection("bed", "Bedtime", 1)),
                counts = mapOf("bed" to 2),
                looks = looks,
                onCreate = { name, look -> made += name to look },
                onEdit = { _, name, _, look -> edits += name to look },
                onDelete = {},
                onDismiss = {},
            )
        }
    }

    private fun tap(matcher: SemanticsMatcher) {
        rule.onNode(matcher).performClick()
        rule.waitForIdle()
    }

    private fun editPage() = hasText(text(R.string.shelf_edit_title))

    @Test
    fun aColourAndEmojiAreSavedWithTheName() {
        show()
        tap(hasContentDescription(text(R.string.shelf_edit_named, "Bedtime")))
        rule.onNode(editPage()).assertExists()

        tap(hasContentDescription(text(R.string.shelf_colour_teal)))
        tap(hasContentDescription(text(R.string.shelf_emoji_moon)))
        rule.onNode(hasSetTextAction()).performTextReplacement("  Night  reads ")
        tap(hasText(text(R.string.collections_save)))

        assertEquals(listOf("Night reads" to ShelfLook(ShelfColour.TEAL, ShelfEmoji.MOON)), edits)
        rule.onNode(editPage()).assertDoesNotExist()
        rule.onNode(hasText(text(R.string.collections_title))).assertExists()
    }

    @Test
    fun theChosenLookIsShownAndSpokenAsChosen() {
        show(mapOf("bed" to ShelfLook(ShelfColour.PLUM, ShelfEmoji.LAMP)))
        tap(hasContentDescription(text(R.string.shelf_edit_named, "Bedtime")))

        rule.onNode(hasContentDescription(text(R.string.shelf_colour_plum))).assertIsSelected()
        rule.onNode(hasContentDescription(text(R.string.shelf_emoji_lamp))).assertIsSelected()
        ShelfColour.entries.forEach { rule.onNode(hasContentDescription(text(colourName(it)))).assertExists() }
    }

    @Test
    fun aChosenColourTappedAgainGoesBackToTheShelfsOwn() {
        show(mapOf("bed" to ShelfLook(ShelfColour.CLAY)))
        tap(hasContentDescription(text(R.string.shelf_edit_named, "Bedtime")))
        tap(hasContentDescription(text(R.string.shelf_colour_clay)))
        tap(hasText(text(R.string.collections_save)))

        assertEquals(listOf("Bedtime" to ShelfLook.DEFAULT), edits)
    }

    @Test
    fun backLeavesTheEditPageButNotTheSheet() {
        show()
        tap(hasContentDescription(text(R.string.shelf_edit_named, "Bedtime")))
        JourneyDevice.pressBackKey()
        rule.waitForIdle()

        rule.onNode(editPage()).assertDoesNotExist()
        rule.onNode(hasText(text(R.string.collections_title))).assertExists()
        assertEquals(emptyList<Pair<String, ShelfLook>>(), edits)
    }

    @Test
    fun aNewCollectionCanHaveItsLookFromTheStart() {
        show()
        tap(hasText(text(R.string.collections_new)))
        rule.onNode(hasSetTextAction()).performTextReplacement("For Ma")
        tap(hasContentDescription(text(R.string.shelf_colour_saffron)))
        tap(hasContentDescription(text(R.string.shelf_emoji_heart)))
        tap(hasText(text(R.string.collections_save)))

        assertEquals(listOf("For Ma" to ShelfLook(ShelfColour.SAFFRON, ShelfEmoji.HEART)), made)
    }
}
