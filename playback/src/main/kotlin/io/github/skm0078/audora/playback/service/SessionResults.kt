// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/service/SessionResults.kt
// Purpose: How a custom command's outcome reaches the controller: success, or an error code under RESULT_ERROR.

package io.github.skm0078.audora.playback.service

import android.os.Bundle
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import io.github.skm0078.audora.playback.contract.PlaybackContract
import io.github.skm0078.audora.playback.contract.PlaybackErrorCode
import io.github.skm0078.audora.playback.session.ParagraphSkipResult

/** The results [SessionCallback] answers custom commands with; apart so a device test can check what the UI reads. */
internal object SessionResults {
    fun success(): SessionResult = SessionResult(SessionResult.RESULT_SUCCESS)

    /** An error the controller reads by name from extras[[PlaybackContract.RESULT_ERROR]]. */
    fun failure(code: PlaybackErrorCode): SessionResult {
        val resultCode = if (code == PlaybackErrorCode.BAD_REQUEST) {
            SessionError.ERROR_BAD_VALUE
        } else {
            SessionError.ERROR_UNKNOWN
        }
        return SessionResult(resultCode, Bundle().apply { putString(PlaybackContract.RESULT_ERROR, code.name) })
    }

    /** SKIP_PARAGRAPH: "nowhere to go" is told to the listener; a dropped skip is stale. */
    fun of(skip: ParagraphSkipResult): SessionResult = when (skip) {
        ParagraphSkipResult.MOVED -> success()
        ParagraphSkipResult.NONE_THAT_WAY -> failure(PlaybackErrorCode.NO_PARAGRAPH_THAT_WAY)
        ParagraphSkipResult.DROPPED -> failure(PlaybackErrorCode.STALE_REQUEST)
    }
}
