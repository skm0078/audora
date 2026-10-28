// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/firstrun/GuidedTourScreensTest.kt
// Purpose: The 1-minute tour (ADR 2026-10-28-home-and-tour) on a phone-sized screen: the offer sheet starts it, each
//          stop's card sits beside what it points at and says which stop it is, Back steps back, Done on the last
//          closes it, and the stops read in Bengali at large text. Screenshots for review in CI's "player-screens"
//          artifact.

package io.github.skm0078.audora.ui.firstrun

import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.Locales
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.then
import androidx.compose.ui.text.intl.LocaleList as ComposeLocales
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skm0078.audora.R
import io.github.skm0078.audora.ui.AppTab
import io.github.skm0078.audora.ui.Screens
import io.github.skm0078.audora.ui.shell.AppShell
import io.github.skm0078.audora.ui.theme.AudoraTheme
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GuidedTourScreensTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var offered = 0

    @Test
    fun theOfferStartsTheTourAndDoneOnTheLastStopClosesIt() {
        val tour = GuidedTour(mutableIntStateOf(GuidedTour.OFFER)) { offered++ }
        rule.setContent { Phone { Frame(tour) } }

        rule.onNodeWithText(text(R.string.tour_offer_title), substring = true).assertExists()
        Screens.save("tour-offer", rule.onAllNodes(isRoot()).onLast().captureToImage().asAndroidBitmap())
        rule.onNodeWithText(text(R.string.tour_start)).performClick()
        rule.waitForIdle()

        rule.onNodeWithTag(TOUR_TAG).assertExists()
        rule.onNodeWithText(step(1)).assertExists()
        rule.onNodeWithText(text(R.string.tour_listen_title)).assertExists()
        Screens.save("tour-stop-listen", rule.onNodeWithTag(TOUR_TAG).captureToImage().asAndroidBitmap())
        repeat(TourStop.entries.size - 1) {
            rule.onNodeWithText(text(R.string.tour_next)).performClick()
            rule.waitForIdle()
        }
        rule.onNodeWithText(step(TourStop.entries.size)).assertExists()
        rule.onNodeWithText(text(R.string.tour_you_title)).assertExists()
        rule.onNodeWithText(text(R.string.tour_skip)).assertDoesNotExist()
        Screens.save("tour-stop-you", rule.onNodeWithTag(TOUR_TAG).captureToImage().asAndroidBitmap())

        rule.onNodeWithText(text(R.string.tour_done)).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(TOUR_TAG).assertDoesNotExist()
        assertEquals(1, offered)
    }

    @Test
    fun theListenStopsCardSitsUnderThePlayItPointsAt() {
        val tour = GuidedTour(mutableIntStateOf(0)) { offered++ }
        rule.setContent { Phone { Frame(tour) } }
        rule.waitForIdle()

        val play = rule.onNodeWithTag(PLAY_TAG).getUnclippedBoundsInRoot()
        val card = rule.onNodeWithText(step(1)).getUnclippedBoundsInRoot()
        assertTrue("card at ${card.top}, Play ends at ${play.bottom}", card.top > play.bottom)
    }

    @Test
    fun backStepsToTheStopBeforeAndClosesFromTheFirst() {
        val tour = GuidedTour(mutableIntStateOf(1)) { offered++ }
        rule.setContent { Phone { Frame(tour) } }
        rule.onNodeWithText(step(2)).assertExists()

        back()
        rule.onNodeWithText(step(1)).assertExists()
        back()
        rule.onNodeWithTag(TOUR_TAG).assertDoesNotExist()
    }

    @Test
    fun aStopReadsInBengaliAtLargeText() {
        val tour = GuidedTour(mutableIntStateOf(TourStop.NOTES.ordinal)) { offered++ }
        rule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.Locales(ComposeLocales("bn")) then
                    DeviceConfigurationOverride.FontScale(LARGE_TEXT),
            ) { Phone { Frame(tour) } }
        }
        rule.onNodeWithText(text(R.string.tour_notes_title, BENGALI)).assertExists()
        Screens.save("tour-stop-notes-bn-large", rule.onNodeWithTag(TOUR_TAG).captureToImage().asAndroidBitmap())
    }

    private fun back() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    private fun step(n: Int) = context.getString(R.string.tour_step, n, TourStop.entries.size)

    private fun text(id: Int, locale: Locale? = null): String {
        if (locale == null) return context.getString(id)
        val config = android.content.res.Configuration(context.resources.configuration)
        config.setLocales(LocaleList(locale))
        return context.createConfigurationContext(config).getString(id)
    }

    /** The places' frame with a Play button and a book cover for the first two stops to point at. */
    @Composable
    private fun Frame(tour: GuidedTour) {
        val targets = remember { TourTargets() }
        Box(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalTourTargets provides targets) {
                AppShell(selected = AppTab.LIBRARY, onSelect = {}) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(onClick = {}, Modifier.tourTarget(TourTarget.PLAY).testTag(PLAY_TAG)) { Text("Play") }
                        Box(
                            Modifier
                                .size(104.dp, 139.dp)
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .tourTarget(TourTarget.BOOK),
                        )
                    }
                }
            }
            GuidedTourOverlay(tour, targets, firstBook = true)
        }
    }

    @Composable
    private fun Phone(content: @Composable () -> Unit) {
        DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(TEST_PHONE_SIZE)) {
            AudoraTheme { content() }
        }
    }

    private companion object {
        val TEST_PHONE_SIZE = DpSize(411.dp, 914.dp)
        val BENGALI: Locale = Locale.forLanguageTag("bn")
        const val LARGE_TEXT = 1.5f
        const val PLAY_TAG = "play"
    }
}
