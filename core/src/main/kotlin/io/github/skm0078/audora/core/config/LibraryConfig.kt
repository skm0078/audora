// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/LibraryConfig.kt
// Purpose: Tunables for the library: how a book's content fingerprint is taken, how a PDF is recognized (P1-01),
//          and how many lasting file grants are taken before books are kept as copies.

package io.github.skm0078.audora.core.config

/**
 * @property fingerprintHeadBytes how much of the start of a file is hashed into its fingerprint (with its size).
 *   Enough to tell real books apart, small enough that adding a 40 MB scan stays instant.
 * @property pdfHeaderWindowBytes the `%PDF-` marker must start within this many bytes. The PDF spec lets
 *   readers accept junk before it, and Acrobat accepts up to 1024 bytes.
 * @property maxKeptFileGrants lasting read grants on single files Audora takes before it keeps a new book as its own
 *   copy instead. Android allows an app 512 and silently drops the oldest past that (an old book would stop opening);
 *   the gap leaves room for the folder grants (watched folders, folder sync), which count against the same 512.
 */
data class LibraryConfig(
    val fingerprintHeadBytes: Int = DEFAULT_FINGERPRINT_HEAD_BYTES,
    val pdfHeaderWindowBytes: Int = DEFAULT_PDF_HEADER_WINDOW_BYTES,
    val maxKeptFileGrants: Int = DEFAULT_MAX_KEPT_FILE_GRANTS,
) {
    init {
        require(pdfHeaderWindowBytes > 0) { "pdfHeaderWindowBytes must be > 0, was $pdfHeaderWindowBytes" }
        require(fingerprintHeadBytes >= pdfHeaderWindowBytes) {
            "fingerprintHeadBytes ($fingerprintHeadBytes) must cover pdfHeaderWindowBytes ($pdfHeaderWindowBytes)"
        }
        require(maxKeptFileGrants >= 0) { "maxKeptFileGrants must be >= 0, was $maxKeptFileGrants" }
    }

    private companion object {
        const val DEFAULT_FINGERPRINT_HEAD_BYTES = 1 shl 20 // 1 MiB
        const val DEFAULT_PDF_HEADER_WINDOW_BYTES = 1024
        const val DEFAULT_MAX_KEPT_FILE_GRANTS = 500
    }
}
