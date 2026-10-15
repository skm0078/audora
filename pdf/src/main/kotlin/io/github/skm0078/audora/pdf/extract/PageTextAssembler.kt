// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/extract/PageTextAssembler.kt
// Purpose: Turns PDFium's raw characters into PageText, applying the /ActualText rebuild plan.

package io.github.skm0078.audora.pdf.extract

import io.github.skm0078.audora.core.config.ExtractionConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.TextBox

class PageTextAssembler(config: ExtractionConfig = ExtractionConfig()) {
    private val rebuilder = ActualTextRebuilder(config)

    fun assemble(page: RawPage): PageText {
        val chars = page.chars
        val plan = rebuilder.plan(page)
        if (plan.status == ActualTextRebuild.ALIGNMENT_FAILED) {
            AudoraLog.w(TAG, "ActualText rebuild skipped: text objects did not align, page=${page.pageIndex}")
        }
        val spanBoxes = plan.spans.map { span -> unionBox(chars, span) }
        val out = Output(chars)
        for (i in 0 until chars.size) {
            val spanIndex = plan.spanOf(i)
            if (spanIndex == ActualTextPlan.NO_SPAN) {
                out.appendChar(i)
                continue
            }
            val span = plan.spans[spanIndex]
            if (i == span.firstChar) out.appendText(span.text, spanBoxes[spanIndex], fontOf = i)
            // A span never swallows a line break; layout (P2-02) needs it.
            if (chars.isGenerated(i) && isLineBreak(chars.codePoint(i))) out.appendChar(i)
            if (i == span.lastChar && span.spaceAfter) out.appendText(" ", box = null, fontOf = null)
        }
        return PageText(
            pageIndex = page.pageIndex,
            text = out.text.toString(),
            boxes = out.boxes.toArray(),
            fontRuns = out.finishRuns(),
            stats = statsOf(chars, plan),
        )
    }

    private fun unionBox(chars: RawChars, span: ActualTextSpan): TextBox? {
        var box: TextBox? = null
        for (i in span.firstChar..span.lastChar) {
            val b = if (chars.isGenerated(i)) null else chars.box(i)
            if (b != null) box = box?.union(b) ?: b
        }
        return box
    }

    private fun statsOf(chars: RawChars, plan: ActualTextPlan): PageTextStats {
        var generated = 0
        var mapErrors = 0
        var invisible = 0
        for (i in 0 until chars.size) {
            if (chars.isGenerated(i)) generated++
            if (chars.hasUnicodeMapError(i)) mapErrors++
            if (chars.renderMode(i) == RawChars.RENDER_MODE_INVISIBLE) invisible++
        }
        return PageTextStats(
            pdfiumChars = chars.size,
            generatedChars = generated,
            unicodeMapErrors = mapErrors,
            invisibleChars = invisible,
            actualTextSpans = plan.spans.size,
            actualTextRebuild = plan.status,
        )
    }

    /** Accumulates text, a box per UTF-16 unit and font runs. */
    private class Output(private val chars: RawChars) {
        val text = StringBuilder(chars.size)
        val boxes = FloatArrayBuilder(chars.size * RawChars.BOX_FLOATS)
        private val runs = ArrayList<FontRun>()
        private var runStart = 0
        private var runFontId = RawChars.NO_FONT
        private var runFontSize = 0f

        fun appendChar(i: Int) {
            val before = text.length
            text.appendCodePoint(safeCodePoint(chars.codePoint(i)))
            val box = if (chars.isGenerated(i)) null else chars.box(i)
            repeat(text.length - before) { addBox(box) }
            if (!chars.isGenerated(i)) switchFont(before, i)
        }

        fun appendText(s: String, box: TextBox?, fontOf: Int?) {
            val before = text.length
            text.append(s)
            repeat(s.length) { addBox(box) }
            if (fontOf != null) switchFont(before, fontOf)
        }

        /** Starts a new run at [offset] when char [i]'s font or size differs from the current run. */
        private fun switchFont(offset: Int, i: Int) {
            val id = chars.fontId(i)
            val size = chars.fontSize(i)
            if (id == runFontId && size == runFontSize) return
            closeRun(offset)
            runStart = offset
            runFontId = id
            runFontSize = size
        }

        private fun closeRun(end: Int) {
            if (runFontId != RawChars.NO_FONT && end > runStart) {
                runs.add(FontRun(runStart, end, chars.fontNames[runFontId], runFontSize))
            }
        }

        fun finishRuns(): List<FontRun> {
            closeRun(text.length)
            return runs
        }

        private fun addBox(box: TextBox?) {
            if (box == null) {
                repeat(RawChars.BOX_FLOATS) { boxes.add(Float.NaN) }
            } else {
                boxes.add(box.left)
                boxes.add(box.top)
                boxes.add(box.right)
                boxes.add(box.bottom)
            }
        }
    }

    private companion object {
        const val TAG = "Audora.PageText"

        fun isLineBreak(codePoint: Int) = codePoint == '\n'.code || codePoint == '\r'.code
    }
}
