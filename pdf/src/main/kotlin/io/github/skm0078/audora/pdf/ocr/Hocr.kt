// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/ocr/Hocr.kt
// Purpose: Reads Tesseract's hOCR output into words with pixel boxes, lines, paragraphs and a confidence.

package io.github.skm0078.audora.pdf.ocr

import io.github.skm0078.audora.pdf.render.ViewRect

/** A word in image pixels (left, top, right, bottom; y down), with Tesseract's confidence 0–100. */
class RecognizedWord(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int, val conf: Int)

class RecognizedLine(val words: List<RecognizedWord>, val paragraph: Int)

/**
 * What an [OcrEngine] read in one image of [width] × [height] pixels. [meanConfidence] is the confidence of its
 * words weighted by their length, 0–100; 0 for an image without words.
 */
class RecognizedPage(val lines: List<RecognizedLine>, val width: Int, val height: Int) {
    val meanConfidence: Int by lazy {
        val words = lines.flatMap { it.words }
        val chars = words.sumOf { it.text.length }
        if (chars == 0) 0 else (words.sumOf { it.conf.toLong() * it.text.length } / chars).toInt()
    }

    /** Words with boxes in fractions of the image, turned back by [quarterTurns] onto the page as displayed. */
    fun toOcrText(quarterTurns: Int): OcrText = OcrText(
        lines = lines.map { line ->
            OcrLine(
                words = line.words.map { word ->
                    val rect = ViewRect(
                        left = word.left.toFloat() / width,
                        top = word.top.toFloat() / height,
                        right = word.right.toFloat() / width,
                        bottom = word.bottom.toFloat() / height,
                    )
                    OcrWord(word.text, turnView(rect, OcrText.FULL_TURN - quarterTurns))
                },
                paragraph = line.paragraph,
            )
        },
        quarterTurns = quarterTurns,
    )
}

/**
 * Tesseract's hOCR (`getHOCRText`): `ocr_par` paragraphs holding `ocr_line` lines (or `ocr_header`, `ocr_caption`,
 * `ocr_textfloat`) holding `ocrx_word` words, each with `title="bbox l t r b; x_wconf c"`. Words keep Tesseract's
 * reading order; a word outside any line or paragraph joins the last one seen. Malformed parts are skipped.
 */
object Hocr {
    private const val CLASSES = "ocr_par|ocr_line|ocr_header|ocr_caption|ocr_textfloat|ocrx_word"
    private val ELEMENT = Regex("""<(?:p|span|div)\s[^>]*class=['"]($CLASSES)['"][^>]*>""")

    /** Groups of [BBOX]. */
    private const val LEFT = 1
    private const val TOP = 2
    private const val RIGHT = 3
    private const val BOTTOM = 4
    private val TITLE = Regex("""title=(['"])(.*?)\1""")
    private val BBOX = Regex("""bbox\s+(-?\d+)\s+(-?\d+)\s+(-?\d+)\s+(-?\d+)""")
    private val CONFIDENCE = Regex("""x_wconf\s+(\d+)""")
    private val TAG = Regex("<[^>]*>")
    private const val WORD_END = "</span>"
    private val ENTITIES = listOf("&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&#39;" to "'", "&apos;" to "'")

    fun parse(hocr: String, width: Int, height: Int): RecognizedPage {
        val lines = ArrayList<RecognizedLine>()
        var words = ArrayList<RecognizedWord>()
        var paragraph = -1
        var lineParagraph = 0

        fun addWord(word: RecognizedWord) {
            if (words.isEmpty()) lineParagraph = paragraph.coerceAtLeast(0)
            words.add(word)
        }

        fun closeLine() {
            if (words.isNotEmpty()) lines.add(RecognizedLine(words, lineParagraph))
            words = ArrayList()
        }

        for (element in ELEMENT.findAll(hocr)) {
            when (element.groupValues[1]) {
                "ocr_par" -> {
                    closeLine()
                    paragraph++
                }

                "ocrx_word" -> word(hocr, element)?.let(::addWord)

                else -> closeLine()
            }
        }
        closeLine()
        return RecognizedPage(lines, width, height)
    }

    private fun word(hocr: String, element: MatchResult): RecognizedWord? {
        val start = element.range.last + 1
        val end = hocr.indexOf(WORD_END, start)
        val text = if (end < 0) "" else unescape(hocr.substring(start, end).replace(TAG, "")).trim()
        val title = TITLE.find(element.value)?.groupValues?.get(2).orEmpty()
        val box = BBOX.find(title)?.groupValues
        if (box == null || text.isEmpty()) return null
        return RecognizedWord(
            text = text,
            left = box[LEFT].toInt(),
            top = box[TOP].toInt(),
            right = box[RIGHT].toInt(),
            bottom = box[BOTTOM].toInt(),
            conf = CONFIDENCE.find(title)?.groupValues?.get(1)?.toInt() ?: 0,
        )
    }

    private fun unescape(text: String): String {
        var out = text
        for ((entity, char) in ENTITIES) out = out.replace(entity, char)
        return out.replace("&amp;", "&")
    }
}
