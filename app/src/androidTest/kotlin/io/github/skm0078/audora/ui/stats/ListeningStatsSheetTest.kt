// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/stats/ListeningStatsSheetTest.kt
// Purpose: The Listening stats sheet (CF-8) on a phone-sized screen: its numbers, the empty state, Clear asking
//          first, and screenshots of both for review in CI's "player-screens" artifact.

package io.github.skm0078.audora.ui.stats

import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.stats.ListeningSummary
import io.github.skm0078.audora.ui.Screens
import io.github.skm0078.audora.ui.theme.AudoraTheme
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListeningStatsSheetTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var cleared = 0

    private fun show(summary: ListeningSummary, finished: Int = 0) {
        rule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(TEST_PHONE_SIZE)) {
                AudoraTheme { ListeningStatsSheet(summary, finished, onClear = { cleared++ }, onClose = {}) }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun theSheetShowsTimeStreaksAndBooksFinished() {
        val days = mapOf(
            TODAY to MIN * 25,
            TODAY.minusDays(1) to MIN * 70,
            TODAY.minusDays(2) to MIN * 10,
            TODAY.minusDays(9) to MIN * 5,
        )
        show(ListeningSummary.of(days, TODAY, DayOfWeek.SUNDAY), finished = 2)

        rule.onNodeWithText(context.getString(R.string.stats_minutes, 25)).assertExists()
        // Sunday 25 to Monday 26 October: 25 min today and 1 h 10 min yesterday.
        rule.onNodeWithText(context.getString(R.string.stats_hours_minutes, 1, 35)).assertExists()
        rule.onAllNodes(hasText(context.resources.getQuantityString(R.plurals.stats_days, 3, 3))).onFirst()
            .assertExists()
        rule.onNodeWithText("2").assertExists()
        Screens.save("listening-stats", rule.onAllNodes(isRoot()).onLast().captureToImage().asAndroidBitmap())

        rule.onNodeWithText(context.getString(R.string.stats_clear)).performScrollTo().performClick()
        rule.onNodeWithText(context.getString(R.string.stats_clear_body)).assertExists()
        rule.onNode(hasText(context.getString(R.string.stats_clear_confirm)) and hasAnyAncestor(isDialog()))
            .performClick()
        rule.waitForIdle()
        assertEquals(1, cleared)
    }

    @Test
    fun nothingListenedYetSaysHowItWorksAndOffersNoClear() {
        show(ListeningSummary.of(emptyMap(), TODAY, DayOfWeek.MONDAY))

        rule.onNodeWithText(context.getString(R.string.stats_empty)).assertExists()
        rule.onNodeWithText(context.getString(R.string.stats_private)).assertExists()
        rule.onNodeWithText(context.getString(R.string.stats_clear)).assertDoesNotExist()
        Screens.save("listening-stats-empty", rule.onAllNodes(isRoot()).onLast().captureToImage().asAndroidBitmap())
    }

    private companion object {
        val TEST_PHONE_SIZE = DpSize(411.dp, 914.dp)
        val TODAY: LocalDate = LocalDate.of(2026, 10, 26)
        const val MIN = 60_000L
    }
}
