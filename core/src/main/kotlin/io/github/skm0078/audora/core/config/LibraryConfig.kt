// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/LibraryConfig.kt
// Purpose: Tunables for the library: how a book's content fingerprint is taken and how a PDF is recognized (P1-01).

package io.github.skm0078.audora.core.config

/**
 * @property fingerprintHeadBytes how much of the start of a file is hashed into its fingerprint (with its size).
 *   Enough to tell real books apart, small enough that adding a 40 MB scan stays instant.
 * @property pdfHeaderWindowBytes the `%PDF-` marker must start within this many bytes. The PDF spec lets
 *   readers accept junk before it, and Acrobat accepts up to 1024 bytes.
 */
data class LibraryConfig(
    val fingerprintHeadBytes: Int = DEFAULT_FINGERPRINT_HEAD_BYTES,
    val pdfHeaderWindowBytes: Int = DEFAULT_PDF_HEADER_WINDOW_BYTES,
) {
    init {
        require(pdfHeaderWindowBytes > 0) { "pdfHeaderWindowBytes must be > 0, was $pdfHeaderWindowBytes" }
        require(fingerprintHeadBytes >= pdfHeaderWindowBytes) {
            "fingerprintHeadBytes ($fingerprintHeadBytes) must cover pdfHeaderWindowBytes ($pdfHeaderWindowBytes)"
        }
    }

    private companion object {
        const val DEFAULT_FINGERPRINT_HEAD_BYTES = 1 shl 20 // 1 MiB
        const val DEFAULT_PDF_HEADER_WINDOW_BYTES = 1024
    }
}
