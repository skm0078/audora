// File: app/src/test/kotlin/io/github/skm0078/audora/privacy/AppLockSessionTest.kt
// Purpose: The app lock opens once per proof, survives short trips away and closes after a long one (ADR-061).

package io.github.skm0078.audora.privacy

import io.github.skm0078.audora.core.config.AppLockConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockSessionTest {
    private val session = AppLockSession(AppLockConfig(lockAfterAwayMs = AWAY_LIMIT))

    @Test
    fun `a new process starts locked and asks by itself once`() {
        assertFalse(session.unlocked)
        assertTrue(session.takeAutoPrompt())
        assertFalse(session.takeAutoPrompt())
    }

    @Test
    fun `unlocking opens it and stops the automatic prompt`() {
        session.unlock()
        assertTrue(session.unlocked)
        assertFalse(session.takeAutoPrompt())
    }

    @Test
    fun `a short trip away keeps it open`() {
        session.unlock()
        session.onAway(nowMs = 1_000)
        session.onBack(nowMs = 1_000 + AWAY_LIMIT - 1)
        assertTrue(session.unlocked)
    }

    @Test
    fun `a long trip away locks it and asks again by itself`() {
        session.unlock()
        session.onAway(nowMs = 1_000)
        session.onBack(nowMs = 1_000 + AWAY_LIMIT)
        assertFalse(session.unlocked)
        assertTrue(session.takeAutoPrompt())
    }

    @Test
    fun `the first time away counts, not a later stop while still away`() {
        session.unlock()
        session.onAway(nowMs = 0)
        session.onAway(nowMs = AWAY_LIMIT)
        session.onBack(nowMs = AWAY_LIMIT)
        assertFalse(session.unlocked)
    }

    @Test
    fun `coming back without having left changes nothing`() {
        session.unlock()
        session.onBack(nowMs = 10 * AWAY_LIMIT)
        assertTrue(session.unlocked)
    }

    @Test
    fun `a proof that lands after a long trip away still opens it`() {
        session.takeAutoPrompt()
        session.onAway(nowMs = 0)
        session.onBack(nowMs = 2 * AWAY_LIMIT)
        session.unlock()
        assertTrue(session.unlocked)
    }

    private companion object {
        const val AWAY_LIMIT = 30_000L
    }
}
