// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/ocr/OcrFootnotesTest.kt
// Purpose: Footnotes at the foot of a scanned page are tagged from OCR's line sizes and boxes (RI-5a).

package io.github.skm0078.audora.pdf.ocr

import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.UnitKind
import io.github.skm0078.audora.pdf.render.PageTransform
import io.github.skm0078.audora.pdf.render.ViewRect
import io.github.skm0078.audora.pdf.units.ReadingUnitBuilder
import org.junit.Assert.assertEquals
import org.junit.Test

class OcrFootnotesTest {
    private val builder = ReadingUnitBuilder()
    private val transform = PageTransform.upright(PAGE_WIDTH, PAGE_HEIGHT)

    /** One OCR line of [text]'s words with its top at [top] (view fraction), in Tesseract paragraph [paragraph]. */
    private fun line(
        text: String,
        top: Float,
        paragraph: Int,
        size: Float = BODY,
        confidence: Int = CONFIDENT,
    ): OcrLine {
        val words = text.split(' ').mapIndexed { i, word ->
            OcrWord(
                word,
                ViewRect(LEFT + i * WORD_PITCH, top, LEFT + i * WORD_PITCH + WORD_WIDTH, top + size),
                confidence,
            )
        }
        return OcrLine(words, paragraph, size)
    }

    private fun body(top: Float = BODY_TOP, confidence: Int = CONFIDENT) = listOf(
        "Plants make their food from light and water every day.",
        "The leaves hold the green matter that catches the light.",
        "Roots take water up from the soil into the stem below.",
        "The stem carries the water up to every leaf it holds.",
    ).mapIndexed { i, text -> line(text, top + i * BODY_PITCH, 0, confidence = confidence) }

    private val body = body()

    /**
     * [notes] (text to Tesseract paragraph) set small from [top] down, under the body text; [rule]: a short rule
     * ruled between them.
     */
    private fun page(
        vararg notes: Pair<String, Int>,
        size: Float = SMALL,
        confidence: Int = CONFIDENT,
        top: Float = NOTES_TOP,
        rule: Boolean = false,
    ): List<ReadingUnit> {
        val lines = body(top - (NOTES_TOP - BODY_TOP)) + notes.mapIndexed { i, (text, paragraph) ->
            line(text, top + i * NOTE_PITCH, paragraph, size, confidence)
        }
        val rules = if (rule) {
            listOf(
                ViewRect(LEFT, top - RULE_ABOVE, LEFT + RULE_WIDTH, top - RULE_ABOVE),
            )
        } else {
            emptyList()
        }
        return builder.build(builder.ocrReadingText(PAGE, OcrText(lines, rules = rules)) { transform.toPage(it) })
    }

    /** Three lines of verse set small and indented under the body text. */
    private fun quote(): List<ReadingUnit> {
        val verse = listOf(
            "the soil was dry that year, so the roots grew deeper",
            "and the leaves turned brown before the rains came back",
            "to the hills where the old farm had stood for years.",
        ).mapIndexed { i, text -> line(text, NOTES_TOP + i * NOTE_PITCH, 1, SMALL).shifted(QUOTE_INDENT) }
        return builder.build(builder.ocrReadingText(PAGE, OcrText(body + verse)) { transform.toPage(it) })
    }

    private fun OcrLine.shifted(by: Float) = OcrLine(
        words.map {
            OcrWord(it.text, it.box?.let { b -> ViewRect(b.left + by, b.top, b.right + by, b.bottom) }, it.confidence)
        },
        paragraph,
        size,
    )

    private fun List<ReadingUnit>.notes() = filter { it.kind == UnitKind.FOOTNOTE }.map { it.noteRef to it.text }

    @Test
    fun `numbered small lines at the foot are footnotes, their numbers not read`() {
        val units = page(
            "1 See the appendix for the full list of plants we grew." to 1,
            "2 First printed in the spring of the year nineteen ninety." to 2,
        )
        assertEquals(
            listOf(
                1 to "See the appendix for the full list of plants we grew.",
                2 to "First printed in the spring of the year nineteen ninety.",
            ),
            units.notes(),
        )
        assertEquals(List(body.size) { UnitKind.BODY }, units.take(body.size).map { it.kind })
    }

    @Test
    fun `markers OCR glued to the note or read as signs open a note`() {
        val cases = listOf(
            "3The figures come from the survey of the last forty years." to
                (3 to "The figures come from the survey of the last forty years."),
            "*Equal share of the work on this paper by all the authors." to
                (1 to "Equal share of the work on this paper by all the authors."),
            "*“Read the survey report on the board's website for details.”" to
                (1 to "“Read the survey report on the board's website for details.”"),
            "†Work done while at the university farm in the hill country." to
                (2 to "Work done while at the university farm in the hill country."),
        )
        // A sign is sure on its own; one line opening with a number needs a rule above it as well.
        for ((note, want) in cases) {
            assertEquals(note, listOf(want), page(note to 1, rule = note.first().isDigit()).notes())
        }
    }

    @Test
    fun `a wide note line without a number under a rule carries on a note from the page before`() {
        val units = page("the soil was dry that year, so the roots grew deeper." to 1, rule = true)
        assertEquals(listOf(null to "the soil was dry that year, so the roots grew deeper."), units.notes())
    }

    @Test
    fun `a line that only starts with a number carries on the note above, the next number opens a new one`() {
        val continued = page(
            "1 See the appendix for the full list of plants we grew." to 1,
            "12 pages of tables follow in the appendix to this volume." to 1,
        )
        assertEquals(listOf(1), continued.notes().map { it.first }.distinct().filterNotNull())
        assertEquals(true, continued.notes().any { it.second.contains("grew. 12 pages of tables") })
        val numbered = page(
            "1 See the appendix for the full list of plants we grew." to 1,
            "2 First printed in the spring of the year nineteen ninety." to 1,
        )
        assertEquals(listOf(1, 2), numbered.notes().map { it.first })
    }

    @Test
    fun `a run's first number is text unless it opens a paragraph or the body cites it`() {
        // In the body's Tesseract paragraph: the run's first line carries on a note from the page before.
        for (carried in listOf(
            "12 pages of tables follow in the appendix to this volume.",
            "११ एससीसी ३४५ में यह फ़ैसला छपा है।",
        )) {
            val lines = body + line(carried, NOTES_TOP, 0, SMALL) +
                line("the full list follows in the appendix to this volume.", NOTES_TOP + NOTE_PITCH, 0, SMALL)
            val rule = ViewRect(LEFT, NOTES_TOP - RULE_ABOVE, LEFT + RULE_WIDTH, NOTES_TOP - RULE_ABOVE)
            val units = builder.build(
                builder.ocrReadingText(PAGE, OcrText(lines, rules = listOf(rule))) {
                    transform.toPage(it)
                },
            )
            assertEquals(carried, listOf<Int?>(null), units.notes().map { it.first }.distinct())
            assertEquals(carried, true, units.notes().first().second.startsWith(carried.substringBefore(' ')))
        }
        // The same line, the body citing "12" glued to a word: note 12.
        val cites =
            body.dropLast(1) + line("The stem carries the water up to every leaf.12", BODY_TOP + 3 * BODY_PITCH, 0)
        val note = line("12 pages of tables follow in the appendix to this volume.", NOTES_TOP, 0, SMALL)
        val rule = ViewRect(LEFT, NOTES_TOP - RULE_ABOVE, LEFT + RULE_WIDTH, NOTES_TOP - RULE_ABOVE)
        val units = builder.build(
            builder.ocrReadingText(PAGE, OcrText(cites + note, rules = listOf(rule))) {
                transform.toPage(it)
            },
        )
        assertEquals(listOf(12 to "pages of tables follow in the appendix to this volume."), units.notes())
        // Digits after a decimal point or a group comma cite nothing: "3.12", "1,12" leave the note carried on.
        for (figure in listOf("The stem grew 3.12", "The stem grew 1,12")) {
            val decimal = body.dropLast(1) + line("$figure cm a year.", BODY_TOP + 3 * BODY_PITCH, 0)
            val read = builder.build(
                builder.ocrReadingText(PAGE, OcrText(decimal + note, rules = listOf(rule))) {
                    transform.toPage(it)
                },
            )
            assertEquals(figure, listOf<Int?>(null), read.notes().map { it.first }.distinct())
        }
    }

    @Test
    fun `on a two-column page a title across both columns does not make a right-column note look indented`() {
        val title = line("How the plants on the farm were grown and watered that year", TITLE_TOP, 0)
        val columns = (0 until 4).flatMap { i ->
            val top = BODY_TOP + i * BODY_PITCH
            listOf(
                line("Plants make food from light.", top, 1),
                line("Roots take water from soil.", top, 2).shifted(RIGHT),
            )
        }
        val notes = listOf(
            "the soil was dry, so the roots",
            "grew deeper and the leaves",
            "turned brown before the rains.",
        ).mapIndexed { i, text -> line(text, NOTES_TOP + i * NOTE_PITCH, 3, SMALL).shifted(RIGHT) }
        val units = builder.build(
            builder.ocrReadingText(PAGE, OcrText(listOf(title) + columns + notes)) { transform.toPage(it) },
        )
        assertEquals(listOf<Int?>(null), units.notes().map { it.first }.distinct())
    }

    @Test
    fun `a page read badly holds its notes to a floor, not to its own low confidence`() {
        val note = "1 See the appendix for the full list of plants we grew."
        val lines = body(confidence = BAD_PAGE) + line(note, NOTES_TOP, 1, SMALL, confidence = BELOW_FLOOR)
        val rule = ViewRect(LEFT, NOTES_TOP - RULE_ABOVE, LEFT + RULE_WIDTH, NOTES_TOP - RULE_ABOVE)
        val units = builder.build(
            builder.ocrReadingText(PAGE, OcrText(lines, rules = listOf(rule))) {
                transform.toPage(it)
            },
        )
        assertEquals(emptyList<Pair<Int?, String>>(), units.notes())
    }

    @Test
    fun `small print read less surely than English is held to the page's own confidence`() {
        val notes = listOf(
            "१ पौधों की पूरी सूची परिशिष्ट में दी गई है और वह लंबी है।",
            "२ यह पहली बार उन्नीस सौ नब्बे के वसंत में छपा था।",
        ).mapIndexed { i, text -> line(text, NOTES_TOP + i * NOTE_PITCH, i + 1, SMALL, HINDI_NOTES) }
        val lines = body(confidence = HINDI_BODY) + notes
        val units = builder.build(builder.ocrReadingText(PAGE, OcrText(lines)) { transform.toPage(it) })
        assertEquals(listOf(1, 2), units.notes().map { it.first })
    }

    @Test
    fun `a new Tesseract paragraph starts a new note even when its number was misread`() {
        val units = page(
            "1 See the appendix for the full list of plants we grew." to 1,
            "“To check this, grow two plants side by side for a month." to 2,
        )
        assertEquals(listOf(1, null), units.notes().map { it.first })
    }

    @Test
    fun `small lines that are not notes stay as they were`() {
        val note = "1 See the appendix for the full list of plants we grew."
        val cases = listOf(
            "body-sized" to page(note to 1, size = BODY),
            "one unmarked line barely smaller" to
                page("the soil was dry that year, so the roots grew deeper." to 1, size = BARELY_SMALL),
            "read badly" to page(note to 1, confidence = UNSURE),
            "ending a short page mid-sheet" to page(note to 1, top = MID_PAGE),
            "a caption under a figure" to page("Figure 2: Leaves of the plants we grew in the long summer." to 1),
            "a list in small type" to page("• Water the plants every morning before the sun is high." to 1),
            "a short label" to page("Plate one" to 1),
            "one unmarked line with nothing more to show it is a note" to
                page("the soil was dry that year, so the roots grew deeper." to 1),
            "one line starting with a number, no rule above" to
                page("15 August was the day the plants first came up in the field." to 1),
            "an indented quote set small" to quote(),
            "a source line under a rule" to
                page("Source: survey of the plants grown on the farm in the year two thousand." to 1, rule = true),
            "a Hindi source line spelt with त्र" to
                page("स्त्रोत: वर्ष दो हज़ार में खेत पर उगाए गए पौधों का सर्वेक्षण और उसकी रिपोर्ट।" to 1, rule = true),
        )
        for ((name, units) in cases) assertEquals(name, emptyList<Pair<Int?, String>>(), units.notes())
    }

    @Test
    fun `a body line below the small lines keeps them in the text`() {
        val lines = body + line("1 See the appendix for the full list of plants we grew.", NOTES_TOP, 1, SMALL) +
            line("The plants were watered every morning before the sun was high.", NOTES_TOP + BODY_PITCH, 2)
        val units = builder.build(builder.ocrReadingText(PAGE, OcrText(lines)) { transform.toPage(it) })
        assertEquals(emptyList<Pair<Int?, String>>(), units.notes())
    }

    @Test
    fun `a short body line OCR measured small, set apart above the notes, stays body text`() {
        val lines = body + line("Consider these:", LEAD_TOP, 1, SMALL) +
            line("1 See the appendix for the full list of plants we grew.", NOTES_TOP, 2, SMALL)
        val rule = ViewRect(LEFT, NOTES_TOP - RULE_ABOVE, LEFT + RULE_WIDTH, NOTES_TOP - RULE_ABOVE)
        val units = builder.build(
            builder.ocrReadingText(PAGE, OcrText(lines, rules = listOf(rule))) {
                transform.toPage(it)
            },
        )
        assertEquals(UnitKind.BODY to "Consider these:", units[body.size].kind to units[body.size].text)
        assertEquals(listOf(1 to "See the appendix for the full list of plants we grew."), units.notes())
    }

    @Test
    fun `text without boxes has no footnotes`() {
        val units = builder.build(builder.ocrReadingText(PAGE, "Plants make food.\n\n1 See the appendix."))
        assertEquals(emptyList<Pair<Int?, String>>(), units.notes())
    }

    private companion object {
        const val PAGE = 2
        const val PAGE_WIDTH = 600f
        const val PAGE_HEIGHT = 800f
        const val BODY = 0.015f
        const val SMALL = 0.012f
        const val BARELY_SMALL = 0.0137f
        const val CONFIDENT = 95
        const val UNSURE = 50
        const val BODY_TOP = 0.7f
        const val BODY_PITCH = 0.02f
        const val LEAD_TOP = 0.8f
        const val NOTES_TOP = 0.87f
        const val NOTE_PITCH = 0.016f
        const val MID_PAGE = 0.3f
        const val RULE_ABOVE = 0.03f
        const val RULE_WIDTH = 0.3f
        const val QUOTE_INDENT = 0.1f
        const val HINDI_BODY = 75
        const val HINDI_NOTES = 60
        const val BAD_PAGE = 40
        const val TITLE_TOP = 0.6f
        const val RIGHT = 0.5f
        const val BELOW_FLOOR = 30
        const val LEFT = 0.05f
        const val WORD_PITCH = 0.08f
        const val WORD_WIDTH = 0.07f
    }
}
