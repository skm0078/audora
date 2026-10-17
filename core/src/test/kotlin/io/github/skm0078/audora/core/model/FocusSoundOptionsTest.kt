// File: core/src/test/kotlin/io/github/skm0078/audora/core/model/FocusSoundOptionsTest.kt
// Purpose: The focus sound choice survives its one-line encoding, and a bad line falls back to defaults.

package io.github.skm0078.audora.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusSoundOptionsTest {
    @Test
    fun roundTripsEverySound() {
        for (sound in FocusSound.entries) {
            val options = FocusSoundOptions(sound, level = 0.25f)
            assertEquals(options, FocusSoundOptions.decode(options.encode()))
        }
    }

    @Test
    fun badOrMissingValuesTakeTheirDefaults() {
        val cases = listOf(
            null to FocusSoundOptions(),
            "" to FocusSoundOptions(),
            "sound=THUNDER;level=0.3" to FocusSoundOptions(level = 0.3f),
            "sound=RAIN;level=7" to FocusSoundOptions(FocusSound.RAIN),
            "sound=CAFE;level=x" to FocusSoundOptions(FocusSound.CAFE),
            "level=0.1;sound=BROWN_NOISE;extra=1" to FocusSoundOptions(FocusSound.BROWN_NOISE, 0.1f),
        )
        for ((line, expected) in cases) assertEquals("line $line", expected, FocusSoundOptions.decode(line))
    }

    @Test
    fun onlyASoundAboveZeroIsOn() {
        assertFalse(FocusSoundOptions().on)
        assertFalse(FocusSoundOptions(FocusSound.RAIN, level = 0f).on)
        assertTrue(FocusSoundOptions(FocusSound.RAIN, level = 0.1f).on)
    }
}
