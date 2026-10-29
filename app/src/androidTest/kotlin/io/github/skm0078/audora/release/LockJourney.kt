// File: app/src/androidTest/kotlin/io/github/skm0078/audora/release/LockJourney.kt
// Purpose: The release journey's app lock step on the installed app (ADR 2026-10-28-lock-screen-saver), kept apart
//          from ReleaseJourneyTest so it stays under detekt's class size.

package io.github.skm0078.audora.release

import android.app.KeyguardManager
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skm0078.audora.R
import io.github.skm0078.audora.privacy.AppLockSession
import io.github.skm0078.audora.privacy.PrivacyPreferences
import io.github.skm0078.audora.release.JourneyDevice.app
import io.github.skm0078.audora.release.JourneyDevice.awaitTrue
import io.github.skm0078.audora.release.JourneyDevice.logCount
import io.github.skm0078.audora.release.JourneyDevice.logTail
import io.github.skm0078.audora.release.JourneyDevice.shows
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse

internal class LockJourney(private val rule: ComposeTestRule) {
    private fun text(id: Int): String = app.getString(id)

    /**
     * Turning the lock on covers whatever is open. A device with a secure lock screen gets the full path (see
     * [saverAsksAndUnlockAsksAgain]); one without (CI's emulator image has none, so no PIN can be set) gets the path
     * such a phone takes: the lock opens by itself, so nobody is locked out of their own library.
     */
    fun coversOrOpens() {
        val preferences = PrivacyPreferences(app)
        val secure = app.packageManager.hasSystemFeature(PackageManager.FEATURE_SECURE_LOCK_SCREEN)
        try {
            if (secure) saverAsksAndUnlockAsksAgain(preferences) else opensWithoutAScreenLock(preferences)
        } finally {
            JourneyDevice.onMain { AppLockSession.process.unlock() }
            runBlocking { preferences.saveAppLock(false) }
        }
        rule.waitUntil(UI_TIMEOUT_MS) { !rule.shows(hasText(text(R.string.lock_saver_hidden))) }
    }

    /**
     * With a PIN, the saver asks for the phone's lock by itself and Unlock asks again. The proof itself is Android's
     * screen, so the step opens the lock the way a passed proof does, and the saver's leaving takes the prompt away.
     */
    private fun saverAsksAndUnlockAsksAgain(preferences: PrivacyPreferences) {
        val pinSet = lockSettings("set-pin $PIN")
        try {
            val keyguard = app.getSystemService(KeyguardManager::class.java)
            awaitTrue("the device to have a PIN", state = { "lock_settings said: $pinSet" }) {
                keyguard.isDeviceSecure
            }
            val asked = logCount(ASKING)
            runBlocking { preferences.saveAppLock(true) }
            // This process never unlocked, so the lock closes at once.
            awaitComposed("the lock to ask for the phone's lock by itself") { logCount(ASKING) > asked }
            val saver = hasText(text(R.string.lock_saver_hidden))
            rule.waitUntil(UI_TIMEOUT_MS) { rule.shows(saver) }
            val title = hasText(text(R.string.app_lock_locked_title)) or hasText(text(R.string.lock_saver_reading))
            rule.waitUntil(UI_TIMEOUT_MS) { rule.shows(title) }
            // The screens behind are hidden from TalkBack as well as from sight.
            assertFalse("A tab can be found behind the lock", rule.shows(hasText(text(R.string.tab_you))))

            val before = logCount(ASKING)
            rule.onNode(hasText(text(R.string.app_lock_unlock)) and hasClickAction()).performClick()
            awaitComposed("Unlock to ask for the phone's lock again") { logCount(ASKING) > before }
        } finally {
            lockSettings("clear --old $PIN")
        }
    }

    /** No screen lock to prove: the lock says so in the log and opens, never asking for a proof that cannot come. */
    private fun opensWithoutAScreenLock(preferences: PrivacyPreferences) {
        val opened = logCount(NO_SCREEN_LOCK)
        val asked = logCount(ASKING)
        runBlocking { preferences.saveAppLock(true) }
        awaitComposed("the lock to open on a device with no screen lock") { logCount(NO_SCREEN_LOCK) > opened }
        rule.waitUntil(UI_TIMEOUT_MS) { !rule.shows(hasText(text(R.string.lock_saver_hidden))) }
        assertEquals("The lock asked for a proof the device cannot give", asked, logCount(ASKING))
    }

    /** Android's lock settings command, with what it printed to both outputs, so a refusal says why. */
    private fun lockSettings(arguments: String): String {
        val streams = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommandRwe("cmd lock_settings $arguments")
        streams[1].close()
        val out = ParcelFileDescriptor.AutoCloseInputStream(streams[0]).use { String(it.readBytes()) }
        val err = ParcelFileDescriptor.AutoCloseInputStream(streams[2]).use { String(it.readBytes()) }
        return "$out$err".trim()
    }

    /**
     * Waits through the compose rule, not a plain sleep: under the journey's rule the app's screens only recompose
     * while the rule waits, so the lock window is never drawn (and never asks) during a sleep.
     */
    private fun awaitComposed(what: String, condition: () -> Boolean) {
        try {
            rule.waitUntil(LOG_TIMEOUT_MS, condition)
        } catch (e: ComposeTimeoutException) {
            throw AssertionError("Timed out waiting for: $what. ${recentLog()}", e)
        }
    }

    private fun recentLog(): String {
        val resumed = JourneyDevice.resumedActivity()?.javaClass?.simpleName
        return "Resumed: $resumed; log: ${logTail(LOG_LINES).joinToString(" | ")}"
    }

    private companion object {
        const val PIN = "2580"
        const val ASKING = "Asking for the phone's lock"
        const val NO_SCREEN_LOCK = "the phone has no screen lock; opening"
        const val UI_TIMEOUT_MS = 15_000L
        const val LOG_TIMEOUT_MS = 30_000L
        const val LOG_LINES = 8
    }
}
