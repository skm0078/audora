// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/reading/ReadingSource.kt
// Purpose: Where the session gets its reading modes and the open book's chapter pages, for playlist and renderer alike.

package io.github.skm0078.audora.playback.reading

import io.github.skm0078.audora.core.config.SpeakabilityConfig
import io.github.skm0078.audora.core.model.ReadingModes

/**
 * One per playback service. [modes] is read when a render epoch starts (a mode change restarts the sentence playing,
 * like any style change); [chapterPages] and [scanned] are set when a book opens. All are read on the render thread
 * too, so [chapterPages] and [scanned] are volatile and [modes] must be thread-safe.
 */
class ReadingSource(
    val modes: () -> ReadingModes = { ReadingModes() },
    val speakability: SpeakabilityConfig = SpeakabilityConfig(),
) {
    /** The open book's chapter start pages (PDF outline); empty with no book or no outline. */
    @Volatile
    var chapterPages: Set<Int> = emptySet()

    /** Whether a page of the open book was read by OCR (RI-11); set when a book opens. */
    @Volatile
    var scanned: (Int) -> Boolean = { false }

    /** The modes as they are now, for one render epoch's playlist. */
    fun current(): UnitReading = UnitReading(modes(), speakability, chapterPages)
}
