// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/hints/FirstUseHintsUiTest.kt
// Purpose: The first-use hints on screen (ADR 2026-10-30-first-use-hints): a reader hint shows above Play with its
//          own button, which opens the place it names; Got it answers it for good and no second hint shows that app
//          start; muted shows none; the library's hint opens Add book. Leaves a screenshot for review in CI.

package io.github.skm0078.audora.ui.hints

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skm0078.audora.R
import io.github.skm0078.audora.battery.OnboardingPreferences
import io.github.skm0078.audora.core.config.ReaderConfig
import io.github.skm0078.audora.core.hints.FirstUseHint
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookAvailability
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.ContentIntent
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.ui.Screens
import io.github.skm0078.audora.ui.library.LibraryHint
import io.github.skm0078.audora.ui.library.LibraryItem
import io.github.skm0078.audora.ui.library.LibraryListState
import io.github.skm0078.audora.ui.reader.DocumentState
import io.github.skm0078.audora.ui.reader.NoReaderActions
import io.github.skm0078.audora.ui.reader.PageContent
import io.github.skm0078.audora.ui.reader.PlaybackSnapshot
import io.github.skm0078.audora.ui.reader.ReaderScreen
import io.github.skm0078.audora.ui.reader.ReaderState
import io.github.skm0078.audora.ui.reader.ReaderView
import io.github.skm0078.audora.ui.theme.AudoraTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirstUseHintsUiTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var hints: HintsState

    private fun text(id: Int): String = context.getString(id)

    @Before
    fun freshHints() {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit(commit = true) { clear() }
        hints = HintsState(context, FILE)
        // The one-time gesture tips take the same place and come first; this test is about the hints.
        OnboardingPreferences(context).gestureTipsSeen = true
    }

    private fun showReader(state: ReaderState) = rule.setContent {
        CompositionLocalProvider(LocalHints provides hints) {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(PHONE)) {
                AudoraTheme { ReaderScreen(state, ReaderConfig(), NoReaderActions) }
            }
        }
    }

    private fun awaitText(id: Int) = rule.waitUntil(TIMEOUT_MS) {
        rule.onAllNodes(hasText(text(id))).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun aFastSpeedHintShowsAbovePlayAndShowMeOpensSound() {
        showReader(reading(speed = FAST))
        awaitText(R.string.hint_smart_speed)
        rule.onNodeWithContentDescription(text(R.string.reader_play)).assertExists()
        Screens.save("first-use-hint", rule.onRoot().captureToImage().asAndroidBitmap())

        rule.onNodeWithText(text(R.string.hint_show_me)).performClick()
        awaitText(R.string.speech_smart_speed)
        rule.waitForIdle()
        assertTrue(FirstUseHint.SMART_SPEED in hints.history.seen)
        rule.onNodeWithText(text(R.string.hint_smart_speed)).assertDoesNotExist()
    }

    @Test
    fun gotItAnswersForGoodAndNoSecondHintShowsThisStart() {
        showReader(reading(speed = FAST, intent = ContentIntent.STORY))
        awaitText(R.string.hint_smart_speed)
        rule.onNodeWithText(text(R.string.hint_got_it)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(text(R.string.hint_smart_speed)).assertDoesNotExist()
        // The story hint applies too, but one hint an app start is the rule.
        rule.onNodeWithText(text(R.string.hint_cast)).assertDoesNotExist()
        val saved = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getStringSet(HintsState.KEY_SEEN, null)
        assertEquals(setOf(FirstUseHint.SMART_SPEED.key), saved)
    }

    @Test
    fun mutedShowsNoHint() {
        runBlocking { hints.load() }
        hints.setMuted(true)
        showReader(reading(speed = FAST))
        rule.onNodeWithContentDescription(text(R.string.reader_play)).assertExists()
        rule.waitForIdle()
        rule.onNodeWithText(text(R.string.hint_smart_speed)).assertDoesNotExist()
    }

    @Test
    fun theLibraryHintOpensAddBookAfterAFewStartsWithOneBook() {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit(commit = true) {
            putInt(HintsState.KEY_LIBRARY_VISITS, 2)
        }
        var addOpened = 0
        val one = LibraryListState.Loaded(listOf(LibraryItem(BOOK, BookAvailability.AVAILABLE)))
        rule.setContent {
            CompositionLocalProvider(LocalHints provides hints) {
                AudoraTheme { LibraryHint(one, onAddBook = { addOpened++ }) }
            }
        }
        awaitText(R.string.hint_more_books)
        rule.onNodeWithText(text(R.string.hint_show_me)).performClick()
        rule.waitForIdle()
        assertEquals(1, addOpened)
        assertEquals(3, hints.libraryVisits)
        rule.onNodeWithText(text(R.string.hint_more_books)).assertDoesNotExist()
    }

    private fun reading(speed: Float, intent: ContentIntent? = null) = ReaderState(
        bookId = BOOK.id,
        title = "Chokher Bali",
        document = DocumentState.Open(pageCount = 10),
        content = PageContent.Ready(listOf(ReadingUnit(0, 0, 0, 23, "Mahendra's mother wept.", Lang.EN, 0, null))),
        view = ReaderView.TEXT,
        playback = PlaybackSnapshot(
            PlaybackSnapshot.Connection.CONNECTED,
            styleKnown = true,
            speed = speed,
            readingIntent = intent,
        ),
    )

    private companion object {
        const val FILE = "first_use_hints_test"
        const val TIMEOUT_MS = 10_000L
        const val FAST = 2f
        val PHONE = DpSize(411.dp, 914.dp)
        val BOOK = Book(BookId("hint-book"), "file:///book.pdf", "Chokher Bali", 1L, "f", 10, 0L, null)
    }
}
