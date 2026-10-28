// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/search/ActionSearchScreensTest.kt
// Purpose: The library's search offers actions and settings above the books: a tap starts the action or opens You on
//          the setting, scrolled to and tinted; Hindi and Bengali at large text; none in kids mode. Screenshots for
//          review in CI's "player-screens" artifact (ADR 2026-10-28-action-search).

package io.github.skm0078.audora.ui.search

import android.content.res.Configuration
import android.net.Uri
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.Locales
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.intl.LocaleList as ComposeLocales
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookAvailability
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.BookProgress
import io.github.skm0078.audora.core.library.LibraryQuery
import io.github.skm0078.audora.ui.Screens
import io.github.skm0078.audora.ui.diagnostics.ShareLogsState
import io.github.skm0078.audora.ui.kids.LocalKidsMode
import io.github.skm0078.audora.ui.library.LibraryActions
import io.github.skm0078.audora.ui.library.LibraryItem
import io.github.skm0078.audora.ui.library.LibraryListState
import io.github.skm0078.audora.ui.library.LibraryScreen
import io.github.skm0078.audora.ui.settings.FOCUSED_TAG
import io.github.skm0078.audora.ui.settings.SettingFocus
import io.github.skm0078.audora.ui.settings.SettingKey
import io.github.skm0078.audora.ui.settings.SettingsScreen
import io.github.skm0078.audora.ui.theme.AudoraTheme
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActionSearchScreensTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val asked = mutableListOf<String>()

    private val actions = object : LibraryActions {
        override fun onAddPdf(documentUri: Uri) = Unit

        override fun onLocate(id: BookId, documentUri: Uri) = Unit

        override fun onOpenBook(book: Book) = Unit

        override fun onContinue(book: Book) = Unit

        override fun onRequestRemoval(book: Book) = Unit

        override fun onExportAudiobook(book: Book) {
            asked += "audiobook ${book.title}"
        }

        override fun onConfirmRemoval() = Unit

        override fun onCancelRemoval() = Unit

        override fun onMessageShown() = Unit

        override fun onScreenResumed() = Unit

        override fun onOpenSetting(key: SettingKey) {
            asked += "setting $key"
        }
    }

    /** The library searched for [query]; [shown] are the books it keeps. */
    private fun showLibrary(
        query: String,
        shown: List<LibraryItem>,
        language: String = "en",
        fontScale: Float = 1f,
        kids: Boolean = false,
    ) = rule.setContent {
        val config = DeviceConfigurationOverride.ForcedSize(PHONE_SIZE) then
            DeviceConfigurationOverride.FontScale(fontScale) then
            DeviceConfigurationOverride.Locales(ComposeLocales(language))
        DeviceConfigurationOverride(config) {
            CompositionLocalProvider(LocalKidsMode provides kids) {
                AudoraTheme {
                    LibraryScreen(
                        listState = LibraryListState.Loaded(
                            items = shown,
                            all = listOf(GORA, DARKNESS),
                            query = LibraryQuery(text = query),
                            latest = GORA,
                        ),
                        busy = false,
                        message = null,
                        pendingRemoval = null,
                        actions = actions,
                        settingValues = mapOf(SettingKey.THEME to text(R.string.settings_dark_mode_system, language)),
                    )
                }
            }
        }
    }

    private fun text(id: Int, language: String = "en", vararg args: Any): String {
        val config = Configuration(context.resources.configuration)
        config.setLocales(LocaleList(Locale.forLanguageTag(language)))
        return context.createConfigurationContext(config).getString(id, *args)
    }

    private val inFound = hasAnyAncestor(hasTestTag(FOUND_TAG))

    @Test
    fun audiobookOffersSavingTheBookBeingRead() {
        showLibrary("audiobook", shown = emptyList())
        rule.onNodeWithTag(FOUND_TAG).assertIsDisplayed()
        rule.onNode(hasText(text(R.string.found_no_book, "en", "audiobook"))).assertIsDisplayed()
        Screens.save("search-actions-audiobook", rule.onRoot().captureToImage().asAndroidBitmap())

        rule.onNode(hasText(text(R.string.found_book_audiobook, "en", "Gora")) and inFound).performClick()
        assertEquals(listOf("audiobook Gora"), asked)
    }

    @Test
    fun darkOffersTheThemeAboveTheBooksItMatches() {
        showLibrary("dark", shown = listOf(DARKNESS))
        val where = text(R.string.found_path, "en", text(R.string.tab_you), text(R.string.settings_group_reading))
        val now = text(R.string.found_now, "en", where, text(R.string.settings_dark_mode_system))
        rule.onNode(hasText(now) and inFound).assertIsDisplayed()
        rule.onNode(hasText(text(R.string.found_books_heading)) and inFound).assertIsDisplayed()
        Screens.save("search-actions-dark", rule.onRoot().captureToImage().asAndroidBitmap())

        rule.onNode(hasText(text(R.string.settings_dark_mode_title)) and inFound).performClick()
        assertEquals(listOf("setting THEME"), asked)
    }

    @Test
    fun hindiFindsTheThemeAtLargeText() = themeFoundAtLargeText("hi", "थीम")

    @Test
    fun bengaliFindsTheThemeAtLargeText() = themeFoundAtLargeText("bn", "থিম")

    @Test
    fun englishWordsWorkInHindi() {
        showLibrary("dark", shown = listOf(DARKNESS), language = "hi")
        rule.onNode(hasText(text(R.string.settings_dark_mode_title, "hi")) and inFound).assertIsDisplayed()
    }

    private fun themeFoundAtLargeText(language: String, word: String) {
        showLibrary(word, shown = emptyList(), language = language, fontScale = LARGE_TEXT)
        rule.onNode(hasText(text(R.string.settings_dark_mode_title, language)) and inFound).assertIsDisplayed()
        rule.onNode(hasText(text(R.string.found_heading, language))).assertIsDisplayed()
        Screens.save("search-actions-$language-large", rule.onRoot().captureToImage().asAndroidBitmap())
    }

    @Test
    fun kidsModeOffersNoActionsOrSettings() {
        showLibrary("dark", shown = listOf(DARKNESS), kids = true)
        rule.waitForIdle()
        assertTrue(rule.onAllNodes(hasTestTag(FOUND_TAG)).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun aFoundSettingOpensItsPageScrolledToAndFocused() {
        var shown = false
        // Cleared once shown, as the app clears it, so going back to You later shows nothing again.
        var key by mutableStateOf<SettingKey?>(SettingKey.SHARE_LOGS)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(PHONE_SIZE)) {
                AudoraTheme {
                    SettingsScreen(
                        shareState = ShareLogsState.Idle,
                        batteryExempt = true,
                        versionName = "test",
                        showDebugTools = false,
                        onOpenBatterySetup = {},
                        onOpenVoices = {},
                        onOpenLicences = {},
                        onShareLogs = {},
                        onSimulateReadingSession = {},
                        focus = SettingFocus(key) {
                            key = null
                            shown = true
                        },
                    )
                }
            }
        }
        rule.mainClock.advanceTimeBy(TINTED_MS)
        rule.onNodeWithTag(FOCUSED_TAG).assertIsDisplayed().assertIsFocused()
        rule.onNode(hasText(text(R.string.settings_advanced_title))).assertIsDisplayed()
        Screens.save("search-setting-tinted", rule.onRoot().captureToImage().asAndroidBitmap())
        rule.mainClock.autoAdvance = true
        rule.waitUntil(TIMEOUT_MS) { shown }
        assertTrue(rule.onAllNodes(hasTestTag(FOCUSED_TAG)).fetchSemanticsNodes().isEmpty())
    }

    private companion object {
        val PHONE_SIZE = DpSize(411.dp, 914.dp)
        const val LARGE_TEXT = 2f
        const val TINTED_MS = 400L
        const val TIMEOUT_MS = 5_000L
        val GORA = LibraryItem(
            Book(BookId("gora"), "content://test/gora", "Gora", 1_000, "f-gora", 10, 0, null),
            BookAvailability.AVAILABLE,
            BookProgress(page = 4, pageCount = 10, savedAtMillis = 1L),
        )
        val DARKNESS = LibraryItem(
            Book(BookId("darkness"), "content://test/darkness", "Heart of Darkness", 900, "f-hod", 12, 1, null),
            BookAvailability.AVAILABLE,
        )
    }
}
