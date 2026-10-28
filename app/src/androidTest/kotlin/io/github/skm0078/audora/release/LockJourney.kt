// File: app/src/androidTest/kotlin/io/github/skm0078/audora/release/LockJourney.kt
// Purpose: The release journey's app lock step on the installed app (ADR 2026-10-28-lock-screen-saver), kept apart
//          from ReleaseJourneyTest so it stays under detekt's class size.

package io.github.skm0078.audora.release

import android.app.KeyguardManager
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performClick
import io.github.skm0078.audora.R
import io.github.skm0078.audora.privacy.AppLockSession
import io.github.skm0078.audora.privacy.PrivacyPreferences
import io.github.skm0078.audora.release.JourneyDevice.app
import io.github.skm0078.audora.release.JourneyDevice.awaitTrue
import io.github.skm0078.audora.release.JourneyDevice.logCount
import io.github.skm0078.audora.release.JourneyDevice.logTail
import io.github.skm0078.audora.release.JourneyDevice.shows
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse

internal class LockJourney(private val rule: ComposeTestRule) {
    private fun text(id: Int): String = app.getString(id)

    /**
     * With a PIN on the emulator, turning the lock on covers whatever is open with the saver, which asks for the
     * phone's lock by itself; Unlock asks again. The proof itself is Android's screen, so the step opens the lock the
     * way a passed proof does, and the saver's leaving takes the system prompt away with it.
     */
    fun theSaverCoversTheAppAndUnlockAsks() {
        val preferences = PrivacyPreferences(app)
        val pinSet = JourneyDevice.shell("locksettings set-pin $PIN")
        try {
            // Without a screen lock the saver opens at once by design, so the PIN must be in force first.
            val keyguard = app.getSystemService(KeyguardManager::class.java)
            awaitTrue("the emulator to have a PIN", state = { "locksettings said: $pinSet" }) {
                keyguard.isDeviceSecure
            }
            val asked = logCount(ASKING)
            runBlocking { preferences.saveAppLock(true) }
            // This process never unlocked, so the lock closes at once.
            awaitTrue("the lock to ask for the phone's lock by itself", state = ::recentLog) {
                logCount(ASKING) > asked
            }
            val saver = hasText(text(R.string.lock_saver_hidden))
            rule.waitUntil(UI_TIMEOUT_MS) { rule.shows(saver) }
            val title = hasText(text(R.string.app_lock_locked_title)) or hasText(text(R.string.lock_saver_reading))
            rule.waitUntil(UI_TIMEOUT_MS) { rule.shows(title) }
            // The screens behind are hidden from TalkBack as well as from sight.
            assertFalse("A tab can be found behind the lock", rule.shows(hasText(text(R.string.tab_you))))

            val before = logCount(ASKING)
            rule.onNode(hasText(text(R.string.app_lock_unlock)) and hasClickAction()).performClick()
            awaitTrue("Unlock to ask for the phone's lock again", state = ::recentLog) { logCount(ASKING) > before }
        } finally {
            JourneyDevice.onMain { AppLockSession.process.unlock() }
            runBlocking { preferences.saveAppLock(false) }
            JourneyDevice.shell("locksettings clear --old $PIN")
        }
        rule.waitUntil(UI_TIMEOUT_MS) { !rule.shows(hasText(text(R.string.lock_saver_hidden))) }
    }

    private fun recentLog(): String {
        val resumed = JourneyDevice.resumedActivity()?.javaClass?.simpleName
        return "Resumed: $resumed; log: ${logTail(LOG_LINES).joinToString(" | ")}"
    }

    private companion object {
        const val PIN = "2580"
        const val ASKING = "Asking for the phone's lock"
        const val UI_TIMEOUT_MS = 15_000L
        const val LOG_LINES = 8
    }
}
