// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/ocr/OcrParagraphKindsTest.kt
// Purpose: Scanned pages get headings from OCR's type sizes and list items from bullets OCR misread (RI-10).

package io.github.skm0078.audora.pdf.ocr

import io.github.skm0078.audora.core.model.UnitKind
import io.github.skm0078.audora.pdf.render.PageTransform
import io.github.skm0078.audora.pdf.render.ViewRect
import io.github.skm0078.audora.pdf.units.ReadingUnitBuilder
import org.junit.Assert.assertEquals
import org.junit.Test

class OcrParagraphKindsTest {
    private val builder = ReadingUnitBuilder()
    private val transform = PageTransform.upright(PAGE_WIDTH, PAGE_HEIGHT)

    /** One OCR line of [text]'s words at row [row], in Tesseract paragraph [paragraph], at [size] and [confidence]. */
    private fun line(text: String, row: Int, paragraph: Int, size: Float = BODY, confidence: Int = CONFIDENT): OcrLine {
        val top = TOP + row * ROW_PITCH
        val words = text.split(' ').mapIndexed { i, word ->
            OcrWord(
                word,
                ViewRect(LEFT + i * WORD_PITCH, top, LEFT + i * WORD_PITCH + WORD_WIDTH, top + size),
                confidence,
            )
        }
        return OcrLine(words, paragraph, size)
    }

    private val body = listOf(
        "Plants make their food from light and water every day.",
        "The leaves hold the green matter that catches the light.",
        "Roots take water up from the soil into the stem.",
    )

    /** [first] as Tesseract paragraph 0, then three body lines as paragraph 1. */
    private fun kinds(first: OcrLine): List<Pair<UnitKind, String>> {
        val lines = listOf(first) + body.mapIndexed { i, text -> line(text, i + 1, 1) }
        val units = builder.build(builder.ocrReadingText(PAGE, OcrText(lines)) { transform.toPage(it) })
        return units.map { it.kind to it.text }
    }

    @Test
    fun `a large, cleanly read line is a heading`() {
        assertEquals(UnitKind.HEADING to "How plants feed", kinds(line("How plants feed", 0, 0, LARGE)).first())
        assertEquals(
            List(body.size) {
                UnitKind.BODY
            },
            kinds(line("How plants feed", 0, 0, LARGE)).drop(1).map { it.first },
        )
    }

    @Test
    fun `large lines that do not read like headings stay body text`() {
        val cases = listOf(
            "unsure read" to line("How plants feed", 0, 0, LARGE, confidence = UNSURE),
            "a sentence" to line("Plants feed on light.", 0, 0, LARGE),
            "a pointer" to line("> click here", 0, 0, LARGE),
            "a table bar" to line("Name | Score", 0, 0, LARGE),
            "body-sized" to line("How plants feed", 0, 0),
        )
        for ((name, first) in cases) assertEquals(name, UnitKind.BODY, kinds(first).first().first)
    }

    @Test
    fun `a heading at the top of a paragraph is split from the body text under it`() {
        val lines = listOf(line("How plants feed", 0, 0, LARGE)) + body.mapIndexed { i, text -> line(text, i + 1, 0) }
        val units = builder.build(builder.ocrReadingText(PAGE, OcrText(lines)) { transform.toPage(it) })
        assertEquals(listOf(UnitKind.HEADING) + List(body.size) { UnitKind.BODY }, units.map { it.kind })
    }

    @Test
    fun `bullets OCR read as letters, zeros or signs start list items and are not read`() {
        val misreads = listOf("o", "०", "০", "०.", "०»", "*", "+", "©", "°")
        for (bullet in misreads) {
            val lines = listOf(line("Leaves are green.", 0, 0), line("$bullet water from the roots", 1, 0))
            val units = builder.build(builder.ocrReadingText(PAGE, OcrText(lines)) { transform.toPage(it) })
            assertEquals(bullet, UnitKind.LIST_ITEM to "water from the roots", units.last().kind to units.last().text)
        }
    }

    @Test
    fun `a lone sign before a number or a word that is not a bullet is kept`() {
        val lines = listOf(line("Add the two numbers.", 0, 0), line("+ 5 more", 1, 1), line("only one", 2, 2))
        val units = builder.build(builder.ocrReadingText(PAGE, OcrText(lines)) { transform.toPage(it) })
        assertEquals(listOf("Add the two numbers.", "+ 5 more", "only one"), units.map { it.text })
        assertEquals(List(units.size) { UnitKind.BODY }, units.map { it.kind })
    }

    @Test
    fun `without type sizes nothing is a heading`() {
        val units = builder.build(builder.ocrReadingText(PAGE, "How plants feed\n\n${body.joinToString("\n")}"))
        assertEquals(List(units.size) { UnitKind.BODY }, units.map { it.kind })
    }

    private companion object {
        const val PAGE = 4
        const val PAGE_WIDTH = 600f
        const val PAGE_HEIGHT = 800f
        const val BODY = 0.015f
        const val LARGE = 0.025f
        const val CONFIDENT = 95
        const val UNSURE = 50
        const val TOP = 0.1f
        const val LEFT = 0.05f
        const val ROW_PITCH = 0.04f
        const val WORD_PITCH = 0.08f
        const val WORD_WIDTH = 0.07f
    }
}
