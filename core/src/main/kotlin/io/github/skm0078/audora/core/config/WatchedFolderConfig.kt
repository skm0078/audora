// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/WatchedFolderConfig.kt
// Purpose: Tunables for watched folders: new books in a chosen folder join the library (plan item 26, ADR-044).

package io.github.skm0078.audora.core.config

/**
 * @property maxDepth sub-folders are searched this many levels down (0 = the folder itself only).
 * @property maxEntriesPerScan a scan lists at most this many files and folders in all, so a huge folder (a whole
 *   Downloads) costs bounded time on each library open.
 * @property maxAddsPerScan at most this many files are tried per scan (added or failed); the rest come on the next
 *   library open, so offline cloud files cannot make every open slow.
 * @property minScanIntervalMs a library open within this long of the last scan does not scan again (returning to
 *   the tab, rotating the phone); adding a folder always scans.
 * @property retryBaseMs a file that could not be added (offline, still downloading, empty) is tried again after
 *   this long, doubling with each failure up to [retryMaxMs], instead of being given up on.
 * @property retryMaxMs the longest wait between two tries of a file.
 * @property extensions file name endings taken as books when the provider's type says nothing more specific. Text
 *   files, Markdown, web pages and pictures are left out: a folder of books often holds READMEs, notes, saved
 *   pages and covers that are not books. Word, OpenDocument and RTF documents are books (ADR-066), and so are FB2,
 *   Kindle and Mobipocket books and CHM help files (ADR 2026-10-26-more-ebook-formats). Comic archives are not
 *   watched: each becomes a new photo book, which is made only when the listener asks for it.
 * @property mimeTypes provider types taken as books.
 */
data class WatchedFolderConfig(
    val maxDepth: Int = 4,
    val maxEntriesPerScan: Int = 5_000,
    val maxAddsPerScan: Int = 50,
    val minScanIntervalMs: Long = 180_000L,
    val retryBaseMs: Long = 3_600_000L,
    val retryMaxMs: Long = 604_800_000L,
    val extensions: Set<String> = setOf(
        "pdf", "epub", "docx", "odt", "rtf", "fb2", "mobi", "azw3", "azw", "prc", "chm",
    ),
    val mimeTypes: Set<String> = setOf(
        "application/pdf",
        "application/epub+zip",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.oasis.opendocument.text",
        "application/rtf",
        "text/rtf",
        "application/x-fictionbook+xml",
        "application/x-mobipocket-ebook",
        "application/vnd.amazon.mobi8-ebook",
        "application/vnd.ms-htmlhelp",
    ),
) {
    init {
        require(maxDepth >= 0) { "maxDepth must be >= 0, was $maxDepth" }
        require(maxEntriesPerScan > 0) { "maxEntriesPerScan must be > 0, was $maxEntriesPerScan" }
        require(maxAddsPerScan > 0) { "maxAddsPerScan must be > 0, was $maxAddsPerScan" }
        require(minScanIntervalMs >= 0) { "minScanIntervalMs must be >= 0, was $minScanIntervalMs" }
        require(retryBaseMs > 0) { "retryBaseMs must be > 0, was $retryBaseMs" }
        require(retryMaxMs >= retryBaseMs) { "retryMaxMs must be >= retryBaseMs, was $retryMaxMs" }
    }
}
