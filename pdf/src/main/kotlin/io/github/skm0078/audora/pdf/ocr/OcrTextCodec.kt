// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/ocr/OcrTextCodec.kt
// Purpose: Writes OcrText to the OCR cache and reads it back: one line of words per text line, boxes included.

package io.github.skm0078.audora.pdf.ocr

import io.github.skm0078.audora.pdf.render.ViewRect
import java.util.Locale

/**
 * A small text format, so a cached page stays a few kilobytes and readable in a debugger:
 * ```
 * audora-ocr 3 <quarterTurns>
 * R\t<left,top,right,bottom>\t<left,top,right,bottom>…
 * <paragraph>\t<left,top,right,bottom or ->[@confidence]\t<word>\t<box>\t<word>…
 * ```
 * The `R` row holds the page's rules and is left out when it has none. Version 2 (before RI-4a part 2) had no rules
 * and version 1 (before RI-2a) no confidence; both still decode, without them. Words never hold a tab or
 * line break (they are split on whitespace). Anything else is a decoding failure, which the cache treats as a miss:
 * the page is recognised again.
 */
internal object OcrTextCodec {
    private const val MAGIC = "audora-ocr"
    private const val VERSION = "3"
    private val READABLE = setOf("1", "2", VERSION)
    private const val RULES = "R"
    private const val CONFIDENCE = '@'
    private const val SEPARATOR = '\t'
    private const val NO_BOX = "-"
    private const val BOX_PARTS = 4
    private const val RIGHT = 2
    private const val BOTTOM = 3
    private const val HEADER_PARTS = 3
    private val CONTROL = Regex("[\\t\\r\\n]")

    fun encode(text: OcrText): String {
        val out = StringBuilder("$MAGIC $VERSION ${text.quarterTurns}\n")
        if (text.rules.isNotEmpty()) {
            out.append(RULES)
            for (rule in text.rules) out.append(SEPARATOR).append(box(rule))
            out.append('\n')
        }
        for (line in text.lines) {
            out.append(line.paragraph)
            for (word in line.words) {
                out.append(SEPARATOR).append(box(word.box))
                word.confidence?.let { out.append(CONFIDENCE).append(it) }
                out.append(SEPARATOR).append(word.text.replace(CONTROL, " "))
            }
            out.append('\n')
        }
        return out.toString()
    }

    /** Null when [encoded] is not this format (an older cache file, a torn write). */
    fun decode(encoded: String): OcrText? {
        val rows = encoded.lines().filter { it.isNotEmpty() }
        val turns = rows.firstOrNull()?.let(::turnsOf)
        val ruleRow = rows.getOrNull(1)?.takeIf { it.startsWith(RULES + SEPARATOR) }
        val rules = ruleRow?.split(SEPARATOR)?.drop(1)?.map(::rect).orEmpty()
        val lines = rows.drop(if (ruleRow == null) 1 else 2).map(::lineOf)
        val valid = turns != null && lines.none { it == null } && rules.none { it == null }
        return if (valid) OcrText(lines.filterNotNull(), requireNotNull(turns), rules.filterNotNull()) else null
    }

    private fun turnsOf(header: String): Int? {
        val parts = header.split(' ')
        val valid = parts.size == HEADER_PARTS && parts[0] == MAGIC && parts[1] in READABLE
        return parts.last().toIntOrNull()?.takeIf { valid && it in 0 until OcrText.FULL_TURN }
    }

    private fun lineOf(row: String): OcrLine? {
        val parts = row.split(SEPARATOR)
        val paragraph = parts[0].toIntOrNull()
        val words = (1 until parts.size - 1 step 2).map { i -> wordOf(parts[i], parts[i + 1]) }
        val valid = paragraph != null && parts.size % 2 == 1 && words.none { it == null }
        return if (valid) OcrLine(words.filterNotNull(), requireNotNull(paragraph)) else null
    }

    private fun wordOf(field: String, text: String): OcrWord? {
        val box = field.substringBefore(CONFIDENCE)
        val confidence = if (CONFIDENCE in field) field.substringAfter(CONFIDENCE).toIntOrNull() else null
        val validConfidence = CONFIDENCE !in field || (confidence != null && confidence in 0..OcrWord.MAX_CONFIDENCE)
        return when {
            !validConfidence -> null
            box == NO_BOX -> OcrWord(text, null, confidence)
            else -> rect(box)?.let { OcrWord(text, it, confidence) }
        }
    }

    private fun box(rect: ViewRect?): String = rect?.let {
        String.format(Locale.ROOT, "%.5f,%.5f,%.5f,%.5f", it.left, it.top, it.right, it.bottom)
    } ?: NO_BOX

    private fun rect(text: String): ViewRect? {
        val values = text.split(',').mapNotNull { it.toFloatOrNull() }
        val valid = values.size == BOX_PARTS && values[0] <= values[RIGHT] && values[1] <= values[BOTTOM]
        return if (valid) ViewRect(values[0], values[1], values[RIGHT], values[BOTTOM]) else null
    }
}
