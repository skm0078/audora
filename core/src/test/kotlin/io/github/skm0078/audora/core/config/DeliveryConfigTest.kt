// File: core/src/test/kotlin/io/github/skm0078/audora/core/config/DeliveryConfigTest.kt
// Purpose: The delivery tunables cover every intent and feeling and refuse values that would break playback (ADR-056).

package io.github.skm0078.audora.core.config

import io.github.skm0078.audora.core.model.ContentIntent
import io.github.skm0078.audora.core.model.Feeling
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DeliveryConfigTest {
    @Test
    fun `defaults cover every intent and feeling, and plain reading is unchanged`() {
        val config = DeliveryConfig()
        ContentIntent.entries.forEach { config.profile(it) }
        Feeling.entries.forEach { config.effect(it) }
        assertEquals(FeelingEffect(), config.effect(Feeling.NEUTRAL))
        val general = config.profile(ContentIntent.GENERAL)
        assertEquals(1f, general.rate)
        assertEquals(1f, general.pauseScale)
    }

    @Test
    fun `only stories and plain text may carry feeling`() {
        val config = DeliveryConfig()
        ContentIntent.entries.filterNot { it.feelings }.forEach {
            assertEquals("$it", 0f, config.profile(it).feelingScale)
        }
        assertTrue(ContentIntent.STORY.feelings)
        assertFalse(ContentIntent.SCRIPTURE.feelings)
    }

    @Test
    fun `bad values are refused`() {
        assertThrows(IllegalArgumentException::class.java) { DeliveryProfile(rate = 0f) }
        assertThrows(IllegalArgumentException::class.java) { DeliveryProfile(feelingScale = 3f) }
        assertThrows(IllegalArgumentException::class.java) { FeelingEffect(pauseScale = Float.NaN) }
        assertThrows(IllegalArgumentException::class.java) { DeliveryConfig(minWords = 10, settleWords = 5) }
        assertThrows(IllegalArgumentException::class.java) {
            DeliveryConfig(profiles = mapOf(ContentIntent.STORY to DeliveryProfile()))
        }
    }

    @Test
    fun `intents read back by name and ignore unknown ones`() {
        ContentIntent.entries.forEach { assertEquals(it, ContentIntent.of(it.name)) }
        assertEquals(null, ContentIntent.of("OPERA"))
        assertEquals(null, ContentIntent.of(null))
    }
}
