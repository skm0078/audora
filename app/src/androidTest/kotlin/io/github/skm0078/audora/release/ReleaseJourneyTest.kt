// File: app/src/androidTest/kotlin/io/github/skm0078/audora/release/ReleaseJourneyTest.kt
// Purpose: The release blockers a machine can check, on the real app (MainActivity, the playback service, the
//          bundled voice) on CI's emulator: first run, "Open with", play without the notification permission, the
//          media controls a notification or lock screen sends, a new voice and a reading setting applying at once,
//          Settings rows, pages and Back, Hide app content, the ListenLock name, and a scan read after the OCR
//          download. Steps run in name order on one fresh install, as one listener would use the app.

package io.github.skm0078.audora.release

import android.Manifest
import android.app.Activity
import android.view.WindowManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.printToLog
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.skm0078.audora.MainActivity
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.model.Lang
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
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ReleaseJourneyTest {
    @get:Rule(order = 0)
    val rule = createEmptyComposeRule()

    /** On a failed step, the screen's semantics tree goes to logcat (tag ReleaseJourney) next to the app's log. */
    @get:Rule(order = 1)
    val dumpOnFailure = object : TestWatcher() {
        override fun failed(e: Throwable, description: Description) {
            runCatching { rule.onAllNodes(SemanticsMatcher("any") { true }).onFirst().printToLog(LOG_TAG) }
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

    /** Off the main thread only (ActivityScenario.onActivity's rule). */
    private fun activity(): MainActivity {
        var current: MainActivity? = null
        requireNotNull(scenario) { "The app was never started" }.onActivity { current = it }
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

    @Test
    fun step01FirstRunWalksThroughAndFinishes() {
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

    @Test
    fun step02TheLauncherSaysListenLock() {
        val label = app.packageManager.getApplicationLabel(app.applicationInfo).toString()
        assertTrue("Launcher label was \"$label\"", label.startsWith("ListenLock"))
        assertFalse("Launcher label was \"$label\"", label.contains("Audora", ignoreCase = true))
    }

    @Test
    fun step03OpenWithAddsTheBookAndOpensTheReader() {
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

    @Test
    fun step04PlayStartsWithoutTheNotificationPermission() {
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

    @Test
    fun step05MediaControlsPauseResumeAndSkip() {
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

    @Test
    fun step06ANewVoiceAppliesWhilePlaying() {
        val restarts = JourneyDevice.logCount(VOICE_RESTART)
        chooseAnotherEnglishVoice()
        awaitLog(VOICE_RESTART, restarts)
        awaitReadingMovesOn("reading to go on in the new voice")
    }

    @Test
    fun step07ANewVoiceAppliesWhilePaused() {
        val player = requireNotNull(port)
        JourneyDevice.onMain(player::pause)
        awaitTrue("pause", state = { "Player: ${snapshot()}" }) { !snapshot().wantsToPlay }
        val restarts = JourneyDevice.logCount(VOICE_RESTART)
        chooseAnotherEnglishVoice()
        awaitLog(VOICE_RESTART, restarts)
        Thread.sleep(HOLD_MS)
        assertFalse("A voice change must not start a paused book", snapshot().wantsToPlay)
        JourneyDevice.onMain(player::play)
        awaitReadingMovesOn("play in the new voice")
    }

    @Test
    fun step08ASettingsRowTogglesFromItsTitleAndSkipCitationsAppliesAtOnce() {
        backToTabs()
        tapText(R.string.tab_settings)
        val row = toggleRow(text(R.string.settings_skip_citations_title))
        val before = toggleState(row)
        val rereads = JourneyDevice.logCount(TEXT_REREAD)
        // The title, not the switch: the whole row is the target (ADR-062).
        tap(hasText(text(R.string.settings_skip_citations_title)))
        awaitTrue("the switch to flip") { toggleState(row) != before }
        awaitLog(TEXT_REREAD, rereads)
        awaitReadingMovesOn("reading to go on after the book is read again")
        tap(hasText(text(R.string.settings_skip_citations_title)))
        awaitTrue("the switch to flip back") { toggleState(row) == before }
    }

    @Test
    fun step09HelpOpensWithoutToggling() {
        val title = text(R.string.settings_skip_citations_title)
        val row = toggleRow(title)
        val before = toggleState(row)
        tap(hasContentDescription(text(R.string.option_help_about, title)))
        tapText(R.string.option_help_ok)
        assertEquals("The help button must not toggle its row", before, toggleState(row))
    }

    @Test
    fun step10PrivacyAndAdvancedOpenAndGoBack() {
        for (page in listOf(R.string.settings_advanced_title, R.string.settings_group_privacy)) {
            node(hasText(text(page))).performScrollTo().performClick()
            waitForNode(hasContentDescription(text(R.string.navigate_back)))
            back()
            gone(hasContentDescription(text(R.string.navigate_back)))
            waitForNode(hasText(text(R.string.settings_group_listening)))
        }
    }

    @Test
    fun step11HideAppContentBlocksScreenshots() {
        node(hasText(text(R.string.settings_group_privacy))).performScrollTo().performClick()
        val row = toggleRow(text(R.string.hide_content_title))
        assertEquals(ToggleableState.Off, toggleState(row))
        tap(hasText(text(R.string.hide_content_title)))
        awaitTrue("FLAG_SECURE on the window") { secure(activity()) }
        tap(hasText(text(R.string.hide_content_title)))
        awaitTrue("FLAG_SECURE off again") { !secure(activity()) }
        back()
    }

    @Test
    fun step12AScanOffersOcrAndReadsAfterTheDownload() {
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

    private fun chooseAnotherEnglishVoice() {
        val library = VoiceLibrary.get(app)
        val rows = runBlocking { library.rows().first { it.isNotEmpty() } }
        val other = rows.firstOrNull { it.lang == Lang.EN && it.kind == VoiceKind.BUILT_IN && !it.readsLanguage }
        assumeTrue("This build has only one English voice", other != null)
        library.readingVoices.choose(Lang.EN, requireNotNull(other).id)
    }

    private fun secure(activity: Activity): Boolean {
        var flags = 0
        JourneyDevice.onMain { flags = activity.window.attributes.flags }
        return flags and WindowManager.LayoutParams.FLAG_SECURE != 0
    }

    companion object {
        private var scenario: ActivityScenario<MainActivity>? = null
        private var port: MediaControllerPlaybackPort? = null

        private const val LOG_TAG = "ReleaseJourney"
        private const val BOOK = "en-udhr-noto.pdf"
        private const val SCAN = "en-udhr-noto-scan200bad-p1.pdf"
        private val firstRunSteps = FirstRunStep.entries.size
        private const val MAX_BACKS = 4
        private const val POLL_MS = 200L
        private const val HOLD_MS = 2_000L
        private const val PERMISSION_PROMPT_MS = 5_000L
        private const val UI_TIMEOUT_MS = 15_000L
        private const val OPEN_TIMEOUT_MS = 60_000L
        private const val PLAY_TIMEOUT_MS = 90_000L
        private const val OCR_DOWNLOAD_TIMEOUT_MS = 360_000L

        // The service's own words when it acts on a change (AudoraPlaybackService.followReadingChanges).
        private const val VOICE_RESTART = "Reading voices changed; restarting the sentence"
        private const val TEXT_REREAD = "Reading text settings changed; reading the open book again"

        @JvmStatic
        @BeforeClass
        fun startCapturing() = JourneyDevice.captureLogs()

        @JvmStatic
        @AfterClass
        fun stopEverything() {
            JourneyDevice.onMain {
                port?.pause()
                port?.release()
            }
            port = null
            scenario?.close()
            scenario = null
        }
    }
}
