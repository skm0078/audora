// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/extract/PdfTextString.kt
// Purpose: Decodes a PDF text string (e.g. an /ActualText value) from its raw bytes (PDF 32000 §7.9.2.2).

package io.github.skm0078.audora.pdf.extract

object PdfTextString {
    /** UTF-16BE with a BOM, UTF-8 with a BOM (PDF 2.0), otherwise PDFDocEncoding. */
    fun decode(bytes: ByteArray): String = when {
        bytes.startsWith(
            UTF16BE_BOM,
        ) -> String(bytes, UTF16BE_BOM.size, bytes.size - UTF16BE_BOM.size, Charsets.UTF_16BE)

        bytes.startsWith(UTF8_BOM) -> String(bytes, UTF8_BOM.size, bytes.size - UTF8_BOM.size, Charsets.UTF_8)

        else -> decodePdfDocEncoding(bytes)
    }

    private fun decodePdfDocEncoding(bytes: ByteArray): String {
        val out = StringBuilder(bytes.size)
        for (b in bytes) out.append(pdfDocChar(b.toInt() and BYTE_MASK))
        return out.toString()
    }

    /** PDFDocEncoding equals Latin-1 except in two ranges and two undefined codes (PDF 32000 Annex D.2). */
    private fun pdfDocChar(code: Int): Char = when (code) {
        in LOW_RANGE_START until LOW_RANGE_START + LOW_RANGE.length -> LOW_RANGE[code - LOW_RANGE_START]
        in HIGH_RANGE_START until HIGH_RANGE_START + HIGH_RANGE.length -> HIGH_RANGE[code - HIGH_RANGE_START]
        DELETE, SOFT_HYPHEN_SLOT -> REPLACEMENT
        else -> code.toChar()
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private const val BYTE_MASK = 0xFF
    private val UTF16BE_BOM = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
    private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    private const val REPLACEMENT = '\uFFFD'
    private const val DELETE = 0x7F
    private const val SOFT_HYPHEN_SLOT = 0xAD

    /** 0x18–0x1F: spacing accents. */
    private const val LOW_RANGE_START = 0x18
    private const val LOW_RANGE = "\u02D8\u02C7\u02C6\u02D9\u02DD\u02DB\u02DA\u02DC"

    /** 0x80–0xA0: typographic punctuation, ligatures, extra Latin letters, euro; 0x9F is undefined. */
    private const val HIGH_RANGE_START = 0x80
    private const val HIGH_RANGE =
        "\u2022\u2020\u2021\u2026\u2014\u2013\u0192\u2044\u2039\u203A\u2212\u2030\u201E\u201C\u201D\u2018" +
            "\u2019\u201A\u2122\uFB01\uFB02\u0141\u0152\u0160\u0178\u017D\u0131\u0142\u0153\u0161\u017E\uFFFD" +
            "\u20AC"
}
