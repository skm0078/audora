// File: playback/src/androidTest/kotlin/io/github/skm0078/audora/playback/contract/PlaybackContractDeviceTest.kt
// Purpose: PLAY_FROM's unit index survives the Bundle, and a start without one (or a bad one) reads as none.

package io.github.skm0078.audora.playback.contract

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackContractDeviceTest {
    @Test
    fun aUnitIndexIsCarriedAndReadBack() {
        val args = PlaybackContract.playFromArgs("book", page = 2, charStart = 40, unitIndex = 7)
        assertEquals(7, PlaybackContract.unitIndexFrom(args))
        assertEquals(0, PlaybackContract.unitIndexFrom(PlaybackContract.playFromArgs("book", 2, 0, unitIndex = 0)))
    }

    @Test
    fun noIndexOrANegativeOneIsNone() {
        assertNull(PlaybackContract.unitIndexFrom(PlaybackContract.playFromArgs("book", page = 2, charStart = 40)))
        val bad = Bundle().apply { putInt(PlaybackContract.ARG_UNIT_INDEX, -1) }
        assertNull(PlaybackContract.unitIndexFrom(bad))
    }
}
