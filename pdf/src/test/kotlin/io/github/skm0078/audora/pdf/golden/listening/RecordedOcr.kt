// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/golden/listening/RecordedOcr.kt
// Purpose: The listening corpus's OCR on the JVM: Tesseract hOCR recorded on the desktop, one file per scanned page.

package io.github.skm0078.audora.pdf.golden.listening

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.map
import io.github.skm0078.audora.pdf.ocr.Hocr
import io.github.skm0078.audora.pdf.ocr.OcrMargins
import io.github.skm0078.audora.pdf.ocr.OcrText
import io.github.skm0078.audora.pdf.quality.OcrError
import io.github.skm0078.audora.pdf.quality.PageOcr
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * Reads `ocr/<id>.p<page>.hocr.gz` (scripts/pdf/golden_corpus.py `ocr`), parsed by the app's own [Hocr] reader. A page
 * without a recording fails like an OCR engine without models, so the pipeline reads its text layer. Recorded pages
 * are upright, so the phone's orientation check is not replayed; margin strips are the recorded lines that sit in the
 * strip, which is what the phone reads there.
 */
class RecordedOcr(private val dir: File, private val documentId: String, private val langs: Set<Lang>) : PageOcr {
    override fun recognize(pageIndex: Int, langs: Set<Lang>): Outcome<OcrText, OcrError> = recorded(pageIndex)

    override fun detectLangs(pageIndex: Int): Outcome<Set<Lang>, OcrError> = Outcome.Success(langs)

    override fun margins(
        pageIndex: Int,
        langs: Set<Lang>,
        quarterTurns: Int,
        share: Float,
    ): Outcome<OcrMargins, OcrError> {
        if (quarterTurns != 0) return Outcome.Failure(OcrError.Failed("recorded pages are upright"))
        return recorded(pageIndex).map { text ->
            fun linesWhere(inStrip: (Float) -> Boolean) = text.lines.filter { line ->
                val boxes = line.words.mapNotNull { it.box }
                boxes.isNotEmpty() && inStrip((boxes.minOf { it.top } + boxes.maxOf { it.bottom }) / 2)
            }.map { it.text }
            OcrMargins(top = linesWhere { it < share }, bottom = linesWhere { it > 1 - share })
        }
    }

    private fun recorded(pageIndex: Int): Outcome<OcrText, OcrError> {
        val file = dir.resolve("$documentId.p$pageIndex.hocr.gz")
        if (!file.isFile) return Outcome.Failure(OcrError.Failed("no recorded OCR for $documentId page $pageIndex"))
        val hocr = GZIPInputStream(file.inputStream()).bufferedReader(Charsets.UTF_8).use { it.readText() }
        // A recording without its page size is a broken fixture, not an OCR failure the pipeline should survive.
        val (width, height) = requireNotNull(PAGE_BOX.find(hocr)) { "$file has no ocr_page bbox" }.destructured
        return Outcome.Success(Hocr.parse(hocr, width.toInt(), height.toInt()).toOcrText(quarterTurns = 0))
    }

    private companion object {
        val PAGE_BOX = Regex("""class=['"]ocr_page['"][^>]*bbox\s+0\s+0\s+(\d+)\s+(\d+)""")
    }
}
