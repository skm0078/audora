// File: core/src/main/kotlin/io/github/skm0078/audora/core/library/DocumentFormat.kt
// Purpose: Tells PDF, EPUB and plain-text books apart by their first bytes (plan items 42 and 43, ADR-042).

package io.github.skm0078.audora.core.library

/** The kinds of book Audora reads. The library stores no format: it is read from the file's head when needed. */
enum class DocumentFormat { PDF, EPUB, TEXT }

object DocumentFormats {
    private val PDF_MAGIC = "%PDF-".toByteArray(Charsets.US_ASCII)
    private val ZIP_MAGIC = "PK\u0003\u0004".toByteArray(Charsets.US_ASCII)
    private val EPUB_MIMETYPE = "application/epub+zip".toByteArray(Charsets.US_ASCII)
    private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    private val UTF16_BOMS =
        listOf(byteArrayOf(0xFE.toByte(), 0xFF.toByte()), byteArrayOf(0xFF.toByte(), 0xFE.toByte()))
    private const val ASCII_DEL = 0x7F
    private const val ASCII_SPACE = 0x20
    private const val BYTE_MASK = 0xFF

    /** Control characters a text file may hold: tab, line feed, carriage return, form feed. */
    private val TEXT_CONTROLS = setOf('\t'.code, '\n'.code, '\r'.code, '\u000C'.code)

    /** At most one control character (other than tab and line breaks) per this many bytes, or it is not text. */
    private const val TEXT_BYTES_PER_CONTROL = 100

    /**
     * The format of a file starting with [head]; null for anything else (a Word file, an image, a plain ZIP).
     * PDF: `%PDF-` within [pdfWindowBytes] (readers accept junk before it). EPUB: a ZIP whose first entry is the
     * `mimetype` file saying `application/epub+zip`, as the EPUB container requires. Text: a byte-order mark, or no
     * NUL byte and almost no control characters (UTF-8, or an 8-bit encoding read as Windows-1252).
     */
    fun sniff(head: ByteArray, pdfWindowBytes: Int): DocumentFormat? = when {
        head.indexOf(PDF_MAGIC, limit = pdfWindowBytes) >= 0 -> DocumentFormat.PDF
        head.startsWith(ZIP_MAGIC) -> DocumentFormat.EPUB.takeIf { head.indexOf(EPUB_MIMETYPE) >= 0 }
        head.startsWith(UTF8_BOM) || UTF16_BOMS.any { head.startsWith(it) } -> DocumentFormat.TEXT
        looksLikeText(head) -> DocumentFormat.TEXT
        else -> null
    }

    private fun looksLikeText(head: ByteArray): Boolean {
        val bytes = head.map { it.toInt() and BYTE_MASK }
        val controls = bytes.count { isStrayControl(it) }
        return bytes.isNotEmpty() && 0 !in bytes && controls * TEXT_BYTES_PER_CONTROL <= bytes.size
    }

    private fun isStrayControl(b: Int): Boolean = (b < ASCII_SPACE && b !in TEXT_CONTROLS) || b == ASCII_DEL

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private fun ByteArray.indexOf(needle: ByteArray, limit: Int = size): Int {
        val last = minOf(size, limit) - needle.size
        return (0..last).firstOrNull { start -> needle.indices.all { this[start + it] == needle[it] } } ?: -1
    }
}
