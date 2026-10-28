// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/made/MadeAndEditScreensTest.kt
// Purpose: The Made list, the in-app player and Edit details (ADR-068) on a phone-sized screen: what each shows, that
//          an edit shows on the library at once, and a screenshot of each for review in CI's "player-screens" artifact.

package io.github.skm0078.audora.ui.made

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookEdit
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.BookOutput
import io.github.skm0078.audora.core.library.DocumentFormat
import io.github.skm0078.audora.core.library.OutputKind
import io.github.skm0078.audora.core.library.OutputSet
import io.github.skm0078.audora.ui.library.BookEditSheet
import io.github.skm0078.audora.ui.library.BookRow
import io.github.skm0078.audora.ui.library.LibraryItem
import io.github.skm0078.audora.ui.theme.AudoraTheme
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MadeAndEditScreensTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theMadeListNamesEachSetAndItsBookAndPlaysTheOneTapped() {
        val played = mutableListOf<String>()
        var jobsOpened = 0
        val sets = listOf(MadeSet(AUDIOBOOK, AUDIOBOOK.files), MadeSet(COPY, emptyList()))
        rule.setContent {
            Phone {
                MadeScreen(
                    sets,
                    mapOf(BOOK to "Gitanjali"),
                    { played += it },
                    runningJobs = 2,
                    onOpenJobs = { jobsOpened++ },
                    Modifier.testTag(SCREEN),
                )
            }
        }
        rule.onNodeWithText(context.getString(R.string.made_kind_audiobook)).assertExists()
        rule.onNodeWithText(context.getString(R.string.made_missing)).assertExists()
        rule.onNodeWithText(context.getString(R.string.made_forget)).assertExists()
        save("made-list", rule.onNodeWithTag(SCREEN).captureToImage().asAndroidBitmap())

        rule.onNodeWithText(context.getString(R.string.made_play)).performClick()
        rule.waitForIdle()
        assertEquals(listOf(AUDIOBOOK.key), played)

        // The running jobs are the first row of the place, with how many work now.
        val running = context.resources.getQuantityString(R.plurals.made_jobs_running, 2, 2)
        rule.onNodeWithText(running).assertExists()
        rule.onNodeWithText(context.getString(R.string.made_jobs)).performClick()
        rule.waitForIdle()
        assertEquals(1, jobsOpened)
    }

    @Test
    fun theMadeListSaysWhatShowsThereWhenNothingWasMade() {
        rule.setContent { Phone { MadeScreen(emptyList(), emptyMap(), {}, runningJobs = 0, onOpenJobs = {}) } }
        rule.onNodeWithText(context.getString(R.string.made_empty)).assertExists()
        rule.onNodeWithText(context.getString(R.string.made_jobs_idle)).assertExists()
    }

    @Test
    fun thePlayerShowsTheChapterWhereItIsAndItsSpeed() {
        val state = MadePlayerState(
            connected = true,
            playing = true,
            index = 0,
            count = 2,
            positionMs = 65_000,
            durationMs = 1_800_000,
            speed = 1.25f,
        )
        rule.setContent {
            Phone {
                MadePlayerScreen(AUDIOBOOK, "Gitanjali", state, controls = null, onBack = {}, Modifier.testTag(SCREEN))
            }
        }
        rule.onNodeWithText(context.getString(R.string.made_player_chapter, 1, 2)).assertExists()
        rule.onNodeWithText(context.getString(R.string.made_player_position, "1:05", "30:00")).assertExists()
        rule.onNodeWithText(context.getString(R.string.made_player_speed, "1.25")).assertExists()
        rule.onNodeWithContentDescription(context.getString(R.string.made_player_pause)).assertExists()
        rule.onNodeWithContentDescription(context.getString(R.string.made_player_previous)).assertIsNotEnabled()
        save("made-player", rule.onNodeWithTag(SCREEN).captureToImage().asAndroidBitmap())
    }

    @Test
    fun aPlayerWhoseAudioIsGoneSaysSo() {
        rule.setContent { Phone { MadePlayerScreen(null, "", MadePlayerState(), controls = null, onBack = {}) } }
        rule.onNodeWithText(context.getString(R.string.made_player_gone)).assertExists()
    }

    @Test
    fun editDetailsSavesOnlyWhatChangedAndTheLibraryShowsItAtOnce() {
        var saved: BookEdit? = null
        var book by mutableStateOf(PDF)
        rule.setContent {
            Phone {
                Column {
                    BookRow(LibraryItem(book, availability = null), onTap = {}, onRemove = {}, onExport = {})
                }
                BookEditSheet(
                    book = PDF,
                    onSave = { draft ->
                        saved = draft.toEdit(PDF)
                        book = PDF.copy(author = draft.author.trim())
                    },
                    onFillFromFile = {},
                    onDismiss = {},
                    modifier = Modifier.testTag(SCREEN),
                )
            }
        }
        val authorField = hasSetTextAction() and hasText(context.getString(R.string.book_edit_author_field))
        rule.onNode(authorField).performTextReplacement("Rabindranath Tagore")
        rule.onNodeWithText(languageName("bn")).performScrollTo().performClick()
        rule.waitForIdle()
        save("edit-details", rule.onNodeWithTag(SCREEN).captureToImage().asAndroidBitmap())

        rule.onNodeWithText(context.getString(R.string.book_edit_save)).performScrollTo().performClick()
        rule.waitForIdle()

        assertEquals(BookEdit(author = "Rabindranath Tagore", language = "bn"), saved)
        rule.onNodeWithText(context.getString(R.string.book_details_author, "Rabindranath Tagore")).assertExists()
    }

    /** The name the sheet shows, as its languageName gives it in the test's locale. */
    private fun languageName(tag: String): String =
        Locale.forLanguageTag(tag).getDisplayLanguage(context.resources.configuration.locales[0])

    @Composable
    private fun Phone(content: @Composable () -> Unit) {
        DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(TEST_PHONE_SIZE)) {
            AudoraTheme { content() }
        }
    }

    /** Into Downloads/ListenLock-screens, which outlives the test app's uninstall; CI pulls it into an artifact. */
    private fun save(name: String, bitmap: Bitmap) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "$name.png")
            put(MediaStore.Downloads.MIME_TYPE, "image/png")
            put(MediaStore.Downloads.RELATIVE_PATH, SCREENS_FOLDER)
        }
        val resolver = context.contentResolver
        val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)) { "No $name" }
        requireNotNull(resolver.openOutputStream(uri)) { "Cannot write $name" }.use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)) { "PNG of $name failed" }
        }
    }

    private companion object {
        val TEST_PHONE_SIZE = DpSize(411.dp, 914.dp)
        const val PNG_QUALITY = 100
        const val SCREENS_FOLDER = "Download/ListenLock-screens"
        const val SCREEN = "screen"
        val BOOK = BookId("made-book")

        val AUDIOBOOK = OutputSet(
            "set-audio",
            BOOK,
            OutputKind.AUDIOBOOK,
            (1..2).map { part ->
                BookOutput(
                    BOOK, OutputKind.AUDIOBOOK, "set-audio", part, "Gitanjali - $part.m4a", "content://test/$part",
                    "audio/mp4", createdAtMillis = 1_761_300_000_000, sizeBytes = 14_000_000, durationMs = 1_800_000,
                    pitch = 1.1f,
                )
            },
        )
        val COPY = OutputSet(
            "set-copy",
            BOOK,
            OutputKind.ACCESSIBLE_COPY,
            listOf(
                BookOutput(
                    BOOK, OutputKind.ACCESSIBLE_COPY, "set-copy", 1, "Gitanjali.epub", "content://test/copy",
                    "application/epub+zip", createdAtMillis = 1_761_200_000_000, sizeBytes = 400_000,
                ),
            ),
        )
        val PDF = Book(
            BookId("edit-book"), "content://test/edit", "Gitanjali", 1_000, "f", 120, 1, null,
            format = DocumentFormat.PDF,
        )
    }
}
