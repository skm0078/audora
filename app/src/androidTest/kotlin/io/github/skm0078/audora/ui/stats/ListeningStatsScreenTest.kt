// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/stats/ListeningStatsScreenTest.kt
// Purpose: The stats screen and its share card on a phone-sized screen (ADR 2026-10-28-listening-stats-screen): the
//          tabs, the goal, Clear asking first, the empty state, large Bengali text, and screenshots of each for
//          review in CI's "player-screens" artifact.

package io.github.skm0078.audora.ui.stats

import android.content.Context
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.Locales
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.then
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.resume.ResumeAnchor
import io.github.skm0078.audora.core.stats.Heard
import io.github.skm0078.audora.core.stats.ListeningPeriod
import io.github.skm0078.audora.core.stats.ListeningSlot
import io.github.skm0078.audora.ui.Screens
import io.github.skm0078.audora.ui.library.CoverSource
import io.github.skm0078.audora.ui.share.CardShape
import io.github.skm0078.audora.ui.share.rememberCardRecorder
import io.github.skm0078.audora.ui.theme.AudoraTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListeningStatsScreenTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val goals = mutableListOf<Int>()
    private var cleared = 0
    private var shares = 0

    private val actions = object : StatsScreenActions {
        override fun onClose() = Unit

        override fun onPeriod(chosen: ListeningPeriod) {
            period = chosen
        }

        override fun onGoal(weeklyHours: Int) {
            goals += weeklyHours
        }

        override fun onShare() {
            shares++
        }

        override fun onClear() {
            cleared++
        }
    }
    private var period by mutableStateOf(ListeningPeriod.WEEK)

    private fun show(slots: Map<ListeningSlot, Heard>, fontScale: Float = 1f, language: String = "en") {
        val all = StatsAllPeriods.of(inputs(slots))
        rule.setContent {
            val config = DeviceConfigurationOverride.ForcedSize(PHONE) then
                DeviceConfigurationOverride.FontScale(fontScale) then
                DeviceConfigurationOverride.Locales(LocaleList(language))
            DeviceConfigurationOverride(config) {
                AudoraTheme {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        ListeningStatsScreen(all, period, weeklyGoalHours = 5, CoverSource.None, actions)
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun text(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun screenshot(name: String) =
        Screens.save(name, rule.onAllNodes(isRoot()).onLast().captureToImage().asAndroidBitmap())

    private fun scrollTo(id: Int) = rule.onNode(hasScrollAction()).performScrollToNode(hasText(text(id)))

    @Test
    fun theWeekShowsItsTotalBarsNumbersAndTheRestBelow() {
        show(sample())

        rule.onNodeWithText(text(R.string.stats_total_week, text(R.string.stats_hours_minutes, 1, 50))).assertExists()
        rule.onNodeWithText(text(R.string.stats_streak_caption, 3)).assertExists()
        screenshot("listening-stats-week")
        scrollTo(R.string.stats_languages)
        scrollTo(R.string.stats_most_listened)
        rule.onNodeWithText(text(R.string.stats_removed_book)).assertExists()
        scrollTo(R.string.stats_milestones)
        screenshot("listening-stats-more")
    }

    @Test
    fun theMonthTabShowsTheMonthAndTheGoalCanChange() {
        show(sample())

        rule.onNode(hasText(text(R.string.stats_period_month)) and hasClickAction()).performClick()
        rule.waitForIdle()
        assertEquals(ListeningPeriod.MONTH, period)
        rule.onNodeWithText(text(R.string.stats_total_in, text(R.string.stats_hours_minutes, 2, 30), "October"))
            .assertExists()
        screenshot("listening-stats-month")

        val goal = text(R.string.stats_goal, context.resources.getQuantityString(R.plurals.stats_goal_hours, 5, 5))
        rule.onNodeWithText(goal).performClick()
        rule.onNode(hasText(context.resources.getQuantityString(R.plurals.stats_goal_hours, 8, 8))).performClick()
        rule.waitForIdle()
        assertEquals(listOf(8), goals)
    }

    @Test
    fun clearAsksFirstAndShareIsOffered() {
        show(sample())

        rule.onNode(hasText(text(R.string.stats_share)) and hasClickAction()).performClick()
        assertEquals(1, shares)
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(text(R.string.stats_clear)))
        rule.onNodeWithText(text(R.string.stats_clear)).performClick()
        rule.onNodeWithText(text(R.string.stats_clear_body)).assertExists()
        rule.onNode(hasText(text(R.string.stats_clear_confirm)) and hasAnyAncestor(isDialog())).performClick()
        rule.waitForIdle()
        assertEquals(1, cleared)
    }

    @Test
    fun nothingListenedYetSaysHowStatsStartAndOffersNoShareOrClear() {
        show(emptyMap())

        rule.onNodeWithText(text(R.string.stats_empty)).assertExists()
        rule.onNodeWithText(text(R.string.stats_clear)).assertDoesNotExist()
        rule.onNode(hasText(text(R.string.stats_share)) and hasClickAction()).performClick()
        assertEquals("Share is off with nothing to share", 0, shares)
        screenshot("listening-stats-empty")
    }

    @Test
    fun largeBengaliTextStillReachesEveryCard() {
        show(sample(), fontScale = LARGE_FONT, language = "bn")

        screenshot("listening-stats-bn-large")
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(context.bengali(R.string.stats_milestones)))
        screenshot("listening-stats-bn-large-more")
    }

    @Test
    fun theShareCardDrawsEachKindAndColour() {
        val all = StatsAllPeriods.of(inputs(sample()))
        var choices by mutableStateOf(StatsCardChoices())
        rule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(PHONE)) {
                AudoraTheme {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        StatsShareScreen(
                            all,
                            choices,
                            busy = false,
                            rememberCardRecorder(),
                            CoverSource.None,
                            shareActions,
                        )
                    }
                }
            }
        }
        StatsCardKind.entries.zip(StatsCardLook.entries).forEach { (kind, look) ->
            choices = StatsCardChoices(kind, look)
            rule.waitForIdle()
            screenshot("stats-card-${kind.name.lowercase()}-${look.name.lowercase()}")
        }
        choices =
            StatsCardChoices(
                StatsCardKind.MONTH,
                StatsCardLook.PAPER,
                CardShape.SQUARE,
            )
        rule.waitForIdle()
        rule.onAllNodes(hasText(text(R.string.stats_save))).onFirst().assertExists()
        screenshot("stats-card-month-paper-square")
    }

    private val shareActions = object : StatsShareActions {
        override fun onBack() = Unit

        override fun onChoices(next: StatsCardChoices) = Unit

        override fun onSave() = Unit

        override fun onShare() = Unit
    }

    private fun Context.bengali(id: Int): String {
        val config = Configuration(resources.configuration).apply {
            setLocale(Locale.forLanguageTag("bn"))
        }
        return createConfigurationContext(config).getString(id)
    }

    /**
     * Wednesday 28 October 2026: 1 h 50 min this week over three days in a row (Monday to today), 40 min earlier in
     * October, in three languages, from two books in the library and one since removed, mostly in the evening.
     */
    private fun sample(): Map<ListeningSlot, Heard> = mapOf(
        ListeningSlot(TODAY, 20, "a", Lang.EN) to Heard(MIN * 50, 7_000),
        ListeningSlot(TODAY.minusDays(1), 21, "b", Lang.BN) to Heard(MIN * 40, 5_000),
        ListeningSlot(TODAY.minusDays(2), 8, "gone", Lang.HI) to Heard(MIN * 20, 2_000),
        ListeningSlot(LocalDate.of(2026, 10, 5), 19, "a", Lang.EN) to Heard(MIN * 40, 4_000),
    )

    private fun inputs(slots: Map<ListeningSlot, Heard>) = StatsInputs(
        slots = slots,
        books = listOf(book("a", "Pather Panchali"), book("b", "Godaan")),
        anchors = listOf(ResumeAnchor(BookId("a"), 9, 0, 1L, 1L, 1, FINISHED_AT)),
        madeAudiobook = true,
        weeklyGoalHours = 5,
        today = TODAY,
        zone = ZONE,
        firstDayOfWeek = DayOfWeek.MONDAY,
    )

    private fun book(id: String, title: String) =
        Book(BookId(id), "content://test/$id", title, 1_000, "f-$id", 10, 1, null)

    private companion object {
        val PHONE = DpSize(411.dp, 914.dp)
        val TODAY: LocalDate = LocalDate.of(2026, 10, 28)
        val ZONE: ZoneId = ZoneId.of("Asia/Kolkata")
        val FINISHED_AT = TODAY.atStartOfDay(ZONE).toInstant().toEpochMilli()
        const val MIN = 60_000L
        const val LARGE_FONT = 1.6f
    }
}
