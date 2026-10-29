// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/stats/ThisWeekCardTest.kt
// Purpose: The home's "This week" card: the time against the goal with the ring, the time alone with no goal, the
//          streak only once there is one, and a tap (with its TalkBack label) opening the listening stats.

package io.github.skm0078.audora.ui.stats

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.stats.WeekGlance
import io.github.skm0078.audora.ui.theme.AudoraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThisWeekCardTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var opened = 0

    private fun text(id: Int, vararg args: Any) = rule.activity.getString(id, *args)

    private fun show(week: WeekGlance) {
        rule.setContent { AudoraTheme { ThisWeekCard(week) { opened++ } } }
    }

    @Test
    fun theWeekIsShownAgainstTheGoalWithTheStreak() {
        show(WeekGlance(ms = 190 * MINUTE, goalMs = 300 * MINUTE, streak = 4))
        val heard = text(R.string.stats_hours_minutes, 3, 10)
        rule.onNode(hasText(text(R.string.home_week_of_goal, heard, text(R.string.stats_hours, 5)))).assertExists()
        rule.onNode(hasContentDescription(text(R.string.stats_goal_progress, 63))).assertExists()
        val streak = rule.activity.resources.getQuantityString(R.plurals.home_week_streak, 4, 4)
        rule.onNode(hasText(streak)).assertExists()
    }

    @Test
    fun withNoGoalThereIsNoRingAndNoStreakUntilOne() {
        show(WeekGlance(ms = 45 * MINUTE, goalMs = null, streak = 0))
        rule.onNode(hasText(text(R.string.stats_total_week, text(R.string.stats_minutes, 45)))).assertExists()
        rule.onNode(
            hasContentDescription(text(R.string.stats_goal_progress, 0).removePrefix("0"), substring = true),
        ).assertDoesNotExist()
        val streak = rule.activity.resources.getQuantityString(R.plurals.home_week_streak, 1, 1)
        rule.onNode(hasText(streak)).assertDoesNotExist()
    }

    @Test
    fun aTapOpensTheListeningAndTalkBackHearsWhere() {
        show(WeekGlance(ms = 0, goalMs = null, streak = 0))
        val label = text(R.string.home_week_open)
        val labelled = SemanticsMatcher("click labelled $label") {
            it.config.getOrNull(SemanticsActions.OnClick)?.label == label
        }
        rule.onNode(labelled).performClick()
        assertEquals(1, opened)
    }

    private companion object {
        const val MINUTE = 60_000L
    }
}
