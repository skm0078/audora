// File: app/src/androidTest/kotlin/io/github/skm0078/audora/release/ReleaseJourneyTest.kt
// Purpose: The release blockers a machine can check, on the real app (MainActivity, the playback service, the
//          bundled voice) on CI's emulator: first run, "Open with", play without the notification permission, the
//          media controls a notification or lock screen sends and its own buttons (CF-1), a new voice, a book's
//          language and a reading setting applying at once, Settings rows, pages and Back, Hide app content, the
//          ListenLock name, a scan read after the OCR download, the e-book formats and comic archives of ADR
//          2026-10-26-more-ebook-formats, the cover inside exported audiobooks, the app's own font in English, Hindi
//          and Bengali, and the four places with Jobs under Made. One test on one fresh install, as
//          one listener would use the app: AndroidJUnitRunner finishes every activity between tests, so the steps are
//          named blocks inside it and the first broken one stops it.

package io.github.skm0078.audora.release

import android.Manifest
import android.app.Activity
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily as PlatformFontFamily
import android.graphics.fonts.FontStyle
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.util.concurrent.ListenableFuture
import io.github.skm0078.audora.MainActivity
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.config.QuoteVideoConfig
import io.github.skm0078.audora.core.library.BookField
import io.github.skm0078.audora.core.library.DocumentFormat
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.getOrNull
import io.github.skm0078.audora.core.share.VideoTimeline
import io.github.skm0078.audora.di.LibraryEntryPoint
import io.github.skm0078.audora.playback.contract.LockScreenCommands
import io.github.skm0078.audora.playback.contract.PlaybackContract
import io.github.skm0078.audora.playback.covers.CoverFolders
import io.github.skm0078.audora.playback.export.AacAudioSink
import io.github.skm0078.audora.playback.export.AudioChapter
import io.github.skm0078.audora.playback.export.BookTags
import io.github.skm0078.audora.playback.export.CoverJpeg
import io.github.skm0078.audora.playback.export.LocalChapterFiles
import io.github.skm0078.audora.playback.export.video.AiVoiceLabel
import io.github.skm0078.audora.playback.export.video.QuoteVideoFrames
import io.github.skm0078.audora.playback.export.video.QuoteVideoWriter
import io.github.skm0078.audora.privacy.PrivacyPreferences
import io.github.skm0078.audora.privacy.PrivacySettings
import io.github.skm0078.audora.release.JourneyDevice.app
import io.github.skm0078.audora.release.JourneyDevice.awaitTrue
import io.github.skm0078.audora.release.JourneyDevice.hides
import io.github.skm0078.audora.release.JourneyDevice.shows
import io.github.skm0078.audora.speech.audio.PcmAudio
import io.github.skm0078.audora.speech.audio.WordTimes
import io.github.skm0078.audora.speech.voices.VoiceKind
import io.github.skm0078.audora.speech.voices.VoiceLibrary
import io.github.skm0078.audora.speech.voices.VoiceStatus
import io.github.skm0078.audora.ui.firstrun.FirstRunStep
import io.github.skm0078.audora.ui.library.BOOK_PANE_TAG
import io.github.skm0078.audora.ui.reader.MediaControllerPlaybackPort
import io.github.skm0078.audora.ui.reader.PlaybackSnapshot
import io.github.skm0078.audora.ui.reader.ReadingPosition
import io.github.skm0078.audora.ui.reader.SIDE_PANEL_TAG
import io.github.skm0078.audora.ui.share.ShareCardActivity
import io.github.skm0078.audora.ui.share.ShareCardFiles
import io.github.skm0078.audora.ui.share.ShareEntry
import io.github.skm0078.audora.ui.theme.AppFont
import java.io.File
import java.util.concurrent.TimeUnit
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
            firstRunToTheLibrary()
            readingSharingAndVoices()
            // Last on purpose: run after step 19, the thousand books' background work left step 26 without its menu.
            step("40 search finds the last of 1,000 books", LoadJourney(rule, ::backToTabs)::lastOfManyIsFound)
        } finally {
            JourneyDevice.onMain {
                port?.pause()
                port?.release()
            }
            scenario?.close()
        }
    }

    /** Steps 01 to 20: first run, playback, settings, every format, and the library's menus. */
    private fun firstRunToTheLibrary() {
        step(
            "01 first run plays a sample, opens the reader and offers the tour once",
            ::firstRunWalksThroughAndFinishes,
        )
        step("02 the launcher says ListenLock and links go to its site", BrandJourney::nameAndLinksAreListenLocks)
        step("03 Open with adds the book and opens the reader", ::openWithAddsTheBookAndOpensTheReader)
        step("04 play starts without the notification permission", ::playStartsWithoutTheNotificationPermission)
        step("05 media controls pause, resume and skip", ::mediaControlsPauseResumeAndSkip)
        step("05b the lock screen's buttons go back, on and bookmark", ::lockScreenButtonsGoBackOnAndBookmark)
        step("06 a new voice applies while playing", ::aNewVoiceAppliesWhilePlaying)
        step("07 a new voice applies while paused", ::aNewVoiceAppliesWhilePaused)
        step("07b a book language set in Edit details applies at once", ::aBookLanguageAppliesAtOnce)
        step("08 a Settings row toggles from its title and applies at once", ::aRowTogglesFromItsTitle)
        step("09 help opens without toggling", ::helpOpensWithoutToggling)
        step("10 Privacy and Advanced open and go back", ::privacyAndAdvancedOpenAndGoBack)
        step("11 Hide app content blocks screenshots", ::hideAppContentBlocksScreenshots)
        step("12 a scan offers OCR and reads after the download", ::aScanOffersOcrAndReadsAfterTheDownload)
        step("13 Open with reads FB2, Kindle and help-file books", ::openWithReadsMoreEbookFormats)
        step("14 Open with makes a comic archive into a photo book", ::openWithMakesAComicIntoAPhotoBook)
        step("15 Open with reads a DjVu's text and makes a DjVu scan a photo book", ::openWithReadsDjvuBooks)
        step("16 Supertonic's licence rules show before its download", ::supertonicRulesShowBeforeItsDownload)
        step("17 an exported chapter carries the book's cover", ::anExportedChapterCarriesTheCover)
        step("18 the app font draws English, Hindi and Bengali", ::theAppFontDrawsAllThreeScripts)
        step("19 the four places open and Jobs goes back to Made", ::theFourPlacesOpenAndJobsLiveInMade)
        step("20 the book menu's Save as opens and the sort sheet turns covers to a list", ::bookMenuAndSortSheet)
        // Right after 20, on its library: from 24b on, the library's search can still hold a setting's words.
        step("42 a phone book says it is on this phone", SourceJourney(rule, ::backToTabs)::aPhoneBookSaysWhereItLives)
    }

    /** Steps 21 on: sharing, page turns, the tour, character voices, symbols, Made, catalogues and the home. */
    private fun readingSharingAndVoices() {
        step("21 Share as a picture makes a story-sized card and opens the share sheet", ::aQuoteCardIsShared)
        step("22 a swipe turns the PDF page", ::aSwipeTurnsThePdfPage)
        step("23 a quote video is written with the AI voice label", ::aQuoteVideoIsWritten)
        step(
            "24 a phone keeps the reader's bookmarks sheet and the library has no book pane",
            ::aPhoneKeepsTheSheets,
        )
        step("24b a search finds a setting, opens it on You, and Back returns to the search") {
            SearchJourney(rule, ::back).opensASetting()
        }
        step("24c You › Show me around walks the 5 stops and Done returns to the library", TourJourney(rule)::replays)
        val share = ShareJourney(rule, ::activity, ::backToTabs)
        step("25 Share as a video reads the quote in the book's voice into an MP4", share::aQuoteVideoIsShared)
        step("26 part of the shown page is shared as a picture with its credit") {
            share.aPageSnippetIsShared(BOOK)
        }
        step("27 Turn pages shows a text book a screenful at a time with its arrows") {
            share.aTextBookTurnsPages(MORE_FORMATS.first())
        }
        step("28 a text turn slides in and settles where the text was") {
            share.aTextTurnSlidesAndSettles(MORE_FORMATS.first())
        }
        step("50 a PDF page curls as it turns, and Page and text keeps Slide when picked") {
            CurlJourney(rule, ::activity).aPdfPageCurlsAndSlideCanBePicked(BOOK)
        }
        step(
            "29 a story's women and men each get a voice of their own gender",
            CharacterJourney::womenAndMenGetVoicesOfTheirGender,
        )
        step("30 the stats screen saves and shares its card", StatsJourney(rule, ::backToTabs)::cardIsSavedAndShared)
        step("31 the app lock shows the saver, or opens with no screen lock", LockJourney(rule)::coversOrOpens)
        step("43 arrows, maths and emoji reach the voice as words or not at all") {
            SymbolJourney.symbolsAreSaidOrLeftOut()
        }
        step("32 Made plays a saved audiobook like the book player", MadeJourney(rule, ::backToTabs)::playsLikeBook)
        step("41 catalogues are a curated list", CatalogueJourney(rule, ::backToTabs)::theCuratedCataloguesAreListed)
        step("44 Make it yours names the greeting and adds a part", HomeJourney(rule, ::backToTabs)::makesItYours)
        step("45 the phone drawer opens Settings", DrawerJourney(rule, ::backToTabs)::opensSettings)
        step("46 the phone drawer opens Make it yours", DrawerJourney(rule, ::backToTabs)::opensMakeItYours)
        step("47 Settings' switches say what they cost", RowLinesJourney(rule, ::backToTabs)::settingsSayWhatTheyCost)
        step("48 This week is a home part and opens Your listening", HomeJourney(rule, ::backToTabs)::offersThisWeek)
        step("49 the side panel holds the library's tools", DrawerJourney(rule, ::backToTabs)::holdsTheLibrarysTools)
        step("51 player shortcuts open their group", SpeechJourney(rule, BOOK, ::backToTabs)::shortcutsOpenTheirGroup)
        step("52 From ListenLock opens the cookbook", CookbookJourney(rule, ::backToTabs)::opensFromTheLibrary)
        step("53 the reader's Share opens the choices and a card in the reader's colours with Whole page") {
            ShareHereJourney(rule, ::activity).theShareButtonOpensTheChoices(BOOK)
        }
        step("54 search and You open the player's options", FindOptionsJourney(rule, ::backToTabs)::openTheSheet)
        step("55 Jobs cleans up unfinished work", JobsCleanupJourney(rule, ::backToTabs)::cleansUpUnfinishedWork)
        step("56 an open book loads its voice early", WarmUpJourney(rule, BOOK, { port }, ::backToTabs)::loadsTheVoice)
        step("57 books zipped with code-page names open") {
            ZipNamesJourney(rule, ::activity, ::backToTabs).booksWithCodePageNamesOpen()
        }
        step("58 the player names its line actions", LineActionsJourney(rule, BOOK, ::backToTabs)::namedAndInSight)
        step("61 Voices & licences opens every licence text") {
            NoticesJourney(rule, ::backToTabs).everyLicenceTextOpens()
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
        rule.waitUntil(timeoutMs) { rule.shows(matcher) }
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
        rule.waitUntil(timeoutMs) { rule.hides(matcher) }
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
        val settingsTab = hasText(text(R.string.tab_you))
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
        tapText(R.string.first_run_next)
        playAVoiceSample()
        repeat(firstRunSteps - 2) { tapText(R.string.first_run_next) }
        node(hasText(text(R.string.first_run_step, firstRunSteps, firstRunSteps))).assertExists()
        // Phone test 2026-10-26: a sample crashed the app. The sample book is saved and opened like shared text.
        tapText(R.string.sample_try)
        waitForNode(hasContentDescription(text(R.string.reader_play)), OPEN_TIMEOUT_MS)
        gone(stepOne)
        backToTabs()
        TourJourney(rule).isOfferedOnceAndPutOff()
    }

    /** The bundled voice's "Play a sample" on the voices step, heard to the end (the stop button goes away). */
    private fun playAVoiceSample() {
        val play = hasContentDescription(text(R.string.voices_sample_play, ""), substring = true) and hasClickAction()
        tap(play)
        val stop = hasContentDescription(text(R.string.voices_sample_stop, ""), substring = true)
        gone(stop, PLAY_TIMEOUT_MS)
        node(play).assertExists()
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

    /**
     * The buttons the notification and lock screen draw (CF-1), sent as Audora's notification controller sends them:
     * a controller from this package, like the one System UI's taps are routed through.
     */
    @OptIn(UnstableApi::class)
    private fun lockScreenButtonsGoBackOnAndBookmark() {
        val controller = MediaController.Builder(app, PlaybackContract.sessionToken(app)).buildAsync()
            .get(UI_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        try {
            var shown = emptyList<String?>()
            JourneyDevice.onMain { shown = controller.mediaButtonPreferences.map { it.sessionCommand?.customAction } }
            assertTrue("The lock screen's buttons were $shown", shown.containsAll(LockScreenCommands.ACTIONS))
            pause()
            awaitTrue("the next sentence to be ready", state = {
                "Player: ${snapshot()}"
            }) { snapshot().canSkipForward }
            val before = position()
            send(controller, LockScreenCommands.NEXT_SENTENCE)
            awaitTrue("next sentence from the lock screen", state = {
                "Player: ${snapshot()}"
            }) { position() != before }
            send(controller, LockScreenCommands.PREVIOUS_SENTENCE)
            awaitTrue("previous sentence from the lock screen", state = { "Player: ${snapshot()}" }) {
                position() == before
            }
            val bookmarks = JourneyDevice.logCount(LOCK_SCREEN_BOOKMARK)
            send(controller, LockScreenCommands.BOOKMARK)
            awaitLog(LOCK_SCREEN_BOOKMARK, bookmarks)
            assertFalse("A lock-screen button must not start a paused book", snapshot().wantsToPlay)
            JourneyDevice.onMain(requireNotNull(port)::play)
            awaitReadingMovesOn("play after the lock screen's buttons")
        } finally {
            JourneyDevice.onMain { controller.release() }
        }
    }

    private fun send(controller: MediaController, command: SessionCommand) {
        var result: ListenableFuture<SessionResult>? = null
        JourneyDevice.onMain { result = controller.sendCustomCommand(command, Bundle.EMPTY) }
        val code = requireNotNull(result).get(UI_TIMEOUT_MS, TimeUnit.MILLISECONDS).resultCode
        assertEquals("${command.customAction} answered $code", SessionResult.RESULT_SUCCESS, code)
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

    /** Edit details writes the book's language to the library; the playing book follows it, and back (phone test). */
    private fun aBookLanguageAppliesAtOnce() {
        val id = requireNotNull(position()?.bookId) { "No book playing" }
        val library = LibraryEntryPoint.of(app).library()
        val before = runBlocking { library.book(id) }?.language
        listOf(OTHER_BOOK_LANGUAGE, before).forEach { language ->
            val changes = JourneyDevice.logCount(LANGUAGE_RESTART)
            runBlocking { library.change(id) { it.copy(language = language, edited = it.edited + BookField.LANGUAGE) } }
            awaitLog(LANGUAGE_RESTART, changes)
            awaitReadingMovesOn("reading to go on in the book's language $language")
        }
    }

    private fun aRowTogglesFromItsTitle() {
        // While a book plays, its highlight recomposes every frame and Compose's test sync never goes idle, so the
        // screens are driven paused; the service acting on the setting and reading on afterwards is what is checked.
        pause()
        backToTabs()
        tapText(R.string.tab_you)
        val row = toggleRow(text(R.string.settings_skip_citations_title))
        val before = toggleState(row)
        val rereads = JourneyDevice.logCount(TEXT_REREAD)
        // The title, not the switch: the whole row is the target (ADR-062). Scrolled to first: rows above it (Voice
        // and speed) can leave it under the bottom bar on a small screen, where a tap lands on nothing.
        node(hasText(text(R.string.settings_skip_citations_title))).performScrollTo().performClick()
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
        node(hasContentDescription(text(R.string.option_help_about, title))).performScrollTo().performClick()
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
                rule.shows(hasText(sentence, substring = true))
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
            rule.shows(hasText(COMIC_TITLE))
        }
    }

    /**
     * DjVu through DjVuLibre (ADR 2026-10-27-djvu-books): one with a text layer opens as a text book showing its first
     * paragraph, joined across lines and a broken word; a scan (no text layer) becomes a photo book that opens by
     * itself, like a comic.
     */
    private fun openWithReadsDjvuBooks() {
        backToTabs()
        val textUri = JourneyDevice.downloadedBook(DJVU_TEXT)
        val textHost = activity()
        JourneyDevice.onMain { textHost.startActivity(JourneyDevice.openWith(textUri, DJVU_TYPE)) }
        awaitSynced("$DJVU_TEXT to show \"$DJVU_SENTENCE\" in the reader", OPEN_TIMEOUT_MS) {
            rule.shows(hasText(DJVU_SENTENCE, substring = true))
        }
        backToTabs()
        val added = JourneyDevice.logCount(PHOTO_BOOK_ADDED)
        val scanUri = JourneyDevice.downloadedBook(DJVU_SCAN)
        val scanHost = activity()
        JourneyDevice.onMain { scanHost.startActivity(JourneyDevice.openWith(scanUri, DJVU_TYPE)) }
        awaitSynced("the DjVu scan to be added as a photo book", OPEN_TIMEOUT_MS) {
            JourneyDevice.logCount(PHOTO_BOOK_ADDED) > added
        }
        awaitSynced("the DjVu scan to open in the reader", OPEN_TIMEOUT_MS) {
            rule.shows(hasText(DJVU_SCAN_TITLE))
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

    /**
     * The Hindi pack (Supertonic, OpenRAIL-M) asks the user to agree to its use rules first (ADR
     * 2026-10-27-licence-fixes); Cancel downloads nothing.
     */
    private fun supertonicRulesShowBeforeItsDownload() {
        pause()
        backToTabs()
        tapText(R.string.tab_you)
        node(hasText(text(R.string.settings_voices_title))).performScrollTo().performClick()
        tap(hasText(text(R.string.voice_pack_download)) and hasClickAction())
        val agree = hasText(text(R.string.use_terms_agree))
        waitForNode(agree)
        tapText(R.string.use_terms_cancel)
        gone(agree)
        val supertonic = runBlocking { VoiceLibrary.get(app).rows().first { it.isNotEmpty() } }
            .single { it.id.name == SUPERTONIC }
        assertEquals("Cancel must not start the download", VoiceStatus.NotDownloaded, supertonic.status)
        backToTabs()
    }

    /**
     * The export's tagging on the installed app: the library's own cover for a PDF the journey opened, made into the
     * JPEG the export embeds, ends up in a chapter file where any audiobook player reads it.
     */
    private fun anExportedChapterCarriesTheCover() {
        val entry = LibraryEntryPoint.of(app)
        val book = runBlocking { entry.library().books().first() }.first { it.format == DocumentFormat.PDF }
        assertTrue("the library cover to be drawn", runBlocking { entry.covers().cover(book) } != null)
        val cover = requireNotNull(CoverJpeg.of(CoverFolders(app.filesDir).shownFile(book))) { "No cover JPEG" }
        val folder = File(app.cacheDir, "journey-export").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            val sink = AacAudioSink(LocalChapterFiles(folder), 1, book = BookTags(book.title, book.author, cover))
            val writer = requireNotNull(sink.open(AudioChapter(1, 0, 0), EXPORT_RATE).getOrNull()) { "No encoder" }
            assertTrue(writer.write(PcmAudio(ShortArray(EXPORT_RATE / 2), EXPORT_RATE)) is Outcome.Success)
            val exported = requireNotNull(writer.finish().getOrNull()) { "Chapter not finished" }
            MediaMetadataRetriever().use { tags ->
                tags.setDataSource(File(folder, exported.name).path)
                assertEquals(book.title, tags.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM))
                assertTrue("no cover in the file", tags.embeddedPicture?.contentEquals(cover) == true)
            }
        } finally {
            folder.deleteRecursively()
        }
    }

    /**
     * The quote video's encoder in the installed app (ADR 2026-10-28-quote-video): the phone's H.264 and
     * AAC encoders write a 720 x 1280 MP4 with both tracks, the word lit by the voice's own timing.
     */
    private fun aQuoteVideoIsWritten() {
        val audio =
            PcmAudio(ShortArray(EXPORT_RATE * 3 / 2) { ((it % TONE_PERIOD) * TONE_STEP).toShort() }, EXPORT_RATE)
        val starts = requireNotNull(WordTimes().of(VIDEO_QUOTE, audio)) { "No word times" }
        val timeline = VideoTimeline(audio.samples.size * MS_PER_S / EXPORT_RATE, starts, QuoteVideoConfig())
        val words = VIDEO_QUOTE.split(' ')
        val frames = object : QuoteVideoFrames {
            private val ink = Paint().apply { textSize = FONT_TEXT_SIZE }

            override fun lookOf(frame: Int) = timeline.wordAt(frame)

            override fun draw(canvas: Canvas, frame: Int) {
                canvas.drawColor(Color.WHITE)
                val lit = timeline.wordAt(frame)
                canvas.drawText(words.getOrElse(lit) { "" }, FONT_TEXT_SIZE, canvas.height / 2f, ink)
            }
        }
        val file = File(app.cacheDir, "journey-quote.mp4")
        try {
            val written = QuoteVideoWriter().write(file, audio, timeline, frames, AiVoiceLabel("AI voice"))
            assertTrue("video not written: $written", written is Outcome.Success)
            MediaMetadataRetriever().use { video ->
                video.setDataSource(file.path)
                assertEquals("720", video.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH))
                assertEquals("1280", video.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT))
                assertEquals("yes", video.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
            }
        } finally {
            file.delete()
        }
    }

    /**
     * Each weight loads from the APK and each script is drawn by its own Anek file: the joined typeface measures
     * exactly like that file alone, and the three weights of a script measure differently, so no weight is drawn
     * with another's file. Not "unlike the phone's sans": two fonts can measure alike by chance.
     */
    private fun theAppFontDrawsAllThreeScripts() {
        // Without spaces: the joined typeface takes the space from the Latin file, the first in the chain, so a
        // spaced Hindi or Bengali line measures a little apart from its own file alone.
        val samples = listOf(FONT_ENGLISH, FONT_HINDI, FONT_BENGALI).map { it.replace(" ", "") }
        val widths = AppFont.Weight.entries.map { weight ->
            val anek = requireNotNull(AppFont.typeface(app, weight)) { "${weight.name} did not load" }
            weight.scripts.zip(samples).map { (file, sample) ->
                val drawn = widthOf(sample, anek)
                val alone = widthOf(sample, own(file, weight))
                assertEquals("${weight.name} draws '$sample' with its own file", alone, drawn, FONT_WIDTH_TOLERANCE)
                drawn
            }
        }
        samples.indices.forEach { script ->
            val byWeight = widths.map { it[script] }
            assertEquals("'${samples[script]}' at 400, 500 and 600: $byWeight", byWeight.size, byWeight.distinct().size)
        }
    }

    /** Library, Notes, Made and You from the bar, with the book paused (MadeJourney.fourPlacesAndJobs). */
    private fun theFourPlacesOpenAndJobsLiveInMade() {
        pause()
        MadeJourney(rule, ::backToTabs, ::back).fourPlacesAndJobs()
    }

    /**
     * A book's menu shows its big buttons and opens Save as in place; the sort sheet's List shows rows at once and
     * Reset brings the covers back. The emulator's screen is small, so the books can sit below Continue reading,
     * not yet composed: each check scrolls the library to what it looks for. Sheets close by their handle: Back
     * reaches the activity, not the sheet's window.
     */
    private fun bookMenuAndSortSheet() {
        // The words around the book's name, whichever side of it they sit in this language.
        val namedMenu = text(R.string.library_book_menu_named, NAME_MARK).split(NAME_MARK).maxBy { it.length }.trim()
        val menuButton = hasContentDescription(namedMenu, substring = true) and hasClickAction()
        scrollLibraryTo(menuButton)
        tap(menuButton)
        waitForNode(hasText(text(R.string.menu_play)))
        tapText(R.string.menu_save_as)
        waitForNode(hasText(text(R.string.menu_save_audiobook)))
        tap(sheetHandle())
        gone(hasText(text(R.string.menu_play)))

        val openBook = text(R.string.a11y_open_book)
        val listRow = SemanticsMatcher("a list row") { node ->
            node.config.getOrNull(SemanticsActions.OnClick)?.label == openBook
        }
        val coverTile = SemanticsMatcher("a cover tile") { node ->
            node.config.getOrNull(SemanticsActions.OnLongClick)?.label?.contains(namedMenu) == true &&
                node.config.getOrNull(SemanticsActions.OnClick)?.label != openBook
        }
        tap(hasContentDescription(text(R.string.library_sort_filter)))
        tapText(R.string.library_layout_list)
        scrollLibraryTo(listRow)
        tapText(R.string.library_sort_reset)
        scrollLibraryTo(coverTile)
        tap(sheetHandle())
        gone(hasText(text(R.string.library_sort_reset)))
    }

    /** The sheet's own handle: Material's scrim behind every sheet carries the same name and fills the screen. */
    private fun sheetHandle(): SemanticsMatcher = hasContentDescription(text(R.string.a11y_sheet_close)) and
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    /** Scrolls the library's grid or list until [matcher]'s node is composed and on screen; fails if it has none. */
    private fun scrollLibraryTo(matcher: SemanticsMatcher) {
        rule.waitForIdle()
        node(hasScrollToIndexAction()).performScrollToNode(matcher)
    }

    /** One Anek file on its own, at the weight it was trimmed to. */
    private fun own(file: Int, weight: AppFont.Weight): Typeface = Typeface.CustomFallbackBuilder(
        PlatformFontFamily.Builder(Font.Builder(app.resources, file).build()).build(),
    ).setStyle(FontStyle(weight.weight.weight, FontStyle.FONT_SLANT_UPRIGHT)).build()

    /** Subpixel and unhinted, so widths are exact sums of the font's advances, not whole pixels. */
    private fun widthOf(text: String, face: Typeface): Float = Paint(EXACT_WIDTHS).apply {
        typeface = face
        textSize = FONT_TEXT_SIZE
    }.measureText(text)

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

    /**
     * "Share as a picture" on the installed app (ADR 2026-10-27-share-cards): the card screen opens from the reader's
     * call with the journey's PDF, Share writes a 1080 x 1920 PNG into the share folder and hands it to the share
     * sheet, and Back returns to the app.
     */
    private fun aQuoteCardIsShared() {
        val entry = LibraryEntryPoint.of(app)
        val book = runBlocking { entry.library().books().first() }.first { it.format == DocumentFormat.PDF }
        val folder = File(app.cacheDir, ShareCardFiles.FOLDER).apply { deleteRecursively() }
        val reader = activity()
        JourneyDevice.onMain { ShareEntry.quote(reader, book.id, CARD_QUOTE, page = 0) }
        waitForNode(hasText(text(R.string.share_card_title)), OPEN_TIMEOUT_MS)
        node(hasText(text(R.string.share_card_send)) and hasClickAction()).performScrollTo().performClick()
        val card = { folder.listFiles()?.firstOrNull { it.name.endsWith(".png") } }
        awaitTrue("the card in the share folder", state = { "Files: ${folder.list()?.toList()}" }) { card() != null }
        val size = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(requireNotNull(card()).path, size)
        assertEquals(CARD_WIDTH, size.outWidth)
        assertEquals(CARD_HEIGHT, size.outHeight)
        // The share sheet (another app's window) covers the card screen; Back closes it, then the card screen.
        awaitTrue("the share sheet") { JourneyDevice.resumedActivity() !is ShareCardActivity }
        repeat(MAX_BACKS) {
            if (JourneyDevice.resumedActivity() is MainActivity) return@repeat
            JourneyDevice.pressBackKey()
            Thread.sleep(POLL_MS)
        }
        awaitTrue("the reader back on screen") { JourneyDevice.resumedActivity() is MainActivity }
    }

    /**
     * The test book opened again (Open with) and paused; a sideways swipe on its page turns it (ADR
     * 2026-10-28-page-flip): back a page if there is one, else on to the next.
     */
    private fun aSwipeTurnsThePdfPage() {
        pause()
        val uri = JourneyDevice.downloadedPdf(BOOK)
        val current = activity()
        JourneyDevice.onMain { current.startActivity(JourneyDevice.openWith(uri)) }
        val pageImage = { page: Int -> hasContentDescription(text(R.string.reader_page_image, page)) }
        val onScreen = { page: Int -> rule.shows(pageImage(page)) }
        var shown: Int? = null
        awaitTrue("a drawn page on screen") {
            shown = (1..MAX_SCANNED_PAGES).firstOrNull(onScreen)
            shown != null
        }
        val from = requireNotNull(shown)
        val to = if (from > 1) from - 1 else from + 1
        node(pageImage(from)).performTouchInput { if (to < from) swipeRight() else swipeLeft() }
        waitForNode(pageImage(to), OPEN_TIMEOUT_MS)
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

    /**
     * The emulator is a phone: the reader (still open from step 22) has no side panel and its Bookmarks button opens
     * the sheet; the library has no book pane (ADR 2026-10-28-tablet-panes).
     */
    private fun aPhoneKeepsTheSheets() {
        waitForNode(hasContentDescription(text(R.string.bookmarks_title)) and hasClickAction(), OPEN_TIMEOUT_MS)
        assertTrue("no side panel on a phone", none(hasTestTag(SIDE_PANEL_TAG)))
        tap(hasContentDescription(text(R.string.bookmarks_title)) and hasClickAction())
        waitForNode(hasText(text(R.string.bookmarks_add_here)))
        tap(sheetHandle())
        gone(hasText(text(R.string.bookmarks_add_here)))
        backToTabs()
        tapText(R.string.tab_library)
        waitForNode(hasText(text(R.string.library_title)))
        assertTrue("no book pane on a phone", none(hasTestTag(BOOK_PANE_TAG)))
    }

    private fun none(matcher: SemanticsMatcher): Boolean = rule.onAllNodes(matcher).fetchSemanticsNodes().isEmpty()

    private companion object {
        const val SUPERTONIC = "supertonic-hi"
        const val LOG_TAG = "ReleaseJourney"
        const val BOOK = "en-udhr-noto.pdf"

        /** Pages looked through for the one on screen: more than the test book has. */
        const val MAX_SCANNED_PAGES = 40
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

        // assets/ebooks, written by scripts/fixtures/make_djvu_fixtures.py: its first paragraph has "remark-" / "able"
        // across a line end, so the sentence shows only when the lines are joined.
        const val DJVU_TEXT = "text.djvu"
        const val DJVU_SCAN = "scan.djvu"
        const val DJVU_TYPE = "image/vnd.djvu"
        const val DJVU_SENTENCE = "The first page of a small DjVu book has a remarkable sentence"
        const val DJVU_SCAN_TITLE = "scan"

        // PhotoBooks' own words once the photo book is in the library.
        const val PHOTO_BOOK_ADDED = "Photo book added"
        const val LOG_TAIL = 30
        val firstRunSteps = FirstRunStep.entries.size
        const val MAX_BACKS = 4
        const val POLL_MS = 200L
        const val HOLD_MS = 2_000L
        const val PERMISSION_PROMPT_MS = 5_000L
        const val UI_TIMEOUT_MS = 15_000L
        const val NAME_MARK = "\u0001"
        const val OPEN_TIMEOUT_MS = 60_000L
        const val PLAY_TIMEOUT_MS = 90_000L
        const val OCR_DOWNLOAD_TIMEOUT_MS = 360_000L

        // The service's own words when it acts on a change (AudoraPlaybackService.followReadingChanges).
        const val VOICE_RESTART = "Reading voices changed; restarting the sentence"
        const val LANGUAGE_RESTART = "Book language changed"
        const val EXPORT_RATE = 22_050
        const val VIDEO_QUOTE = "Every book, read aloud."
        const val TONE_PERIOD = 50
        const val TONE_STEP = 300
        const val MS_PER_S = 1_000L
        const val FONT_TEXT_SIZE = 48f
        const val FONT_WIDTH_TOLERANCE = 0.01f
        const val EXACT_WIDTHS = Paint.LINEAR_TEXT_FLAG or Paint.SUBPIXEL_TEXT_FLAG
        const val FONT_ENGLISH = "Listen to every book"
        const val FONT_HINDI = "हर किताब सुनिए"
        const val FONT_BENGALI = "প্রতিটি বই শুনুন"
        const val OTHER_BOOK_LANGUAGE = "fr"
        const val TEXT_REREAD = "Reading text settings changed; reading the open book again"
        const val LOCK_SCREEN_BOOKMARK = "Lock screen bookmark added"

        // Step 19: a line of the journey's PDF, made into a story-sized card.
        const val CARD_QUOTE = "All human beings are born free and equal in dignity and rights."
        const val CARD_WIDTH = 1080
        const val CARD_HEIGHT = 1920

        // Step 24b: a word that names a setting on Advanced, not a book.
    }
}
