// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/extract/RawPage.kt
// Purpose: What the native reader returns for one page: PDFium's characters and the page's text objects.

package io.github.skm0078.audora.pdf.extract

import io.github.skm0078.audora.core.model.TextBox

/** One page as PDFium sees it, before any repair. Built by the Android adapter, consumed by pure code. */
class RawPage(
    val pageIndex: Int,
    val chars: RawChars,
    /** Text objects in content-stream order, form XObjects flattened depth-first. */
    val textObjects: List<RawTextObject>,
)

/**
 * PDFium's text-page characters as parallel arrays (one entry per PDFium char, not per UTF-16 unit), so a
 * 5,000-glyph page costs a few arrays instead of 5,000 objects.
 */
class RawChars(
    /** Unicode code points (UTF-32), as FPDFText_GetUnicode returns them. */
    private val codePoints: IntArray,
    /** Bit flags, see [FLAG_GENERATED], [FLAG_UNICODE_MAP_ERROR], [RENDER_MODE_SHIFT]. */
    private val flags: IntArray,
    /** Loose char boxes, 4 floats per char (left, top, right, bottom); NaN when PDFium has none. */
    private val boxes: FloatArray,
    private val fontSizes: FloatArray,
    /** Index into [fontNames], or [NO_FONT] (generated characters have no font). */
    private val fontIds: IntArray,
    val fontNames: List<String>,
) {
    init {
        val n = codePoints.size
        require(flags.size == n && fontSizes.size == n && fontIds.size == n) { "char arrays differ in length" }
        require(boxes.size == n * BOX_FLOATS) { "boxes must hold $BOX_FLOATS floats per char" }
    }

    val size: Int get() = codePoints.size

    fun codePoint(i: Int): Int = codePoints[i]

    fun isGenerated(i: Int): Boolean = flags[i] and FLAG_GENERATED != 0

    fun hasUnicodeMapError(i: Int): Boolean = flags[i] and FLAG_UNICODE_MAP_ERROR != 0

    /** PDF text render mode 0–7, or [RENDER_MODE_UNKNOWN]. 3 = invisible (hidden OCR layers). */
    fun renderMode(i: Int): Int = (flags[i] ushr RENDER_MODE_SHIFT) and RENDER_MODE_MASK

    fun fontSize(i: Int): Float = fontSizes[i]

    fun fontId(i: Int): Int = fontIds[i]

    fun box(i: Int): TextBox? {
        val o = i * BOX_FLOATS
        val left = boxes[o]
        if (left.isNaN()) return null
        return TextBox(left = left, top = boxes[o + 1], right = boxes[o + 2], bottom = boxes[o + 3])
    }

    companion object {
        const val BOX_FLOATS = 4
        const val NO_FONT = -1
        const val FLAG_GENERATED = 1
        const val FLAG_UNICODE_MAP_ERROR = 2
        const val RENDER_MODE_SHIFT = 4
        const val RENDER_MODE_MASK = 0xF
        const val RENDER_MODE_UNKNOWN = RENDER_MODE_MASK
        const val RENDER_MODE_INVISIBLE = 3
        private const val RENDER_MODE_MAX = 7

        fun flagsOf(generated: Boolean, unicodeMapError: Boolean, renderMode: Int): Int {
            val mode = if (renderMode in 0..RENDER_MODE_MAX) renderMode else RENDER_MODE_UNKNOWN
            return (if (generated) FLAG_GENERATED else 0) or
                (if (unicodeMapError) FLAG_UNICODE_MAP_ERROR else 0) or
                (mode shl RENDER_MODE_SHIFT)
        }
    }
}

/**
 * @property text the object's characters as PDFium's text page reports them (FPDFTextObj_GetText), which can
 *   include a generated trailing space.
 * @property actualText the `/ActualText` of the enclosing marked-content span, or null.
 * @property spanKey identifies the span: objects with the same non-zero key share one `/ActualText`.
 * @property width object bounds width in points; a zero-width empty object is a space glyph.
 */
data class RawTextObject(val text: String, val actualText: String?, val spanKey: Long, val width: Float)
