// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/storage/StorageScreenTest.kt
// Purpose: Settings → Storage on a device: sizes and failures show, Delete asks first, Clear does not.

package io.github.skm0078.audora.ui.storage

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.skm0078.audora.R
import io.github.skm0078.audora.ui.theme.AudoraTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StorageScreenTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val actions = mutableListOf<StorageKind>()
    private val rows = listOf(
        StorageRow(StorageKind.VOICES, bytes = null, StorageAction.MANAGE_VOICES, failed = true),
        StorageRow(StorageKind.OCR, bytes = 12_000_000L, StorageAction.DELETE),
        StorageRow(StorageKind.PUNCTUATION, bytes = null, StorageAction.DELETE),
        StorageRow(StorageKind.PAGE_PICTURES, bytes = 5_000_000L, StorageAction.CLEAR),
    )

    private fun text(id: Int, vararg args: Any) = rule.activity.getString(id, *args)

    @Before
    fun show() = rule.setContent {
        AudoraTheme { StorageScreen(rows = rows, onAction = { actions += it.kind }, onBack = {}) }
    }

    @Test
    fun aFailedOwnerSaysSoAndAMeasuringOneWaits() {
        rule.onNodeWithText(text(R.string.storage_measure_failed)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.storage_measuring)).assertIsDisplayed()
    }

    @Test
    fun deleteAsksFirst() {
        val ocr = text(R.string.storage_ocr)
        val delete = text(R.string.storage_delete)
        rule.onNodeWithContentDescription(text(R.string.storage_action_on, delete, ocr)).performClick()
        rule.onNodeWithText(text(R.string.storage_delete_title, ocr)).assertIsDisplayed()
        assertTrue(actions.isEmpty())
        rule.onNode(hasText(delete) and hasAnyAncestor(isDialog())).performClick()
        rule.runOnIdle { assertEquals(listOf(StorageKind.OCR), actions) }
    }

    @Test
    fun clearingACacheDoesNotAsk() {
        val pictures = text(R.string.storage_page_pictures)
        rule.onNodeWithContentDescription(text(R.string.storage_action_on, text(R.string.storage_clear), pictures))
            .performClick()
        rule.runOnIdle { assertEquals(listOf(StorageKind.PAGE_PICTURES), actions) }
    }
}
