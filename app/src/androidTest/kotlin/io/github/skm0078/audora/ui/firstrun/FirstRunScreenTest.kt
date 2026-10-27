// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/firstrun/FirstRunScreenTest.kt
// Purpose: The first-run screens on a device: Next and the system back step through them, Skip and Not now finish,
//          and the background step follows Android's answer (Optimized is not done; Unrestricted is).

package io.github.skm0078.audora.ui.firstrun

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.battery.BackgroundAccess
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.speech.voices.VoiceRow
import io.github.skm0078.audora.ui.theme.AudoraTheme
import io.github.skm0078.audora.ui.voices.VoicesActions
import io.github.skm0078.audora.ui.voices.VoicesState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirstRunScreenTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var finished = false
    private var access by mutableStateOf(BackgroundAccess.RESTRICTED)
    private var allowTaps = 0
    private val steps = FirstRunStep.entries.size

    private fun text(id: Int, vararg args: Any) = rule.activity.getString(id, *args)

    private fun assertStep(number: Int) =
        rule.onNodeWithText(text(R.string.first_run_step, number, steps)).assertIsDisplayed()

    private fun next() = rule.onNodeWithText(text(R.string.first_run_next)).performClick()

    @Before
    fun show() = rule.setContent {
        AudoraTheme {
            FirstRunScreen(
                voices = VoicesState(rows = emptyList()),
                voiceActions = NoVoiceActions,
                backgroundAccess = access,
                onAllowBackground = { allowTaps++ },
                onAddPdf = {},
                onFinish = { finished = true },
            )
        }
    }

    @Test
    fun nextAndSystemBackStepThrough() {
        assertStep(1)
        next()
        assertStep(2)
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        assertStep(1)
        repeat(steps - 1) { next() }
        assertStep(steps)
        rule.onNodeWithText(text(R.string.first_run_later)).performClick()
        rule.runOnIdle { assertTrue(finished) }
    }

    /** The phone-test bug: background usage switched on, back in the app, and the step still said "Allow". */
    @Test
    fun backgroundStepFollowsAndroidsAnswer() {
        repeat(FirstRunStep.BACKGROUND.ordinal) { next() }
        rule.onNodeWithText(text(R.string.first_run_background_allow)).performClick()
        access = BackgroundAccess.OPTIMIZED // as the resume check finds it after "Allow background usage"
        rule.onNodeWithText(text(R.string.battery_status_optimized)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.battery_choose_unrestricted)).performClick()
        access = BackgroundAccess.UNRESTRICTED
        rule.onNodeWithText(text(R.string.first_run_background_done)).assertIsDisplayed()
        rule.runOnIdle { assertEquals(2, allowTaps) }
    }

    @Test
    fun skipFinishesAtOnce() {
        rule.onNodeWithText(text(R.string.first_run_skip)).performClick()
        rule.runOnIdle { assertTrue(finished) }
    }

    private object NoVoiceActions : VoicesActions {
        override fun onBack() = Unit

        override fun onDownload(row: VoiceRow) = Unit

        override fun onCancel(row: VoiceRow) = Unit

        override fun onUse(row: VoiceRow) = Unit

        override fun onUseDefault(lang: Lang) = Unit

        override fun onRequestDelete(row: VoiceRow) = Unit

        override fun onConfirmDelete() = Unit

        override fun onCancelDelete() = Unit

        override fun onImport(treeUri: Uri, lang: Lang, label: String) = Unit

        override fun onMessageShown() = Unit
    }
}
