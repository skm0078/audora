// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/WatchedFolderConfig.kt
// Purpose: Tunables for watched folders: new books in a chosen folder join the library (plan item 26, ADR-044).

package io.github.skm0078.audora.core.config

/**
 * @property maxDepth sub-folders are searched this many levels down (0 = the folder itself only).
 * @property maxEntriesPerScan a scan lists at most this many files and folders in all, so a huge folder (a whole
 *   Downloads) costs bounded time on each library open.
 * @property maxAddsPerScan at most this many new books are added per scan; the rest come on the next library open.
 * @property extensions file name endings taken as books when the provider's type says nothing more specific. Text
 *   files are left out: a folder of books often holds READMEs and notes that are not books.
 * @property mimeTypes provider types taken as books.
 */
data class WatchedFolderConfig(
    val maxDepth: Int = 4,
    val maxEntriesPerScan: Int = 5_000,
    val maxAddsPerScan: Int = 50,
    val extensions: Set<String> = setOf("pdf", "epub"),
    val mimeTypes: Set<String> = setOf("application/pdf", "application/epub+zip"),
) {
    init {
        require(maxDepth >= 0) { "maxDepth must be >= 0, was $maxDepth" }
        require(maxEntriesPerScan > 0) { "maxEntriesPerScan must be > 0, was $maxEntriesPerScan" }
        require(maxAddsPerScan > 0) { "maxAddsPerScan must be > 0, was $maxAddsPerScan" }
    }
}
