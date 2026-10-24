// File: core/src/main/kotlin/io/github/skm0078/audora/core/library/DocumentFormat.kt
// Purpose: Tells PDF, EPUB, Word, OpenDocument, RTF, HTML and text books apart by their first bytes (ADR-042, ADR-066).

package io.github.skm0078.audora.core.library

/**
 * The kinds of book Audora reads. The library stores no format: it is read from the file's head when needed.
 * Everything but [PDF] is a text book (ADR-042), read whole and cut into pseudo-pages. Markdown is [TEXT]: it is
 * told from plain text by its content, once the whole file is read (ADR-066).
 */
enum class DocumentFormat {
    PDF,
    EPUB,
    TEXT,

    /** A Word document (.docx, Office Open XML). Old binary .doc files are not read. */
    DOCX,

    /** An OpenDocument text (.odt) from LibreOffice or Google Docs' download. */
    ODT,
    RTF,

    /** A saved web page or an HTML file; never fetched, only read from the phone. */
    HTML,
    ;

    /** Read by the text-book path, not PDFium. */
    val isText: Boolean get() = this != PDF
}

object DocumentFormats {
    private val PDF_MAGIC = "%PDF-".toByteArray(Charsets.US_ASCII)
    private val ZIP_MAGIC = "PK\u0003\u0004".toByteArray(Charsets.US_ASCII)
    private val EPUB_MIMETYPE = "application/epub+zip".toByteArray(Charsets.US_ASCII)
    private val ODT_MIMETYPE = "application/vnd.oasis.opendocument.text".toByteArray(Charsets.US_ASCII)

    /** Entry names of a Word file's package; one of them is in its first local header in practice. */
    private val DOCX_MARKERS = listOf("[Content_Types].xml", "word/", "_rels/.rels").map {
        it.toByteArray(Charsets.US_ASCII)
    }
    private val RTF_MAGIC = "{\\rtf".toByteArray(Charsets.US_ASCII)
    private val HTML_STARTS = listOf("<!doctype html", "<html")
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
     * The format of a file starting with [head]; null for anything else (an image, a spreadsheet, a plain ZIP).
     * PDF: `%PDF-` within [pdfWindowBytes] (readers accept junk before it). EPUB and ODT: a ZIP whose first entry is
     * the `mimetype` file naming the format, as both containers require. DOCX: a ZIP holding a Word package's
     * entries (the opener refuses one with no `word/document.xml`, such as a spreadsheet). RTF: `{\rtf`. HTML: text
     * that opens with an HTML doctype or tag (after a BOM, an XML declaration or comments). Text: a byte-order mark,
     * or no NUL byte and almost no control characters (UTF-8, or an 8-bit encoding read as Windows-1252).
     */
    fun sniff(head: ByteArray, pdfWindowBytes: Int): DocumentFormat? = when {
        head.indexOf(PDF_MAGIC, limit = pdfWindowBytes) >= 0 -> DocumentFormat.PDF
        head.startsWith(ZIP_MAGIC) -> zipFormat(head)
        head.withoutBom().startsWith(RTF_MAGIC) -> DocumentFormat.RTF
        head.startsWith(UTF8_BOM) || UTF16_BOMS.any { head.startsWith(it) } -> textFormat(head)
        looksLikeText(head) -> textFormat(head)
        else -> null
    }

    private fun zipFormat(head: ByteArray): DocumentFormat? = when {
        head.indexOf(EPUB_MIMETYPE) >= 0 -> DocumentFormat.EPUB
        head.indexOf(ODT_MIMETYPE) >= 0 -> DocumentFormat.ODT
        DOCX_MARKERS.any { head.indexOf(it) >= 0 } -> DocumentFormat.DOCX
        else -> null
    }

    /** HTML or plain text; a UTF-16 file is checked as text only (HTML files are UTF-8 in practice). */
    private fun textFormat(head: ByteArray): DocumentFormat =
        if (opensAsHtml(String(head.withoutBom(), Charsets.ISO_8859_1))) DocumentFormat.HTML else DocumentFormat.TEXT

    /** `<!DOCTYPE html` or `<html` first, after spaces, an XML declaration and comments: a saved page, not prose. */
    private fun opensAsHtml(text: String): Boolean {
        var rest = text.trimStart()
        while (true) {
            rest = when {
                rest.startsWith("<?") -> rest.substringAfter("?>", "").trimStart()
                rest.startsWith("<!--") -> rest.substringAfter("-->", "").trimStart()
                else -> return HTML_STARTS.any { rest.startsWith(it, ignoreCase = true) }
            }
        }
    }

    private fun ByteArray.withoutBom(): ByteArray = if (startsWith(UTF8_BOM)) copyOfRange(UTF8_BOM.size, size) else this

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
