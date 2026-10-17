// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/EpubConfig.kt
// Purpose: Tunables for the accessible copy and the talking-book EPUB (plan items 69 and 76, ADR-040).

package io.github.skm0078.audora.core.config

/**
 * @property paragraphPauseMs a sentence followed by at least this much silence (`ReadingUnit.pauseAfterMs`) ends a
 *   paragraph, as it does for listening.
 * @property keepAtLeast units scored below this (`ReadingUnit.speakScore`, OCR junk) are left out of the copy,
 *   unless they were read aloud in a talking book; the same threshold "Skip unreadable text" uses.
 * @property chapterTitleMaxChars a heading longer than this is cut (with an ellipsis) in the table of contents.
 * @property fileNameMaxChars the book title's share of a file name.
 * @property folder where copies go on shared storage, under Documents (ebook and screen-reader apps look there).
 * @property progressStepPercent the notification moves on in steps of at least this much.
 * @property wakeLockMs the wake lock is taken for this long and renewed at every progress step and chapter, so a stuck
 *   job lets the phone sleep within this time; no page or chapter step takes nearly this long.
 */
data class EpubConfig(
    val paragraphPauseMs: Int = ReadingUnitConfig().paragraphPauseMs,
    val keepAtLeast: Float = SpeakabilityConfig().skipBelow,
    val chapterTitleMaxChars: Int = 80,
    val fileNameMaxChars: Int = AudiobookConfig().folderNameMaxChars,
    val folder: String = "Documents/Audora/",
    val progressStepPercent: Int = 1,
    val wakeLockMs: Long = 1_800_000L, // 30 min
) {
    init {
        require(paragraphPauseMs >= 0) { "paragraphPauseMs must be >= 0, was $paragraphPauseMs" }
        require(keepAtLeast in 0f..1f) { "keepAtLeast must be in 0..1, was $keepAtLeast" }
        require(chapterTitleMaxChars > 1) { "chapterTitleMaxChars must be > 1, was $chapterTitleMaxChars" }
        require(fileNameMaxChars > 0) { "fileNameMaxChars must be > 0, was $fileNameMaxChars" }
        require(folder.endsWith('/')) { "folder must end with '/', was $folder" }
        require(progressStepPercent in 1..PERCENT) { "progressStepPercent must be in 1..100" }
        require(wakeLockMs > 0) { "wakeLockMs must be > 0, was $wakeLockMs" }
    }

    private companion object {
        const val PERCENT = 100
    }
}
