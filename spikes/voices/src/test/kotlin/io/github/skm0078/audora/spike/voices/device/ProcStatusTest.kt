// File: spikes/voices/src/test/kotlin/io/github/skm0078/audora/spike/voices/device/ProcStatusTest.kt
// Purpose: Parses resident memory from /proc/self/status text.

package io.github.skm0078.audora.spike.voices.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProcStatusTest {
    private val status = "Name:\tvoices\nVmHWM:\t  412340 kB\nVmRSS:\t  204800 kB\nThreads:\t42\n"

    @Test
    fun `reads a kB field as MB`() {
        assertEquals(200L, ProcStatus.parseKbFieldMb(status, "VmRSS"))
        assertEquals(402L, ProcStatus.parseKbFieldMb(status, "VmHWM"))
    }

    @Test
    fun `missing field is null`() {
        assertNull(ProcStatus.parseKbFieldMb(status, "VmSwap"))
    }
}
