// File: core/src/main/kotlin/io/github/skm0078/audora/core/resume/AnchorText.kt
// Purpose: The text a resume anchor fingerprints, so units with no text of their own can still be found again.

package io.github.skm0078.audora.core.resume

import io.github.skm0078.audora.core.model.ReadingUnit

/**
 * What [SentenceFingerprint] hashes for this unit: its text, or, for a unit with none (a zero-length table summary,
 * continued or headless), a key built from its table's shape. Without it such a unit fingerprints as
 * [SentenceFingerprint.NONE] and an anchor saved on it is never found again after a pipeline change. Every saver
 * (resume, bookmark, headset bookmark) and [ReAnchoring] go through this, so they agree.
 */
val ReadingUnit.anchorText: String
    get() {
        val info = table
        return if (text.isNotBlank() || info == null) {
            text
        } else {
            // SentenceFingerprint keeps only letters and digits, so spaces vanish: a letter before each number keeps
            // rowsBefore 1 / rowCount 23 apart from 12 / 3, and "h" ends the numbers before a header such as "2019".
            "table r${info.rowsBefore} n${info.rowCount} c${info.columnCount} h " + info.headers.joinToString(" ")
        }
    }
