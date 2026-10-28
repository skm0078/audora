// File: app/src/androidTest/kotlin/io/github/skm0078/audora/privacy/LockSaverTest.kt
// Purpose: The app lock's screen saver (ADR 2026-10-28-lock-screen-saver): Pause only while reading, Unlock always,
//          reachable at large text and lying sideways, with screenshots in CI's "player-screens" artifact.

package io.github.skm0078.audora.privacy

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.Locales
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.then
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skm0078.audora.R
import io.github.skm0078.audora.ui.Screens
import io.github.skm0078.audora.ui.theme.AudoraTheme
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LockSaverTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var pauses = 0
    private var unlocks = 0

    private fun show(reading: Boolean, size: DpSize = PHONE, fontScale: Float = 1f, language: String = "en") {
        rule.setContent {
            val config = DeviceConfigurationOverride.ForcedSize(size) then
                DeviceConfigurationOverride.FontScale(fontScale) then
                DeviceConfigurationOverride.Locales(LocaleList(language))
            DeviceConfigurationOverride(config) {
                AudoraTheme { LockSaver(reading, onPause = { pauses++ }, onUnlock = { unlocks++ }) }
            }
        }
        rule.waitForIdle()
    }

    private fun text(id: Int, language: String = "en"): String {
        val config = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        return context.createConfigurationContext(config).getString(id)
    }

    private fun unlockIn(language: String) = hasText(text(R.string.app_lock_unlock, language)) and hasClickAction()

    private val unlock get() = unlockIn("en")
    private val pause get() = hasContentDescription(text(R.string.lock_saver_pause)) and hasClickAction()

    @Test
    fun whileReadingItSaysSoAndPausePauses() {
        show(reading = true)

        rule.onNode(hasText(text(R.string.lock_saver_reading))).assertExists()
        rule.onNode(hasText(text(R.string.lock_saver_hidden))).assertExists()
        Screens.save("lock-saver-reading", rule.onRoot().captureToImage().asAndroidBitmap())
        rule.onNode(pause).performClick()
        rule.onNode(unlock).performClick()
        assertEquals(1, pauses)
        assertEquals(1, unlocks)
    }

    @Test
    fun whenNotReadingItShowsTheLockedTitleAndNoPause() {
        show(reading = false)

        rule.onNode(hasText(text(R.string.app_lock_locked_title))).assertExists()
        rule.onNode(hasText(text(R.string.lock_saver_reading))).assertDoesNotExist()
        rule.onNode(pause).assertDoesNotExist()
        Screens.save("lock-saver-locked", rule.onRoot().captureToImage().asAndroidBitmap())
        rule.onNode(unlock).performClick()
        assertEquals(1, unlocks)
    }

    /** Bengali at twice the text size, and a phone lying on its side: Unlock is scrolled to, never cut off. */
    @Test
    fun unlockIsReachableAtLargeBengaliText() {
        show(reading = true, fontScale = LARGE_TEXT, language = "bn")
        Screens.save("lock-saver-bn-large", rule.onRoot().captureToImage().asAndroidBitmap())
        rule.onNode(unlockIn("bn")).performScrollTo().performClick()
        assertEquals(1, unlocks)
    }

    @Test
    fun unlockIsReachableSideways() {
        show(reading = true, size = SIDEWAYS)
        Screens.save("lock-saver-sideways", rule.onRoot().captureToImage().asAndroidBitmap())
        rule.onNode(unlock).performScrollTo().performClick()
        rule.onNode(pause).performScrollTo().performClick()
        assertEquals(1, unlocks)
        assertEquals(1, pauses)
    }

    private companion object {
        val PHONE = DpSize(411.dp, 914.dp)
        val SIDEWAYS = DpSize(914.dp, 411.dp)
        const val LARGE_TEXT = 2f
    }
}
