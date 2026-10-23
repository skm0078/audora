// File: playback/src/androidTest/kotlin/io/github/skm0078/audora/playback/contract/PlaybackContractDeviceTest.kt
// Purpose: PLAY_FROM's unit index survives the Bundle (a bad one reads as none); command errors read back by name.

package io.github.skm0078.audora.playback.contract

import android.os.Bundle
import androidx.media3.session.SessionResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.skm0078.audora.playback.service.SessionResults
import io.github.skm0078.audora.playback.session.ParagraphSkipResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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

    @Test
    fun aParagraphSkipWithNowhereToGoIsTheNoParagraphThatWayError() {
        // BookSessionTest shows the session answers NONE_THAT_WAY at the book's start; this is the result it becomes.
        val result = SessionResults.of(ParagraphSkipResult.NONE_THAT_WAY)
        assertNotEquals(SessionResult.RESULT_SUCCESS, result.resultCode)
        assertEquals(
            PlaybackErrorCode.NO_PARAGRAPH_THAT_WAY.name,
            result.extras.getString(PlaybackContract.RESULT_ERROR),
        )
        assertEquals(SessionResult.RESULT_SUCCESS, SessionResults.of(ParagraphSkipResult.MOVED).resultCode)
        assertEquals(
            PlaybackErrorCode.STALE_REQUEST.name,
            SessionResults.of(ParagraphSkipResult.DROPPED).extras.getString(PlaybackContract.RESULT_ERROR),
        )
    }
}
