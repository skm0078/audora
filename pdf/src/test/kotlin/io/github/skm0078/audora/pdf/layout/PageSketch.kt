// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/layout/PageSketch.kt
// Purpose: Builds synthetic pages line by line at given positions, in any content-stream order, for layout tests.

package io.github.skm0078.audora.pdf.layout

import io.github.skm0078.audora.pdf.extract.PageText
import io.github.skm0078.audora.pdf.extract.PageTextAssembler
import io.github.skm0078.audora.pdf.extract.RawChars
import io.github.skm0078.audora.pdf.extract.RawPage

/**
 * Lines are emitted in call order, as a content stream would draw them, each followed by PDFium's generated
 * CR LF. Glyphs are [CHAR_WIDTH] × size wide; spaces are generated (no box) and advance [SPACE_WIDTH] × size.
 * Boxes are loose: [BOX_HEIGHT] × size tall, top at [line]'s `top`.
 */
class PageSketch(private val boxes: Boolean = true) {
    private val codePoints = ArrayList<Int>()
    private val flags = ArrayList<Int>()
    private val boxFloats = ArrayList<Float>()
    private val sizes = ArrayList<Float>()
    private val fontIds = ArrayList<Int>()

    fun line(text: String, left: Float, top: Float, size: Float = BODY, font: Int = REGULAR): PageSketch {
        var x = left
        for (ch in text) {
            if (ch == ' ') {
                generated(' ')
                x += size * SPACE_WIDTH
                continue
            }
            val right = x + size * CHAR_WIDTH
            add(ch.code, floatArrayOf(x, top, right, top - size * BOX_HEIGHT), size, font)
            x = right
        }
        generated('\r')
        generated('\n')
        return this
    }

    /**
     * One line of [pieces] set side by side, each a text, a size and a rise: how far its glyphs' bottoms sit above
     * the line's baseline (a superscript; negative for a subscript). The baseline is where a [BODY]-sized glyph
     * topped at [top] ends.
     */
    fun mixed(pieces: List<Triple<String, Float, Float>>, left: Float, top: Float): PageSketch {
        val baseline = top - BODY * BOX_HEIGHT
        var x = left
        for ((text, size, rise) in pieces) {
            for (ch in text) {
                if (ch == ' ') {
                    generated(' ')
                    x += size * SPACE_WIDTH
                    continue
                }
                val right = x + size * CHAR_WIDTH
                val bottom = baseline + rise
                add(ch.code, floatArrayOf(x, bottom + size * BOX_HEIGHT, right, bottom), size, REGULAR)
                x = right
            }
        }
        generated('\r')
        generated('\n')
        return this
    }

    /** Glyphs at explicit left edges, in storage order: for scripts drawn out of storage order. */
    fun glyphs(placed: List<Pair<Char, Float>>, top: Float, size: Float = BODY): PageSketch {
        for ((ch, left) in placed) {
            add(
                ch.code,
                floatArrayOf(
                    left,
                    top,
                    left + size * CHAR_WIDTH,
                    top - size * BOX_HEIGHT,
                ),
                size,
                REGULAR,
            )
        }
        generated('\r')
        generated('\n')
        return this
    }

    /** A word-wrapped paragraph: one [line] per entry, [pitch] apart. Returns the top below the last line. */
    fun lines(texts: List<String>, left: Float, top: Float, pitch: Float = BODY * 1.2f, size: Float = BODY): Float {
        texts.forEachIndexed { i, text -> line(text, left, top - i * pitch, size) }
        return top - texts.size * pitch
    }

    fun build(pageIndex: Int = 0): PageText = PageTextAssembler().assemble(
        RawPage(
            pageIndex = pageIndex,
            chars = RawChars(
                codePoints = codePoints.toIntArray(),
                flags = flags.toIntArray(),
                boxes = boxFloats.toFloatArray(),
                fontSizes = sizes.toFloatArray(),
                fontIds = fontIds.toIntArray(),
                fontNames = FONT_NAMES,
            ),
            textObjects = emptyList(),
        ),
    )

    private fun add(codePoint: Int, box: FloatArray, size: Float, font: Int) {
        codePoints.add(codePoint)
        flags.add(RawChars.flagsOf(generated = false, unicodeMapError = false, renderMode = 0))
        (if (boxes) box else FloatArray(RawChars.BOX_FLOATS) { Float.NaN }).forEach { boxFloats.add(it) }
        sizes.add(size)
        fontIds.add(font)
    }

    private fun generated(ch: Char) {
        codePoints.add(ch.code)
        flags.add(RawChars.flagsOf(generated = true, unicodeMapError = false, renderMode = 0))
        repeat(RawChars.BOX_FLOATS) { boxFloats.add(Float.NaN) }
        sizes.add(0f)
        fontIds.add(RawChars.NO_FONT)
    }

    companion object {
        const val BODY = 10f
        const val REGULAR = 0
        const val BOLD = 1
        const val CHAR_WIDTH = 0.5f
        const val SPACE_WIDTH = 0.25f
        const val BOX_HEIGHT = 1.15f
        val FONT_NAMES = listOf("ABCDEF+NotoSerif-Regular", "GHIJKL+NotoSerif-Bold")
    }
}
