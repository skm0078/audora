// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/export/AudioChapters.kt
// Purpose: Splits a book's pages into the chapters of an exported audiobook, one audio file each (ADR-034).

package io.github.skm0078.audora.playback.export

import io.github.skm0078.audora.core.config.AudiobookConfig

/**
 * One file of the audiobook: pages [firstPage]..[lastPage], zero-based and inclusive.
 * @property number one-based position in the book; files sort by it.
 */
data class AudioChapter(val number: Int, val firstPage: Int, val lastPage: Int) {
    init {
        require(number >= 1) { "number must be >= 1, was $number" }
        require(firstPage in 0..lastPage) { "firstPage ($firstPage) must be in 0..lastPage ($lastPage)" }
    }

    val pages: IntRange get() = firstPage..lastPage

    /**
     * "03 Pages 41-58": zero-padded to the widest [number] of [total], so every file manager and audiobook
     * player sorts the files in reading order. Pages are shown one-based, as the reader shows them.
     */
    fun fileStem(total: Int): String {
        val width = total.toString().length
        val range = if (firstPage == lastPage) "Page ${firstPage + 1}" else "Pages ${firstPage + 1}-${lastPage + 1}"
        return "${number.toString().padStart(width, '0')} $range"
    }
}

object AudioChapters {
    /**
     * Chapters start at [chapterStarts] (from the PDF outline, `BookText.chapterStarts`). Pages before the first
     * chapter (cover, contents, preface) become a chapter of their own rather than being dropped. A book with
     * no chapters is cut into parts of [AudiobookConfig.pagesPerPartWithoutChapters] pages.
     */
    fun plan(
        chapterStarts: List<Int>,
        pageCount: Int,
        config: AudiobookConfig = AudiobookConfig(),
    ): List<AudioChapter> {
        if (pageCount <= 0) return emptyList()
        val outlined = chapterStarts.filter { it in 0 until pageCount }
        val starts = if (outlined.any { it > 0 }) {
            (outlined + 0).distinct().sorted()
        } else {
            (0 until pageCount step config.pagesPerPartWithoutChapters).toList()
        }
        return starts.mapIndexed { i, first ->
            val last = starts.getOrNull(i + 1)?.minus(1) ?: (pageCount - 1)
            AudioChapter(number = i + 1, firstPage = first, lastPage = last)
        }
    }
}

/** Where a book's audiobook goes on shared storage: `Audiobooks/<title>/`, the folder audiobook players scan. */
object AudiobookFolder {
    /** Android's `Environment.DIRECTORY_AUDIOBOOKS`, kept here so this stays plain Kotlin. */
    const val ROOT = "Audiobooks"

    /** Characters no Android file system or FAT-formatted card accepts in a name. */
    private val UNSAFE = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")
    private val SPACES = Regex("\\s+")

    fun relativePath(title: String, fallback: String, config: AudiobookConfig = AudiobookConfig()): String =
        "$ROOT/${folderName(title, config).ifEmpty { folderName(fallback, config) }}/"

    /** The title with unsafe characters and leading dots removed, spaces collapsed, cut to the configured length. */
    fun folderName(title: String, config: AudiobookConfig = AudiobookConfig()): String = title
        .replace(UNSAFE, " ")
        .replace(SPACES, " ")
        .trim()
        .trimStart('.')
        .take(config.folderNameMaxChars)
        .trim()
}
