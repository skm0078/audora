// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/ocr/OcrTextCacheFiles.kt
// Purpose: The recognised-text cache's size and a way to empty it, for the Storage screen.

package io.github.skm0078.audora.playback.ocr

import android.content.Context
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Text OCR recognised, kept so a page is never recognised twice; it trims itself to OcrConfig.cacheMaxBytes.
 * Emptying it only costs time: each scanned page is recognised again the next time it is read.
 */
object OcrTextCacheFiles {
    private const val TAG = "Playback.OcrCache"

    suspend fun sizeBytes(context: Context): Long = withContext(Dispatchers.IO) {
        folder(context).walkBottomUp().filter { it.isFile }.sumOf { it.length() }
    }

    /** Fails, logged, with the files that could not be removed; a missing folder is already empty. */
    suspend fun clear(context: Context): Outcome<Unit, String> = withContext(Dispatchers.IO) {
        val files = folder(context).listFiles().orEmpty()
        val kept = files.count { !it.deleteRecursively() }
        if (kept == 0) {
            AudoraLog.i(TAG, "OCR text cache cleared, files=${files.size}")
            Outcome.Success(Unit)
        } else {
            AudoraLog.w(TAG, "OCR text cache: $kept of ${files.size} files could not be removed")
            Outcome.Failure("$kept files could not be removed")
        }
    }

    private fun folder(context: Context) = File(context.applicationContext.cacheDir, BookOcr.CACHE_DIR)
}
