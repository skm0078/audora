// File: app/src/androidTest/kotlin/io/github/skm0078/audora/release/ReleaseJourneyTest.kt
// Purpose: The release blockers a machine can check, on the real app (MainActivity, the playback service, the
//          bundled voice) on CI's emulator: first run, "Open with", play without the notification permission, the
//          media controls a notification or lock screen sends, a new voice and a reading setting applying at once,
//          Settings rows, pages and Back, Hide app content, the ListenLock name, a scan read after the OCR
//          download, and the e-book formats and comic archives of ADR 2026-10-26-more-ebook-formats. One test on one
//          fresh install, as one listener would use the app: AndroidJUnitRunner finishes every activity between
//          tests, so the steps are named blocks inside it and the first broken one stops it.

package io.github.skm0078.audora.release

import android.Manifest
import android.app.Activity
import android.util.Log
import android.view.WindowManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.skm0078.audora.MainActivity
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.privacy.PrivacyPreferences
import io.github.skm0078.audora.privacy.PrivacySettings
import io.github.skm0078.audora.release.JourneyDevice.app
import io.github.skm0078.audora.release.JourneyDevice.awaitTrue
import io.github.skm0078.audora.speech.voices.VoiceKind
import io.github.skm0078.audora.speech.voices.VoiceLibrary
import io.github.skm0078.audora.ui.firstrun.FirstRunStep
import io.github.skm0078.audora.ui.reader.MediaControllerPlaybackPort
import io.github.skm0078.audora.ui.reader.PlaybackSnapshot
import io.github.skm0078.audora.ui.reader.ReadingPosition
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReleaseJourneyTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null
    private var port: MediaControllerPlaybackPort? = null

    @Test
    fun aListenerUsesTheAppFromFirstRunToAScan() {
        JourneyDevice.captureLogs()
        try {
            step("01 first run walks through and finishes", ::firstRunWalksThroughAndFinishes)
            step("02 the launcher says ListenLock", ::theLauncherSaysListenLock)
            step("03 Open with adds the book and opens the reader", ::openWithAddsTheBookAndOpensTheReader)
            step("04 play starts without the notification permission", ::playStartsWithoutTheNotificationPermission)
            step("05 media controls pause, resume and skip", ::mediaControlsPauseResumeAndSkip)
            step("06 a new voice applies while playing", ::aNewVoiceAppliesWhilePlaying)
            step("07 a new voice applies while paused", ::aNewVoiceAppliesWhilePaused)
            step("08 a Settings row toggles from its title and applies at once", ::aRowTogglesFromItsTitle)
            step("09 help opens without toggling", ::helpOpensWithoutToggling)
            step("10 Privacy and Advanced open and go back", ::privacyAndAdvancedOpenAndGoBack)
            step("11 Hide app content blocks screenshots", ::hideAppContentBlocksScreenshots)
            step("12 a scan offers OCR and reads after the download", ::aScanOffersOcrAndReadsAfterTheDownload)
            step("13 Open with reads FB2, Kindle and help-file books", ::openWithReadsMoreEbookFormats)
            step("14 Open with makes a comic archive into a photo book", ::openWithMakesAComicIntoAPhotoBook)
        } finally {
            JourneyDevice.onMain {
                port?.pause()
                port?.release()
            }
            scenario?.close()
        }
    }

    /**
     * Runs one named step; a failure names it and dumps the screen's semantics tree to logcat (tag ReleaseJourney)
     * next to the app's own log.
     */
    private fun step(name: String, block: () -> Unit) {
        Log.i(LOG_TAG, "Step $name")
        try {
            block()
        } catch (failure: Throwable) {
            runCatching { Log.i(LOG_TAG, rule.onAllNodes(isRoot()).onFirst().printToString(Int.MAX_VALUE)) }
            throw AssertionError("Step $name failed: ${failure.message}", failure)
        }
    }

    private fun text(id: Int, vararg args: Any): String = app.getString(id, *args)

    private fun waitForNode(matcher: SemanticsMatcher, timeoutMs: Long = UI_TIMEOUT_MS) {
        rule.waitUntil(timeoutMs) { rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun node(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        waitForNode(matcher)
        return rule.onAllNodes(matcher).onFirst()
    }

    private fun tap(matcher: SemanticsMatcher) {
        node(matcher).performClick()
    }

    private fun tapText(id: Int, vararg args: Any) = tap(hasText(text(id, *args)))

    private fun gone(matcher: SemanticsMatcher, timeoutMs: Long = UI_TIMEOUT_MS) {
        rule.waitUntil(timeoutMs) { rule.onAllNodes(matcher).fetchSemanticsNodes().isEmpty() }
    }

    private fun back() {
        val current = activity()
        JourneyDevice.onMain { current.onBackPressedDispatcher.onBackPressed() }
    }

    /** The app on screen now (MainActivity is singleTop, so "Open with" reaches the same one). */
    private fun activity(): MainActivity {
        var current: MainActivity? = null
        awaitTrue("the app on screen") {
            current = JourneyDevice.resumedActivity() as? MainActivity
            current != null
        }
        return requireNotNull(current)
    }

    private fun snapshot(): PlaybackSnapshot = port?.state?.value ?: PlaybackSnapshot.Connecting

    private fun position(): ReadingPosition? = snapshot().position

    /** Reading is under way and has moved past [from]: the voice is rendering and the player is playing it. */
    private fun awaitReadingMovesOn(what: String, from: ReadingPosition? = position()) = awaitTrue(
        what,
        PLAY_TIMEOUT_MS,
        state = { "Player: ${snapshot()}" },
    ) { snapshot().wantsToPlay && position() != null && position() != from }

    private fun awaitLog(fragment: String, before: Int) =
        awaitTrue("the log line \"$fragment\"", state = { "Seen ${JourneyDevice.logCount(fragment)} time(s)" }) {
            JourneyDevice.logCount(fragment) > before
        }

    private fun toggleRow(title: String): SemanticsMatcher = hasText(title) and isToggleable()

    private fun toggleState(matcher: SemanticsMatcher): ToggleableState =
        node(matcher).fetchSemanticsNode().config[SemanticsProperties.ToggleableState]

    /** Back until the tab bar shows (the library, from a reader or player opened on top of it). */
    private fun backToTabs() {
        val settingsTab = hasText(text(R.string.tab_settings))
        repeat(MAX_BACKS) {
            if (rule.onAllNodes(settingsTab).fetchSemanticsNodes().isNotEmpty()) return
            back()
            rule.waitForIdle()
        }
        waitForNode(settingsTab)
    }

    private fun firstRunWalksThroughAndFinishes() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        val stepOne = hasText(text(R.string.first_run_step, 1, firstRunSteps))
        waitForNode(stepOne)
        node(hasText(text(R.string.first_run_privacy))).assertExists()
        repeat(firstRunSteps - 1) { tapText(R.string.first_run_next) }
        node(hasText(text(R.string.first_run_step, firstRunSteps, firstRunSteps))).assertExists()
        tapText(R.string.first_run_later)
        waitForNode(hasText(text(R.string.tab_library)))
        gone(stepOne)
    }

    private fun theLauncherSaysListenLock() {
        val label = app.packageManager.getApplicationLabel(app.applicationInfo).toString()
        assertTrue("Launcher label was \"$label\"", label.startsWith("ListenLock"))
        assertFalse("Launcher label was \"$label\"", label.contains("Audora", ignoreCase = true))
    }

    private fun openWithAddsTheBookAndOpensTheReader() {
        val uri = JourneyDevice.downloadedPdf(BOOK)
        val current = activity()
        JourneyDevice.onMain { current.startActivity(JourneyDevice.openWith(uri)) }
        waitForNode(hasContentDescription(text(R.string.reader_play)), OPEN_TIMEOUT_MS)
        JourneyDevice.onMain {
            port = MediaControllerPlaybackPort(app).also(MediaControllerPlaybackPort::connect)
        }
        awaitTrue("the playback service to connect", state = { "Player: ${snapshot()}" }) {
            snapshot().connection == PlaybackSnapshot.Connection.CONNECTED
        }
    }

    private fun playStartsWithoutTheNotificationPermission() {
        assertFalse(
            "A fresh install must not hold the notification permission yet",
            JourneyDevice.hasPermission(Manifest.permission.POST_NOTIFICATIONS),
        )
        tap(hasContentDescription(text(R.string.reader_play)))
        // Android 13+ asks first; answering "Don't allow" (Back) must still start reading.
        val askedAt = System.currentTimeMillis()
        while (System.currentTimeMillis() - askedAt < PERMISSION_PROMPT_MS && JourneyDevice.resumedActivity() != null) {
            Thread.sleep(POLL_MS)
        }
        if (JourneyDevice.resumedActivity() == null) JourneyDevice.pressBackKey()
        awaitTrue("the app back on screen") { JourneyDevice.resumedActivity() is MainActivity }
        awaitReadingMovesOn("the first sentence to play", from = null)
        awaitReadingMovesOn("reading to go on to the next sentence")
    }

    private fun mediaControlsPauseResumeAndSkip() {
        val player = requireNotNull(port)
        JourneyDevice.onMain(player::pause)
        awaitTrue("pause", state = { "Player: ${snapshot()}" }) { !snapshot().wantsToPlay }
        val paused = position()
        Thread.sleep(HOLD_MS)
        assertEquals("Paused reading must stay on its sentence", paused, position())
        awaitTrue("the next sentence to be ready", state = { "Player: ${snapshot()}" }) { snapshot().canSkipForward }
        JourneyDevice.onMain(player::skipToNextSentence)
        awaitTrue("next sentence while paused", state = { "Player: ${snapshot()}" }) { position() != paused }
        JourneyDevice.onMain(player::play)
        awaitReadingMovesOn("play after pause and skip")
    }

    private fun aNewVoiceAppliesWhilePlaying() {
        val restarts = JourneyDevice.logCount(VOICE_RESTART)
        if (!chooseAnotherEnglishVoice()) return
        awaitLog(VOICE_RESTART, restarts)
        awaitReadingMovesOn("reading to go on in the new voice")
    }

    private fun aNewVoiceAppliesWhilePaused() {
        val player = requireNotNull(port)
        JourneyDevice.onMain(player::pause)
        awaitTrue("pause", state = { "Player: ${snapshot()}" }) { !snapshot().wantsToPlay }
        val restarts = JourneyDevice.logCount(VOICE_RESTART)
        if (!chooseAnotherEnglishVoice()) {
            JourneyDevice.onMain(player::play)
            return
        }
        awaitLog(VOICE_RESTART, restarts)
        Thread.sleep(HOLD_MS)
        assertFalse("A voice change must not start a paused book", snapshot().wantsToPlay)
        JourneyDevice.onMain(player::play)
        awaitReadingMovesOn("play in the new voice")
    }

    private fun aRowTogglesFromItsTitle() {
        // While a book plays, its highlight recomposes every frame and Compose's test sync never goes idle, so the
        // screens are driven paused; the service acting on the setting and reading on afterwards is what is checked.
        pause()
        backToTabs()
        tapText(R.string.tab_settings)
        val row = toggleRow(text(R.string.settings_skip_citations_title))
        val before = toggleState(row)
        val rereads = JourneyDevice.logCount(TEXT_REREAD)
        // The title, not the switch: the whole row is the target (ADR-062).
        tap(hasText(text(R.string.settings_skip_citations_title)))
        awaitTrue("the switch to flip") { toggleState(row) != before }
        awaitLog(TEXT_REREAD, rereads)
        JourneyDevice.onMain(requireNotNull(port)::play)
        awaitReadingMovesOn("reading to go on after the book is read again")
        pause()
        tap(hasText(text(R.string.settings_skip_citations_title)))
        awaitTrue("the switch to flip back") { toggleState(row) == before }
    }

    private fun helpOpensWithoutToggling() {
        val title = text(R.string.settings_skip_citations_title)
        val row = toggleRow(title)
        val before = toggleState(row)
        tap(hasContentDescription(text(R.string.option_help_about, title)))
        tapText(R.string.option_help_ok)
        assertEquals("The help button must not toggle its row", before, toggleState(row))
    }

    private fun privacyAndAdvancedOpenAndGoBack() {
        for (page in listOf(R.string.settings_advanced_title, R.string.settings_group_privacy)) {
            node(hasText(text(page))).performScrollTo().performClick()
            waitForNode(hasContentDescription(text(R.string.navigate_back)))
            back()
            gone(hasContentDescription(text(R.string.navigate_back)))
            waitForNode(hasText(text(R.string.settings_group_listening)))
        }
    }

    private fun hideAppContentBlocksScreenshots() {
        node(hasText(text(R.string.settings_group_privacy))).performScrollTo().performClick()
        val row = toggleRow(text(R.string.hide_content_title))
        assertEquals(ToggleableState.Off, toggleState(row))
        node(row).performScrollTo().performClick()
        // One link at a time, so a failure names the broken one: the switch, the saved setting, the window.
        awaitTrue("the switch to turn on") { toggleState(row) == ToggleableState.On }
        awaitTrue("the setting to be saved", state = { "Saved: ${savedPrivacy()}" }) { savedPrivacy().hideContent }
        awaitTrue("FLAG_SECURE on the window", state = { windowState() }) { secure(activity()) }
        node(row).performScrollTo().performClick()
        awaitTrue("the switch to turn off") { toggleState(row) == ToggleableState.Off }
        awaitTrue("FLAG_SECURE off again", state = { windowState() }) { !secure(activity()) }
        back()
    }

    private fun aScanOffersOcrAndReadsAfterTheDownload() {
        val uri = JourneyDevice.downloadedPdf(SCAN)
        val current = activity()
        JourneyDevice.onMain { current.startActivity(JourneyDevice.openWith(uri)) }
        val card = hasText(text(R.string.reader_ocr_needs_models))
        waitForNode(card, OPEN_TIMEOUT_MS)
        // The open scan must read once the files land, without reopening the book (the untested blocker, X03).
        tap(hasText(text(R.string.ocr_models_download_short), substring = true) and hasClickAction())
        gone(card, OCR_DOWNLOAD_TIMEOUT_MS)
        waitForNode(hasText(text(R.string.reader_ocr_badge)), OCR_DOWNLOAD_TIMEOUT_MS)
    }

    /**
     * Each format is told from its bytes, added and opened as a text book (ADR 2026-10-26-more-ebook-formats): the
     * reader shows the book's own first sentence. The library's mini player also has a Play button, so the reader's
     * text, not its controls, is what is checked; TextBooks' log lines name a failed open.
     */
    private fun openWithReadsMoreEbookFormats() {
        for ((asset, type, sentence) in MORE_FORMATS) {
            backToTabs()
            val uri = JourneyDevice.downloadedBook(asset)
            val current = activity()
            JourneyDevice.onMain { current.startActivity(JourneyDevice.openWith(uri, type)) }
            awaitTrue("$asset to show \"$sentence\" in the reader", OPEN_TIMEOUT_MS, state = ::textBookLog) {
                rule.onAllNodes(hasText(sentence, substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    private fun textBookLog(): String =
        "Opened: ${JourneyDevice.logCount(TEXT_BOOK_OPENED)}, not opened: ${JourneyDevice.logCount(TEXT_BOOK_FAILED)}"

    /**
     * A comic archive is not added as a book: its pages become a photo book, added to the library, which opens in the
     * reader by itself. Its pages are blank pictures; OCR reading a picture page is step 12's. The library's work runs
     * in the screen's scope, which the Compose test clock drives, so these waits go through [awaitSynced].
     */
    private fun openWithMakesAComicIntoAPhotoBook() {
        backToTabs()
        val added = JourneyDevice.logCount(PHOTO_BOOK_ADDED)
        val uri = JourneyDevice.downloadedBook(COMIC)
        val current = activity()
        JourneyDevice.onMain { current.startActivity(JourneyDevice.openWith(uri, COMIC_TYPE)) }
        awaitSynced("the comic to be added as a photo book", OPEN_TIMEOUT_MS) {
            JourneyDevice.logCount(PHOTO_BOOK_ADDED) > added
        }
        awaitSynced("the comic to open in the reader", OPEN_TIMEOUT_MS) {
            rule.onAllNodes(hasText(COMIC_TITLE)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Waits on the Compose test clock (frames and effects run meanwhile); a timeout lists the app's last log lines. */
    private fun awaitSynced(what: String, timeoutMs: Long, condition: () -> Boolean) {
        try {
            rule.waitUntil(timeoutMs, condition)
        } catch (e: ComposeTimeoutException) {
            throw AssertionError("Timed out waiting for: $what. Last log lines: ${JourneyDevice.logTail(LOG_TAIL)}", e)
        }
    }

    private fun pause() {
        JourneyDevice.onMain(requireNotNull(port)::pause)
        awaitTrue("pause", state = { "Player: ${snapshot()}" }) { !snapshot().wantsToPlay }
    }

    /** False (logged, the step passes) on a build with only one English voice. */
    private fun chooseAnotherEnglishVoice(): Boolean {
        val library = VoiceLibrary.get(app)
        val rows = runBlocking { library.rows().first { it.isNotEmpty() } }
        val other = rows.firstOrNull { it.lang == Lang.EN && it.kind == VoiceKind.BUILT_IN && !it.readsLanguage }
        if (other == null) {
            Log.w(LOG_TAG, "Only one English voice in this build; the voice change is not checked")
            return false
        }
        library.readingVoices.choose(Lang.EN, other.id)
        return true
    }

    private fun savedPrivacy(): PrivacySettings = runBlocking { PrivacyPreferences(app).settings.first() }

    private fun windowState(): String {
        val resumed = JourneyDevice.resumedActivity()
        return "Resumed: $resumed, secure: ${resumed?.let(::secure)}"
    }

    private fun secure(activity: Activity): Boolean {
        var flags = 0
        JourneyDevice.onMain { flags = activity.window.attributes.flags }
        return flags and WindowManager.LayoutParams.FLAG_SECURE != 0
    }

    private companion object {
        const val LOG_TAG = "ReleaseJourney"
        const val BOOK = "en-udhr-noto.pdf"
        const val SCAN = "en-udhr-noto-scan200bad-p1.pdf"

        // assets/ebooks, written by scripts/fixtures/make_ebook_fixtures.py, with the type a Files app sends and
        // a sentence from the book's first page.
        val MORE_FORMATS = listOf(
            Triple("book.fb2", "application/x-fictionbook+xml", "The first chapter of a small FictionBook."),
            Triple("kf8.azw3", "application/vnd.amazon.mobi8-ebook", "The first file"),
            Triple("help.chm", "application/vnd.ms-htmlhelp", "Press the green button to begin."),
        )

        // TextBooks' own words when a text book opens, or does not (shown when a step times out).
        const val TEXT_BOOK_OPENED = "Text book opened"
        const val TEXT_BOOK_FAILED = "Text book not opened"
        const val COMIC = "comic.cbz"
        const val COMIC_TYPE = "application/vnd.comicbook+zip"

        // The photo book is named after the file; the reader's top bar shows it.
        const val COMIC_TITLE = "comic"

        // PhotoBooks' own words once the photo book is in the library.
        const val PHOTO_BOOK_ADDED = "Photo book added"
        const val LOG_TAIL = 30
        val firstRunSteps = FirstRunStep.entries.size
        const val MAX_BACKS = 4
        const val POLL_MS = 200L
        const val HOLD_MS = 2_000L
        const val PERMISSION_PROMPT_MS = 5_000L
        const val UI_TIMEOUT_MS = 15_000L
        const val OPEN_TIMEOUT_MS = 60_000L
        const val PLAY_TIMEOUT_MS = 90_000L
        const val OCR_DOWNLOAD_TIMEOUT_MS = 360_000L

        // The service's own words when it acts on a change (AudoraPlaybackService.followReadingChanges).
        const val VOICE_RESTART = "Reading voices changed; restarting the sentence"
        const val TEXT_REREAD = "Reading text settings changed; reading the open book again"
    }
}
