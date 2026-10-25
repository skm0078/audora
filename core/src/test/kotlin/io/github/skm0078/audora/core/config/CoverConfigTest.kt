// File: core/src/test/kotlin/io/github/skm0078/audora/core/config/CoverConfigTest.kt
// Purpose: Covers sized for the phone (ADR-069): the memory cache follows the app's heap, within bounds, and a
//          low-memory phone decodes covers in 16-bit colour.

package io.github.skm0078.audora.core.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverConfigTest {
    @Test
    fun `the memory cache is a sixteenth of the heap, between 4 and 16 MiB`() {
        assertEquals(4 shl 20, CoverConfig.forDevice(memoryClassMb = 48, lowRam = true).memoryCacheBytes)
        assertEquals(12 shl 20, CoverConfig.forDevice(memoryClassMb = 192, lowRam = false).memoryCacheBytes)
        assertEquals(16 shl 20, CoverConfig.forDevice(memoryClassMb = 512, lowRam = false).memoryCacheBytes)
    }

    @Test
    fun `only a low-memory phone gets 16-bit covers`() {
        assertTrue(CoverConfig.forDevice(memoryClassMb = 128, lowRam = true).lowMemory)
        assertFalse(CoverConfig.forDevice(memoryClassMb = 128, lowRam = false).lowMemory)
    }
}
