// File: core/src/main/kotlin/io/github/skm0078/audora/core/library/DocumentFormat.kt
// Purpose: Tells PDF, EPUB, Word, OpenDocument, RTF, HTML, FB2, MOBI, CHM and text books apart by their first bytes
//          (ADR-042, ADR-066, ADR 2026-10-26-more-ebook-formats); comic archives are told apart too, to become photo
//          books.

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

    /** A FictionBook (.fb2), or one zipped alone (.fb2.zip). */
    FB2,

    /** A Kindle book without DRM (.mobi, .azw3, .azw, .prc) or an old PalmDOC (.pdb); a DRM-locked one is refused. */
    MOBI,

    /** A compiled Windows help file (.chm): HTML pages in Microsoft's container. */
    CHM,
    ;

    /** Read by the text-book path, not PDFium. */
    val isText: Boolean get() = this != PDF
}

@Suppress("TooManyFunctions") // One small sniffer per container kind.
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
    private const val FB2_START = "<fictionbook"
    private const val FB2_ENTRY = ".fb2"
    private val CHM_MAGIC = "ITSF".toByteArray(Charsets.US_ASCII)

    /** A Palm database's type and creator at [PDB_TYPE_OFFSET]: a Mobipocket or Kindle book, or a PalmDOC. */
    private val PDB_BOOK_TYPES = listOf("BOOKMOBI", "TEXtREAd").map { it.toByteArray(Charsets.US_ASCII) }
    private const val PDB_TYPE_OFFSET = 60

    /** Pages of a comic archive (.cbz): the pictures Android decodes, and the ComicInfo.xml some tools add first. */
    private val COMIC_ENTRY_ENDINGS = listOf(".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp", "comicinfo.xml")
    private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    private val UTF16_BOMS =
        listOf(byteArrayOf(0xFE.toByte(), 0xFF.toByte()), byteArrayOf(0xFF.toByte(), 0xFE.toByte()))
    private const val ASCII_DEL = 0x7F
    private const val ASCII_SPACE = 0x20
    private const val BYTE_MASK = 0xFF
    private const val BITS_PER_BYTE = 8

    // A ZIP local file header (APPNOTE 4.3.7): flags, compressed size, name and extra-field lengths, then the name.
    private const val ZIP_FLAGS_OFFSET = 6
    private const val ZIP_COMPRESSED_SIZE_OFFSET = 18
    private const val ZIP_NAME_LENGTH_OFFSET = 26
    private const val ZIP_EXTRA_LENGTH_OFFSET = 28
    private const val ZIP_NAME_OFFSET = 30
    private const val ZIP_DATA_DESCRIPTOR_FLAG = 0x08

    /** An empty deflate stream and a data descriptor with its signature and 64-bit sizes fit in this. */
    private const val FOLDER_TAIL_MAX = 32

    /** Control characters a text file may hold: tab, line feed, carriage return, form feed. */
    private val TEXT_CONTROLS = setOf('\t'.code, '\n'.code, '\r'.code, '\u000C'.code)

    /** At most one control character (other than tab and line breaks) per this many bytes, or it is not text. */
    private const val TEXT_BYTES_PER_CONTROL = 100

    /**
     * The format of a file starting with [head]; null for anything else (an image, a spreadsheet, a plain ZIP).
     * PDF: `%PDF-` within [pdfWindowBytes] (readers accept junk before it). EPUB and ODT: a ZIP whose first entry is
     * the `mimetype` file naming the format, as both containers require. DOCX: a ZIP holding a Word package's
     * entries (the opener refuses one with no `word/document.xml`, such as a spreadsheet). RTF: `{\rtf`. HTML: text
     * that opens with an HTML doctype or tag (after a BOM, an XML declaration or comments). FB2: text that opens with
     * `<FictionBook`, or a ZIP whose first entry is a `.fb2`. MOBI: a Palm database of type `BOOKMOBI` (Mobipocket and
     * Kindle, AZW3 included) or `TEXtREAd` (PalmDOC). CHM: `ITSF`. Text: a byte-order mark,
     * or no NUL byte and almost no control characters (UTF-8, or an 8-bit encoding read as Windows-1252).
     */
    fun sniff(head: ByteArray, pdfWindowBytes: Int): DocumentFormat? = when {
        head.indexOf(PDF_MAGIC, limit = pdfWindowBytes) >= 0 -> DocumentFormat.PDF
        head.startsWith(ZIP_MAGIC) -> zipFormat(head)
        head.startsWith(CHM_MAGIC) -> DocumentFormat.CHM
        PDB_BOOK_TYPES.any { head.startsWith(it, at = PDB_TYPE_OFFSET) } -> DocumentFormat.MOBI
        head.withoutBom().startsWith(RTF_MAGIC) -> DocumentFormat.RTF
        head.startsWith(UTF8_BOM) || UTF16_BOMS.any { head.startsWith(it) } -> textFormat(head)
        looksLikeText(head) -> textFormat(head)
        else -> null
    }

    /** A ZIP's first bytes: a zipped FB2 is read through its archive, a plain one as it is. */
    fun isZip(head: ByteArray): Boolean = head.startsWith(ZIP_MAGIC)

    /**
     * A comic book archive (.cbz): a ZIP whose first entries are pictures (or ComicInfo.xml), not a book [sniff]
     * accepts. It is not a book itself: its pages become a photo book read by OCR (ADR 2026-10-26-more-ebook-formats).
     * Only the entry names in [head] are looked at, so a folder entry first is fine while the next name fits.
     */
    fun isComicArchive(head: ByteArray, pdfWindowBytes: Int): Boolean =
        head.startsWith(ZIP_MAGIC) && sniff(head, pdfWindowBytes) == null &&
            zipEntryNames(head).firstOrNull { !it.endsWith('/') }?.lowercase()
                ?.let { name -> COMIC_ENTRY_ENDINGS.any { name.endsWith(it) } } == true

    private fun zipFormat(head: ByteArray): DocumentFormat? = when {
        head.indexOf(EPUB_MIMETYPE) >= 0 -> DocumentFormat.EPUB
        head.indexOf(ODT_MIMETYPE) >= 0 -> DocumentFormat.ODT
        DOCX_MARKERS.any { head.indexOf(it) >= 0 } -> DocumentFormat.DOCX
        zipEntryNames(head).firstOrNull()?.lowercase()?.endsWith(FB2_ENTRY) == true -> DocumentFormat.FB2
        else -> null
    }

    /**
     * The names of the ZIP's entries whose local headers lie whole in [head]: each header gives its name's length
     * and, unless the sizes come after the data, how far the next header is.
     */
    private fun zipEntryNames(head: ByteArray): List<String> {
        val names = ArrayList<String>()
        var at = 0
        while (at >= 0 && head.startsWith(ZIP_MAGIC, at) && at + ZIP_NAME_OFFSET <= head.size) {
            val nameLength = head.u16(at + ZIP_NAME_LENGTH_OFFSET)
            val extraLength = head.u16(at + ZIP_EXTRA_LENGTH_OFFSET)
            val nameEnd = at + ZIP_NAME_OFFSET + nameLength
            if (nameLength == 0 || nameEnd > head.size) break
            val name = String(head, at + ZIP_NAME_OFFSET, nameLength, Charsets.UTF_8)
            names += name
            at = afterEntry(head, at, name, nameEnd + extraLength)
        }
        return names
    }

    /** Where the entry at [at] whose data starts at [dataStart] ends; -1 when its header does not say. */
    private fun afterEntry(head: ByteArray, at: Int, name: String, dataStart: Int): Int {
        val sizesFollowData = head.u16(at + ZIP_FLAGS_OFFSET) and ZIP_DATA_DESCRIPTOR_FLAG != 0
        return when {
            !sizesFollowData -> dataStart + head.u32(at + ZIP_COMPRESSED_SIZE_OFFSET)

            // A folder holds at most an empty deflate stream and its sizes: the next header is a few bytes on.
            name.endsWith('/') -> nextHeader(head, dataStart)

            else -> -1
        }
    }

    private fun nextHeader(head: ByteArray, from: Int): Int =
        (from..minOf(head.size, from + FOLDER_TAIL_MAX)).firstOrNull { head.startsWith(ZIP_MAGIC, it) } ?: -1

    private fun ByteArray.u16(at: Int): Int = (this[at].toInt() and BYTE_MASK) or
        ((this[at + 1].toInt() and BYTE_MASK) shl BITS_PER_BYTE)

    /** A little-endian 32-bit size; one over [Int.MAX_VALUE] is negative, which ends the walk. */
    private fun ByteArray.u32(at: Int): Int = u16(at) or (u16(at + 2) shl (2 * BITS_PER_BYTE))

    /** HTML, FB2 or plain text; a UTF-16 file is checked as text only (HTML files are UTF-8 in practice). */
    private fun textFormat(head: ByteArray): DocumentFormat {
        val first = firstElement(String(head.withoutBom(), Charsets.ISO_8859_1))
        return when {
            HTML_STARTS.any { first.startsWith(it, ignoreCase = true) } -> DocumentFormat.HTML
            first.startsWith(FB2_START, ignoreCase = true) -> DocumentFormat.FB2
            else -> DocumentFormat.TEXT
        }
    }

    /**
     * The text from its first tag, after spaces, an XML declaration and comments: `<!DOCTYPE html` or `<html` is a
     * saved page, `<FictionBook` an FB2, anything else prose.
     */
    private fun firstElement(text: String): String {
        var rest = text.trimStart()
        while (true) {
            rest = when {
                rest.startsWith("<?") -> rest.substringAfter("?>", "").trimStart()
                rest.startsWith("<!--") -> rest.substringAfter("-->", "").trimStart()
                else -> return rest
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

    private fun ByteArray.startsWith(prefix: ByteArray, at: Int = 0): Boolean =
        at >= 0 && size >= at + prefix.size && prefix.indices.all { this[at + it] == prefix[it] }

    private fun ByteArray.indexOf(needle: ByteArray, limit: Int = size): Int {
        val last = minOf(size, limit) - needle.size
        return (0..last).firstOrNull { start -> needle.indices.all { this[start + it] == needle[it] } } ?: -1
    }
}
