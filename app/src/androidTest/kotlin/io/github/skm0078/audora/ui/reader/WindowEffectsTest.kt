// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/reader/WindowEffectsTest.kt
// Purpose: Keeping the screen on and the volume-key skips while two book screens overlap in a screen change.

package io.github.skm0078.audora.ui.reader

import android.view.KeyEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WindowEffectsTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    /** The leaving screen and the arriving one, both composed during a screen change. */
    private var leaving by mutableStateOf(true)
    private var arriving by mutableStateOf(true)

    @Test
    fun theLeavingScreenLettingGoKeepsTheScreenOnForTheArrivingOne() {
        lateinit var view: View
        rule.setContent {
            view = LocalView.current
            if (leaving) KeepScreenOn(active = true)
            if (arriving) KeepScreenOn(active = true)
        }
        rule.runOnIdle { assertTrue(view.keepScreenOn) }
        rule.runOnIdle { leaving = false }
        rule.runOnIdle { assertTrue(view.keepScreenOn) }
        rule.runOnIdle { arriving = false }
        rule.runOnIdle { assertFalse(view.keepScreenOn) }
    }

    @Test
    fun theVolumeKeysGoToTheArrivingScreenAndBackToTheVolumeAtTheEnd() {
        var leavingSkips = 0
        var arrivingSkips = 0
        val original = rule.activity.window.callback
        rule.setContent {
            if (leaving) VolumeKeySkips(active = true, onNext = { leavingSkips++ }, onPrevious = {})
            if (arriving) VolumeKeySkips(active = true, onNext = { arrivingSkips++ }, onPrevious = {})
        }
        rule.runOnIdle { leaving = false }
        rule.runOnIdle {
            val callback = rule.activity.window.callback
            callback.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN))
            callback.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_DOWN))
        }
        rule.runOnIdle {
            assertEquals(0, leavingSkips)
            assertEquals(1, arrivingSkips)
        }
        rule.runOnIdle { arriving = false }
        rule.runOnIdle { assertSame(original, rule.activity.window.callback) }
    }
}
