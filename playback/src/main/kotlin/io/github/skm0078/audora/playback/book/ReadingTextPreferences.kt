// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/book/ReadingTextPreferences.kt
// Purpose: The user's choices for how a book's text is cleaned before reading (P2-03), in a small properties file.

package io.github.skm0078.audora.playback.book

import android.content.Context
import io.github.skm0078.audora.core.config.CleanupConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import java.io.File
import java.io.IOException
import java.util.Properties

/**
 * Read by both the reader and the playback service when they open a book, so both build their sentences with the
 * same [CleanupConfig] and their `charStart` offsets agree. A change applies to books opened afterwards; resume
 * anchors re-anchor by fingerprint when offsets move. A missing or unreadable file means the defaults, never an
 * error. Blocking file I/O: call off the main thread.
 */
class ReadingTextPreferences(private val file: File) {
    /** Whether bracketed numeric citations ("[12]", "[4–7]") are skipped; [CleanupConfig]'s default if unset. */
    fun skipCitationMarkers(): Boolean =
        read().getProperty(KEY_SKIP_CITATIONS)?.toBooleanStrictOrNull() ?: DEFAULTS.skipCitationMarkers

    /** Saves [skip] atomically (temp file + rename). Returns false, after logging, if it could not. */
    fun setSkipCitationMarkers(skip: Boolean): Boolean {
        val properties = read().apply { setProperty(KEY_SKIP_CITATIONS, skip.toString()) }
        val temp = File(file.path + ".tmp")
        return try {
            file.parentFile?.mkdirs()
            temp.writer(Charsets.UTF_8).use { properties.store(it, null) }
            if (!temp.renameTo(file)) throw IOException("cannot replace ${file.name}")
            AudoraLog.i(TAG, "Skip citation markers set to $skip")
            true
        } catch (e: IOException) {
            AudoraLog.e(TAG, "Could not save the reading text settings", e)
            false
        }
    }

    /** [base] with the user's choices applied: what a book's ReadingUnitBuilder is built with. */
    fun cleanupConfig(base: CleanupConfig = DEFAULTS): CleanupConfig =
        base.copy(skipCitationMarkers = skipCitationMarkers())

    private fun read(): Properties {
        if (!file.isFile) return Properties()
        return try {
            file.reader(Charsets.UTF_8).use { reader -> Properties().apply { load(reader) } }
        } catch (e: IOException) {
            AudoraLog.w(TAG, "Reading text settings unreadable, using defaults: ${e.message}")
            Properties()
        } catch (e: IllegalArgumentException) {
            // Properties.load throws this on a malformed \uXXXX escape.
            AudoraLog.w(TAG, "Reading text settings malformed, using defaults: ${e.message}")
            Properties()
        }
    }

    companion object {
        /** In the app's files, shared by the reader and the playback service (same process, same app). */
        const val FILE_NAME = "reading-text.properties"

        private const val TAG = "Playback.TextPrefs"
        private const val KEY_SKIP_CITATIONS = "skipCitationMarkers"
        private val DEFAULTS = CleanupConfig()

        /** The one place that knows where the file lives. */
        fun forContext(context: Context): ReadingTextPreferences =
            ReadingTextPreferences(File(context.applicationContext.filesDir, FILE_NAME))
    }
}
