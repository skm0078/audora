// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/document/DocumentKindClassifierTest.kt
// Purpose: Page signals are counted from units and raw text, and the decision list picks each kind (RI-10).

package io.github.skm0078.audora.pdf.document

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.UnitKind
import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentKindClassifierTest {
    private val classifier = DocumentKindClassifier()

    private fun unit(text: String, kind: UnitKind = UnitKind.BODY, score: Float? = null) =
        ReadingUnit(0, 0, 0, text.length, text, Lang.EN, 0, null, kind, score)

    /** One page of 1,000 words in 50 body sentences (20 words each) and no structure; tests change what they need. */
    private val page = PageSignals.EMPTY.copy(words = 1_000, bodySentences = 50, bodyWords = 1_000, letters = 5_000)

    @Test
    fun `the decision list picks each kind`() {
        data class Case(val name: String, val page: PageSignals, val pageCount: Int, val kind: DocumentKind)
        listOf(
            Case(
                "too little text",
                page.copy(words = 100, bodySentences = 5, bodyWords = 100),
                3,
                DocumentKind.GENERAL,
            ),
            Case("table rows", page.copy(bodySentences = 10, tableRows = 20), 40, DocumentKind.FORM),
            Case("a list of figures", page.copy(digits = 2_000), 40, DocumentKind.FORM),
            Case("citations", page.copy(citations = 5), 12, DocumentKind.PAPER),
            Case("figures and equations", page.copy(captions = 2, equations = 2), 12, DocumentKind.PAPER),
            Case("citations in a long report", page.copy(citations = 5, headings = 10), 300, DocumentKind.TEXTBOOK),
            Case("official vocabulary", page.copy(officialTerms = 10, listItems = 20), 80, DocumentKind.GAZETTE),
            Case("headings and lists", page.copy(headings = 4, listItems = 4), 80, DocumentKind.TEXTBOOK),
            Case("a short leaflet with lists", page.copy(headings = 4, listItems = 10), 4, DocumentKind.GENERAL),
            Case("long sentences, no structure", page, 300, DocumentKind.NOVEL),
            Case("short sentences, no structure", page.copy(bodySentences = 100), 300, DocumentKind.GENERAL),
        ).forEach { case ->
            assertEquals(case.name, case.kind, classifier.classify(listOf(case.page), case.pageCount))
        }
    }

    @Test
    fun `rates are taken over all the sampled pages`() {
        val pages = listOf(page.copy(citations = 3), page, page)
        assertEquals(DocumentKind.NOVEL, classifier.classify(pages, 30))
        assertEquals(DocumentKind.PAPER, classifier.classify(pages + page.copy(citations = 6), 30))
    }

    @Test
    fun `page signals count kinds, labels, citations and official words, never junk`() {
        val units = listOf(
            unit("2.4 Sound arguments", UnitKind.HEADING),
            unit("The Commission hereby notifies the Regulations as in Section 4."),
            unit("(২) আবেদনকারীর জন্য তথ্য।"),
            unit("• Bring your card."),
            unit("A fourth item.", UnitKind.LIST_ITEM),
            unit("Ŧ§¤ ¶¶ ‡‡ ŧŧ", score = 0.1f),
        )
        val raw = "as shown [4] and [12, 13], or by Murray (1989) and (Kot and Schaffer, 1986)."
        val signals = PageSignals.of(units, raw, readableFrom = 0.55f)
        assertEquals(1, signals.headings)
        assertEquals(1, signals.numberedHeadings)
        assertEquals(3, signals.listItems)
        assertEquals(3, signals.bodySentences)
        assertEquals(4, signals.citations)
        assertEquals(4, signals.officialTerms)
        assertEquals(units.dropLast(1).sumOf { it.text.count(Char::isDigit) }, signals.digits)
    }

    @Test
    fun `an OCR'd page counts citations in its units`() {
        val signals = PageSignals.of(listOf(unit("as shown [4].")), rawText = null, readableFrom = 0.55f)
        assertEquals(1, signals.citations)
    }

    @Test
    fun `sample pages are spread over the book, first and last included`() {
        listOf(
            Triple(0, 12, emptyList()),
            Triple(5, 12, listOf(0, 1, 2, 3, 4)),
            Triple(100, 1, listOf(0)),
            Triple(100, 5, listOf(0, 25, 50, 74, 99)),
            Triple(13, 12, listOf(0, 1, 2, 3, 4, 5, 7, 8, 9, 10, 11, 12)),
        ).forEach { (pageCount, count, expected) ->
            assertEquals("$pageCount pages, $count samples", expected, samplePages(pageCount, count))
        }
    }
}
