// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/tour/UiTourTest.kt
// Purpose: A screenshot tour of the real app for UI reviews: every main screen in English, Hindi and Bengali at font
//          sizes 1, 1.3 and 2 (and English in dark mode), on a common 6.7-inch phone screen. It fails on a word
//          broken across two lines, the sign of a squeezed row, and on what TalkBack would trip over
//          (TalkBackAudit); a screen it cannot reach is logged and skipped. Runs only when asked (ui-tour.yml passes
//          uiTour=true, and uiTourScales to tour fewer sizes).

package io.github.skm0078.audora.ui.tour

import android.content.ContentValues
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.LocaleList
import android.provider.MediaStore
import android.util.Log
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.core.graphics.scale
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skm0078.audora.MainActivity
import io.github.skm0078.audora.R
import io.github.skm0078.audora.battery.OnboardingPreferences
import io.github.skm0078.audora.language.AppLanguage
import io.github.skm0078.audora.language.AppLanguageStore
import io.github.skm0078.audora.language.AppLanguages
import io.github.skm0078.audora.release.JourneyDevice
import io.github.skm0078.audora.ui.firstrun.FirstRunStep
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiTourTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var pass: Pass
    private var shot = 0
    private val broken = mutableListOf<String>()
    private val talkBack = linkedSetOf<String>()

    /** One language, font size and theme. */
    private data class Pass(val language: AppLanguage, val book: String, val fontScale: Float, val dark: Boolean) {
        val name: String
            get() = "${language.tag}-font${(fontScale * PERCENT).toInt()}${if (dark) "-dark" else ""}"
    }

    @Test
    fun tourEveryScreen() {
        assumeTrue("Only ui-tour.yml runs the tour", InstrumentationRegistry.getArguments().getString(ARG) == "true")
        prepareDevice()
        val scales = InstrumentationRegistry.getArguments().getString(SCALES_ARG)?.split(',')?.map(String::toFloat)
        try {
            PASSES.filter { scales == null || it.fontScale in scales }.forEach { next ->
                runCatching { runPass(next) }.onFailure { Log.w(TAG, "Pass ${next.name} stopped: ${it.message}", it) }
            }
        } finally {
            scenario?.close()
            restoreDevice()
        }
        writeTalkBackReport()
        val report = listOf(
            "Words broken across lines (a squeezed row)" to broken,
            "TalkBack problems" to talkBack,
        ).filter { it.second.isNotEmpty() }
            .joinToString("\n") { (title, lines) -> "$title:\n" + lines.joinToString("\n") }
        assertTrue(report, report.isEmpty())
    }

    private fun runPass(next: Pass) {
        pass = next
        shot = 0
        Log.i(TAG, "Pass ${pass.name}")
        JourneyDevice.shell("settings put system font_scale ${pass.fontScale}")
        JourneyDevice.shell("cmd uimode night ${if (pass.dark) "yes" else "no"}")
        AppLanguageStore(app).save(pass.language)
        JourneyDevice.onMain { AppLanguages.applyToProcess(app, pass.language) }
        OnboardingPreferences(app).apply {
            firstRunDone = false
            gestureTipsSeen = false
            // The tour has its own screens test; here its offer would cover every pass's library.
            tourOffered = true
        }
        scenario?.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        settle()
        visit("first-run", ::firstRun)
        visit("library", ::library)
        visit("notes", ::notes)
        visit("made", ::made)
        visit("settings", ::settings)
        visit("reader", ::reader)
        visit("player", ::player)
    }

    /** Runs one part of the tour; a screen that cannot be reached is logged and the tour goes on from the tabs. */
    private fun visit(name: String, block: () -> Unit) {
        runCatching(block).onFailure { failure ->
            Log.w(TAG, "${pass.name}: could not tour $name: ${failure.message}")
            runCatching {
                capture("$name$FAILED")
                backToTabs()
            }.onFailure { Log.w(TAG, "${pass.name}: could not get back to the tabs: ${it.message}") }
        }
    }

    /** Only on an empty library, so only the first pass sees it. */
    private fun firstRun() {
        val steps = FirstRunStep.entries.size
        val first = hasText(text(R.string.first_run_step, 1, steps))
        val shown = runCatching { node(first, FIRST_RUN_WAIT_MS) }.isSuccess
        if (!shown) return
        repeat(steps) { index ->
            node(hasText(text(R.string.first_run_step, index + 1, steps)))
            capture("first-run-${index + 1}")
            if (index < steps - 1) tap(hasText(text(R.string.first_run_next)))
        }
        tap(hasText(text(R.string.first_run_later)))
        node(hasText(text(R.string.tab_library)))
    }

    private fun library() {
        capture("library")
        scrollCaptures("library")
    }

    private fun notes() {
        tap(hasText(text(R.string.tab_notes)))
        settle()
        capture("notes")
        tap(hasText(text(R.string.tab_library)))
    }

    /** The Made place, then Jobs from its top bar. */
    private fun made() {
        tap(hasText(text(R.string.tab_made)))
        settle()
        capture("made")
        tap(hasText(text(R.string.made_jobs_short)))
        settle()
        capture("jobs")
        backToTabs()
        tap(hasText(text(R.string.tab_library)))
    }

    private fun settings() {
        tap(hasText(text(R.string.tab_you)))
        settle()
        capture("settings")
        scrollCaptures("settings")
        for (page in listOf(R.string.settings_group_privacy, R.string.settings_advanced_title)) {
            openFromSettings(page)
            capture("settings-${app.resources.getResourceEntryName(page)}")
            scrollCaptures("settings-${app.resources.getResourceEntryName(page)}")
            back()
        }
        for (screen in listOf(R.string.settings_voices_title, R.string.storage_title, R.string.licences_title)) {
            openFromSettings(screen)
            capture(app.resources.getResourceEntryName(screen))
            scrollCaptures(app.resources.getResourceEntryName(screen))
            back()
        }
        openFromSettings(R.string.settings_battery_title)
        capture("battery-setup")
        backToTabs()
    }

    private fun openFromSettings(title: Int) {
        tap(hasText(text(R.string.tab_you)))
        settle()
        rule.onAllNodes(hasText(text(title))).onFirst().performScrollTo().performClick()
        settle()
    }

    private fun reader() {
        val uri = JourneyDevice.downloadedPdf(pass.book)
        val activity = requireNotNull(JourneyDevice.resumedActivity()) { "No app on screen" }
        JourneyDevice.onMain { activity.startActivity(JourneyDevice.openWith(uri)) }
        node(hasContentDescription(text(R.string.reader_play)), OPEN_TIMEOUT_MS)
        settle()
        capture("reader-page")
        tryTap(hasContentDescription(text(R.string.reader_show_text)))
        capture("reader-text")
        tryTap(hasContentDescription(text(R.string.reader_more)))
        capture("reader-more-menu")
        JourneyDevice.pressBackKey()
        settle()
        readerSheets()
        // Clickable only: in Bengali the time-left line also holds the speed word and sits first.
        tap(hasText(speedButtonText(), substring = true) and hasClickAction())
        settle()
        capture("speech-sheet")
        scrollCaptures("speech-sheet", pages = SHEET_PAGES)
        JourneyDevice.pressBackKey()
        settle()
        tryTap(hasContentDescription(text(R.string.reader_sleep_timer)))
        capture("sleep-menu")
        JourneyDevice.pressBackKey()
        settle()
        // Play, so the library shows the mini player that opens the full player.
        tryTap(hasContentDescription(text(R.string.reader_play)))
    }

    /** The reader's sheets: from its top bar, then from its More menu. */
    private fun readerSheets() {
        opened("chapters-sheet", hasContentDescription(text(R.string.chapters_title)))
        opened("bookmarks-sheet", hasContentDescription(text(R.string.bookmarks_title)))
        for ((name, item) in listOf(
            "page-text" to R.string.page_text_title,
            "search" to R.string.search_action,
            "up-next" to R.string.up_next_title,
        )) {
            opened(name, hasContentDescription(text(R.string.reader_more)), hasText(text(item)))
        }
    }

    /**
     * Taps [path] in turn, captures what opened and goes back. A step with nothing to tap ends it uncaptured; Back is
     * pressed only once something opened, so a missing button never backs out of the screen.
     */
    private fun opened(name: String, vararg path: SemanticsMatcher) {
        val taps = path.takeWhile { step -> runCatching { tap(step) }.isSuccess }.size
        if (taps == path.size) capture(name) else Log.w(TAG, "${pass.name}: could not open $name")
        if (taps > 0) back()
    }

    /** A book's menu and details sheet, from its row in the library. */
    private fun bookSheets() {
        val menu = hasContentDescription(textBefore(R.string.library_book_menu_named), substring = true)
        opened("book-menu", menu)
        opened("book-details", menu, hasText(text(R.string.book_details_title)))
        backToTabs()
    }

    /** The full player, from the mini player on the library once the reader is closed. */
    private fun player() {
        backToTabs()
        capture("library-with-book")
        bookSheets()
        // The mini player sits under the content, so its "Page 3 of 9" is the last one on screen.
        // Tap the clickable surface that holds it, not the text itself.
        val where = hasClickAction() and
            hasAnyDescendant(hasText(textBefore(R.string.library_playing_page_of), substring = true))
        node(where)
        rule.onAllNodes(where).onLast().performClick()
        settle()
        capture("player")
        backToTabs()
    }

    /** The speed button's words without the number ("Speed" from "Speed %1$s×"), in this pass's language. */
    private fun speedButtonText(): String = textBefore(R.string.reader_speed).ifEmpty { "×" }

    /** A format string's words before its first argument ("Page " from "Page %1$d of %2$d"). */
    private fun textBefore(id: Int): String = localized().resources.getText(id).toString().substringBefore('%').trim()

    private fun scrollCaptures(name: String, pages: Int = SCROLL_PAGES) {
        var previous = screenshot()
        for (page in 2..pages) {
            // The topmost window: a sheet or dialog over the screen, else the screen itself.
            rule.onAllNodes(isRoot()).onLast().performTouchInput {
                swipeUp(startY = height * SWIPE_FROM, endY = height * SWIPE_TO)
            }
            settle()
            val now = screenshot()
            if (middle(now).sameAs(middle(previous))) return
            save("$name-$page", now)
            previous = now
        }
    }

    private fun capture(name: String) {
        save(name, screenshot())
        checkWords(name)
        if (!name.endsWith(FAILED)) checkTalkBack(name)
    }

    /** Every window on screen; a menu needs no heading. Each problem is reported once per screen and language. */
    private fun checkTalkBack(name: String) {
        val roots = runCatching { rule.onAllNodes(isRoot()).fetchSemanticsNodes() }.getOrDefault(emptyList())
        talkBackProblems(roots, needsHeading = !name.endsWith(MENU)).forEach { problem ->
            if (talkBack.add("${pass.language.tag} $name: $problem")) Log.w(TAG, "${pass.name} $name: $problem")
        }
    }

    /** Every text on screen, the topmost window included; the test books' file names are long and may wrap anywhere. */
    private fun checkWords(name: String) {
        runCatching {
            rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
                .fetchSemanticsNodes()
        }.getOrDefault(emptyList()).forEach { node ->
            brokenWords(node).filterNot { BOOK_FILE in it }.forEach {
                broken += "${pass.name} $name: $it"
                Log.w(TAG, "${pass.name} $name: broken word: $it")
            }
        }
    }

    /** Every TalkBack problem, also as a file beside the screenshots, so a review can read it with them. */
    private fun writeTalkBackReport() {
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, TALKBACK_REPORT)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, FOLDER)
            }
            val resolver = app.contentResolver
            val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
            requireNotNull(resolver.openOutputStream(uri)).bufferedWriter().use { out ->
                talkBack.forEach { out.appendLine(it) }
            }
        }.onFailure { Log.w(TAG, "Could not write $TALKBACK_REPORT: ${it.message}") }
    }

    private fun screenshot(): Bitmap =
        requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()) { "No screenshot" }

    /** The screen without the status bar, whose clock changes. */
    private fun middle(bitmap: Bitmap): Bitmap =
        Bitmap.createBitmap(bitmap, 0, bitmap.height / EDGE_PART, bitmap.width, bitmap.height * 2 / EDGE_PART)

    private fun save(name: String, bitmap: Bitmap) {
        shot++
        val file = "${pass.name}-${shot.toString().padStart(2, '0')}-$name.webp"
        val small = bitmap.scale(SAVED_WIDTH, bitmap.height * SAVED_WIDTH / bitmap.width)
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, file)
            put(MediaStore.Downloads.MIME_TYPE, "image/webp")
            put(MediaStore.Downloads.RELATIVE_PATH, FOLDER)
        }
        val resolver = app.contentResolver
        val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)) { "No $file" }
        requireNotNull(resolver.openOutputStream(uri)) { "Cannot write $file" }.use { out ->
            check(small.compress(Bitmap.CompressFormat.WEBP_LOSSY, WEBP_QUALITY, out)) { "WebP of $file failed" }
        }
    }

    private fun settle() {
        rule.waitForIdle()
        Thread.sleep(SETTLE_MS)
    }

    private fun node(matcher: SemanticsMatcher, timeoutMs: Long = UI_TIMEOUT_MS) =
        rule.apply { waitUntil(timeoutMs) { onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() } }
            .onAllNodes(matcher).onFirst()

    private fun tap(matcher: SemanticsMatcher) {
        node(matcher).performClick()
        settle()
    }

    private fun tryTap(matcher: SemanticsMatcher) {
        runCatching { tap(matcher) }.onFailure { Log.w(TAG, "${pass.name}: nothing to tap for $matcher") }
    }

    private fun back() {
        JourneyDevice.pressBackKey()
        settle()
    }

    /** Back until the tab bar shows, or a fresh start of the app when Back cannot get there (or it left the screen). */
    private fun backToTabs() {
        val tabs = hasText(text(R.string.tab_you))
        val onTabs = { runCatching { rule.onAllNodes(tabs).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
        repeat(MAX_BACKS) {
            if (onTabs()) return
            if (JourneyDevice.resumedActivity() == null) return@repeat
            back()
        }
        if (!onTabs()) {
            scenario?.close()
            scenario = ActivityScenario.launch(MainActivity::class.java)
            settle()
        }
    }

    private fun localized(): Context {
        val config = Configuration(app.resources.configuration)
        config.setLocales(LocaleList.forLanguageTags(pass.language.tag))
        return app.createConfigurationContext(config)
    }

    private fun text(id: Int, vararg args: Any): String = localized().getString(id, *args)

    /** The screen size comes from the emulator's own settings (ui-tour.yml); only animations are set here. */
    private fun prepareDevice() {
        listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale").forEach {
            JourneyDevice.shell("settings put global $it 0")
        }
    }

    private fun restoreDevice() {
        JourneyDevice.shell("settings put system font_scale 1.0")
        JourneyDevice.shell("cmd uimode night no")
    }

    private val app: Context get() = JourneyDevice.app

    private companion object {
        const val TAG = "UiTour"
        const val ARG = "uiTour"
        const val SCALES_ARG = "uiTourScales"
        const val BOOK_FILE = "udhr"
        const val TALKBACK_REPORT = "talkback.txt"
        const val FAILED = "-failed"
        const val MENU = "-menu"
        const val FOLDER = "Download/ListenLock-screens/tour"
        const val PERCENT = 100

        const val SAVED_WIDTH = 720
        const val WEBP_QUALITY = 82

        // A swipe in the middle of the screen, clear of the system's edge gestures (one from the bottom goes home).
        const val SWIPE_FROM = 0.7f
        const val SWIPE_TO = 0.3f
        const val SCROLL_PAGES = 4
        const val SHEET_PAGES = 6
        const val EDGE_PART = 4

        const val SETTLE_MS = 600L
        const val UI_TIMEOUT_MS = 10_000L
        const val FIRST_RUN_WAIT_MS = 4_000L
        const val OPEN_TIMEOUT_MS = 60_000L
        const val MAX_BACKS = 4

        val PASSES = listOf(1f, 1.3f, 2f).flatMap { scale ->
            listOf(
                Pass(AppLanguage.ENGLISH, "en-udhr-noto.pdf", scale, dark = false),
                Pass(AppLanguage.HINDI, "hi-udhr-noto.pdf", scale, dark = false),
                Pass(AppLanguage.BENGALI, "bn-udhr-noto.pdf", scale, dark = false),
            )
        } + Pass(AppLanguage.ENGLISH, "en-udhr-noto.pdf", 1f, dark = true) +
            Pass(AppLanguage.HINDI, "hi-udhr-noto.pdf", 1.3f, dark = true)
    }
}
