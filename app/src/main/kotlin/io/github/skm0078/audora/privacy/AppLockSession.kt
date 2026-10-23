// File: app/src/main/kotlin/io/github/skm0078/audora/privacy/AppLockSession.kt
// Purpose: Whether the app lock (ADR-061) is open for this process, and when it closes again.

package io.github.skm0078.audora.privacy

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.skm0078.audora.core.config.AppLockConfig

/**
 * Lives as long as the process, so turning the phone sideways never asks again, and a new process (after Android
 * reclaimed the app) always starts locked. Saved instance state is deliberately not used: it would carry "unlocked"
 * across a process restart. Snapshot state, so a lock set in onStart is drawn on the very next frame.
 *
 * Whether the lock is turned on at all lives in [PrivacyPreferences]; this only says whether it is open right now.
 * The app uses the one [process] session; tests make their own.
 */
class AppLockSession(private val config: AppLockConfig) {
    var unlocked by mutableStateOf(false)
        private set

    private var awaySinceMs: Long? = null
    private var autoPromptDue = true

    /** The person proved it is them (or turned the lock on, which proves it too). */
    fun unlock() {
        unlocked = true
        awaySinceMs = null
        autoPromptDue = false
    }

    /** The app went out of sight (onStop, not for a configuration change). */
    fun onAway(nowMs: Long) {
        if (awaySinceMs == null) awaySinceMs = nowMs
    }

    /** The app is in sight again (onStart): locks if it was away for [AppLockConfig.lockAfterAwayMs] or longer. */
    fun onBack(nowMs: Long) {
        val since = awaySinceMs ?: return
        awaySinceMs = null
        if (unlocked && nowMs - since >= config.lockAfterAwayMs) {
            unlocked = false
            autoPromptDue = true
        }
    }

    /**
     * True once per lock: the lock screen asks for the fingerprint by itself the first time, and after a cancel
     * waits for the Unlock button, so dismissing the prompt never brings it straight back.
     */
    fun takeAutoPrompt(): Boolean = autoPromptDue.also { autoPromptDue = false }

    companion object {
        /** The session of this process: a new process starts locked. */
        val process = AppLockSession(AppLockConfig())
    }
}
