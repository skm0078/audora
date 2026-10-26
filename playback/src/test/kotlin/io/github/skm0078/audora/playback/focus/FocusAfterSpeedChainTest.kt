// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/focus/FocusAfterSpeedChainTest.kt
// Purpose: The focus sound is the audio chain's last step, after speed and pitch, so a recording is never warped (Q-1).

package io.github.skm0078.audora.playback.focus

import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.SonicAudioProcessor
import io.github.skm0078.audora.core.model.FocusSoundOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusAfterSpeedChainTest {
    private val focus = FocusSoundProcessor({ FocusSoundOptions() })
    private val chain = FocusAfterSpeedChain(focus)

    @Test
    fun theFocusSoundComesAfterTheSpeedAndPitchChange() {
        val processors = chain.audioProcessors
        assertSame(focus, processors.last())
        val sonic = processors.indexOfFirst { it is SonicAudioProcessor }
        assertTrue("Sonic at $sonic", sonic in 0 until processors.lastIndex)
        assertEquals(1, processors.count { it === focus })
    }

    @Test
    fun speedAndPitchStillReachTheDefaultChain() {
        val asked = PlaybackParameters(2f, 1.2f)
        assertEquals(asked, chain.applyPlaybackParameters(asked))
        assertEquals(true, chain.applySkipSilenceEnabled(true))
        assertEquals(0L, chain.skippedOutputFrameCount)
    }
}
