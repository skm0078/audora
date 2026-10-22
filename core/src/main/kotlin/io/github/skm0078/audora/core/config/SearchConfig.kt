// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/SearchConfig.kt
// Purpose: Tunables for search in the book (plan item 16, ADR-041).

package io.github.skm0078.audora.core.config

/**
 * @property minQueryChars a query with fewer letters and digits than this finds nothing (one letter would match
 *   most of the book through the prefix match).
 * @property maxQueryTerms words past this many are ignored, so a pasted paragraph stays a cheap query.
 * @property maxHits at most this many sentences are listed, in reading order.
 * @property batchPages pages written to the index in one transaction; also how often progress moves on.
 * @property folder the indexes' folder under noBackupFilesDir: a rebuildable cache, left out of backups.
 */
data class SearchConfig(
    val minQueryChars: Int = 2,
    val maxQueryTerms: Int = 8,
    val maxHits: Int = 200,
    val batchPages: Int = 10,
    val folder: String = "search",
) {
    init {
        require(minQueryChars > 0) { "minQueryChars must be > 0, was $minQueryChars" }
        require(maxQueryTerms > 0) { "maxQueryTerms must be > 0, was $maxQueryTerms" }
        require(maxHits > 0) { "maxHits must be > 0, was $maxHits" }
        require(batchPages > 0) { "batchPages must be > 0, was $batchPages" }
        require(folder.isNotBlank()) { "folder must not be blank" }
    }
}
