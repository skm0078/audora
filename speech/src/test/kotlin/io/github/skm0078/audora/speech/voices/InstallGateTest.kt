// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/voices/InstallGateTest.kt
// Purpose: A model deleted while it downloads is not installed after all; one downloaded after the delete is.

package io.github.skm0078.audora.speech.voices

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstallGateTest {
    private val gate = InstallGate()

    @Test
    fun `an install runs when no delete came since its ticket`() {
        val ticket = gate.ticket()
        assertEquals("installed", gate.install(ticket) { "installed" })
    }

    @Test
    fun `a delete after the ticket stops the install, and a new ticket installs again`() {
        val stale = gate.ticket()
        assertEquals("deleted", gate.delete { "deleted" })
        assertNull(gate.install(stale) { "installed" })
        assertEquals("installed", gate.install(gate.ticket()) { "installed" })
    }
}
