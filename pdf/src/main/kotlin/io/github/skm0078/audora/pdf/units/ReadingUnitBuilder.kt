// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/units/ReadingUnitBuilder.kt
// Purpose: Cuts a page's reading-order text into speakable ReadingUnits with stable charStart/charEnd.

package io.github.skm0078.audora.pdf.units

import io.github.skm0078.audora.core.config.CleanupConfig
import io.github.skm0078.audora.core.config.LayoutConfig
import io.github.skm0078.audora.core.config.QualityConfig
import io.github.skm0078.audora.core.config.ReadingUnitConfig
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.TextBox
import io.github.skm0078.audora.core.model.UnitKind
import io.github.skm0078.audora.pdf.cleanup.ClutterRemover
import io.github.skm0078.audora.pdf.cleanup.PageProfile
import io.github.skm0078.audora.pdf.cleanup.PageProfiler
import io.github.skm0078.audora.pdf.cleanup.TextRepair
import io.github.skm0078.audora.pdf.extract.PageText
import io.github.skm0078.audora.pdf.layout.ListMarkers
import io.github.skm0078.audora.pdf.layout.PageLayout
import io.github.skm0078.audora.pdf.layout.PageLayoutAnalyzer
import io.github.skm0078.audora.pdf.layout.ParagraphKind
import io.github.skm0078.audora.pdf.layout.ReadingParagraph
import io.github.skm0078.audora.pdf.layout.ReadingText
import io.github.skm0078.audora.pdf.layout.ReadingTextBuilder
import io.github.skm0078.audora.pdf.ocr.OcrClutter
import io.github.skm0078.audora.pdf.ocr.OcrPageText
import io.github.skm0078.audora.pdf.ocr.OcrText
import io.github.skm0078.audora.pdf.quality.IndicOrder
import io.github.skm0078.audora.pdf.quality.PageQuality
import io.github.skm0078.audora.pdf.quality.PageQualityScorer
import io.github.skm0078.audora.pdf.quality.SpeakabilityScorer
import io.github.skm0078.audora.pdf.quality.indicOrderOf
import io.github.skm0078.audora.pdf.render.ViewRect

/**
 * The quality scorer (P3-03) routes the page and measures whether its Indic text is in visual order; layout (P2-02)
 * puts the page in reading order; cleanup (P2-03) drops running headers, page numbers and table interiors and
 * repairs the text, after legacy-font conversion (P3-04) and the Indic repair and reorder pass (P3-03); sentences
 * are then split within each paragraph, so a heading without a full stop never runs into the paragraph below it.
 * `charStart`/`charEnd` index [ReadingText.text] (ADR-018, ADR-020). Number and date normalization is P2-04. Units
 * without a letter or digit are skipped. Every unit gets a speakability score (RI-2a, ADR-035), with the OCR
 * engine's confidence in its words on a scanned page.
 *
 * Header and footer removal compares a page with its neighbours ([PageProfile]). `PdfBook.readingUnits` supplies
 * them; [build] with a page alone applies every rule that needs no neighbour.
 */
@Suppress("TooManyFunctions") // The pipeline's one entry point: one function per way a page is read or sampled.
class ReadingUnitBuilder(
    private val config: ReadingUnitConfig = ReadingUnitConfig(),
    private val splitter: SentenceSplitter = BreakIteratorSentenceSplitter(),
    layoutConfig: LayoutConfig = LayoutConfig(),
    val cleanupConfig: CleanupConfig = CleanupConfig(),
    val qualityConfig: QualityConfig = QualityConfig(),
    private val speakability: SpeakabilityScorer = SpeakabilityScorer(),
) {
    private val lineBoxes = LineBoxes(config.lineOverlapRatio)
    private val analyzer = PageLayoutAnalyzer(layoutConfig)
    private val profiler = PageProfiler(cleanupConfig)
    private val clutter = ClutterRemover(cleanupConfig, layoutConfig)

    /** Running headers and page numbers on scanned pages (ADR-032); `PdfBook` feeds it the neighbours' strips. */
    internal val ocrClutter = OcrClutter(cleanupConfig)
    private val textBuilder = ReadingTextBuilder(TextRepair(cleanupConfig))
    private val scorer = PageQualityScorer(qualityConfig)

    /** Quality, layout and neighbour summary of one page: what a book caches between calls. */
    fun analyze(page: PageText): AnalyzedPage {
        val quality = scorer.score(page)
        val layout = analyzer.analyze(page)
        return AnalyzedPage(page, layout, profiler.profile(page, layout, quality.indicOrder), quality)
    }

    fun build(page: PageText): List<ReadingUnit> = build(readingText(page))

    /** [neighbours]: profiles of the pages around [page] (not [page] itself), in any order. */
    fun build(page: AnalyzedPage, neighbours: List<PageProfile>): List<ReadingUnit> =
        build(readingText(page, neighbours))

    /** The page's text in reading order: what [build]'s offsets index. */
    fun readingText(page: PageText): ReadingText = readingText(analyze(page), emptyList())

    fun readingText(page: AnalyzedPage, neighbours: List<PageProfile>): ReadingText =
        textOf(page, neighbours, clutter.clean(page.text, page.layout, page.profile, neighbours))

    /**
     * The page's whole text layer in layout order, repaired, with headers and page numbers kept: what OCR of the
     * same page is compared with in the book's cross-check (P3-03).
     */
    fun sampleText(page: AnalyzedPage, neighbours: List<PageProfile>): String =
        textOf(page, neighbours, page.layout).text

    /**
     * The reading text of a page read by OCR (P3-05): the OCR'd words with the same repair as a text layer
     * (Tesseract writes Indic text in logical order), [dropped] lines (running headers, ADR-032) left out. With
     * [toPage] the words keep their boxes, so [build] gives units a highlight; without it bounds stay null.
     */
    fun ocrReadingText(
        pageIndex: Int,
        ocr: OcrText,
        dropped: Set<Int> = emptySet(),
        toPage: ((ViewRect) -> TextBox)? = null,
    ): ReadingText {
        val page = OcrPageText.build(pageIndex, ocr, dropped, toPage)
        return textBuilder.build(page.text, page.layout, page.hyphenated, IndicOrder.LOGICAL)
    }

    /** Text without positions (tests, and engines without boxes): blank lines start paragraphs. */
    fun ocrReadingText(pageIndex: Int, ocrText: String): ReadingText = ocrReadingText(pageIndex, OcrText.plain(ocrText))

    private fun textOf(page: AnalyzedPage, neighbours: List<PageProfile>, layout: PageLayout): ReadingText {
        val lexicon = neighbours.fold(page.profile.hyphenated) { words, other -> words + other.hyphenated }
        val order = indicOrderOf(page.profile.indicOrder, neighbours.map { it.indicOrder }, qualityConfig)
        return textBuilder.build(page.text, layout, lexicon, order, page.quality.legacyRuns)
    }

    fun build(reading: ReadingText): List<ReadingUnit> {
        val text = reading.text
        if (text.isBlank()) return emptyList()
        val pageLang = ScriptDetector.dominantLang(text) ?: Lang.EN
        // Same length as text, so the splitter's offsets are offsets into text.
        val flat = String(CharArray(text.length) { i -> if (text[i] in LINE_BREAKS) ' ' else text[i] })
        val units = ArrayList<ReadingUnit>()
        for (paragraph in reading.paragraphs) {
            val sentences = sentencesOf(flat, paragraph, pageLang)
            val pieces = sentences.flatMap { capLength(text, trim(text, it.first, it.last + 1), config.maxUnitChars) }
            pieces.forEachIndexed { i, piece ->
                val raw = text.substring(piece.first, piece.last + 1)
                if (raw.none { it.isLetterOrDigit() }) return@forEachIndexed
                val unitText = raw.split(WHITESPACE).filter { it.isNotEmpty() }.joinToString(" ")
                val ocrConfidence = reading.ocrConfidence(piece.first, piece.last + 1)
                units.add(
                    ReadingUnit(
                        page = reading.pageIndex,
                        index = units.size,
                        charStart = piece.first,
                        charEnd = piece.last + 1,
                        text = unitText,
                        lang = ScriptDetector.dominantLang(raw) ?: pageLang,
                        pauseAfterMs = pauseAfter(paragraph.kind, last = i == pieces.lastIndex),
                        bounds = lineBoxes.of(reading, piece.first, piece.last + 1),
                        kind = paragraph.kind.unitKind(),
                        speakScore = speakability.score(unitText, ocrConfidence),
                    ),
                )
            }
        }
        return units
    }

    /** Sentence ranges of [paragraph], as offsets into the whole text. */
    private fun sentencesOf(flat: String, paragraph: ReadingParagraph, pageLang: Lang): List<IntRange> {
        val slice = flat.substring(paragraph.start, paragraph.end)
        val lang = ScriptDetector.dominantLang(slice) ?: pageLang
        val sentences = splitter.split(slice, lang).map { (it.first + paragraph.start)..(it.last + paragraph.start) }
        return if (paragraph.kind == ParagraphKind.LIST_ITEM) keepLabel(flat, sentences) else sentences
    }

    /** An item's label is read with its first sentence ("1. Open the box"), never as a sentence of its own. */
    private fun keepLabel(flat: String, sentences: List<IntRange>): List<IntRange> {
        val first = sentences.firstOrNull()
        val alone = first != null && sentences.size >= 2 &&
            ListMarkers.isLabel(flat.substring(first.first, first.last + 1))
        return if (alone) listOf(first.first..sentences[1].last) + sentences.drop(2) else sentences
    }

    /** Each sentence of a heading pauses like a heading; of a paragraph or list item, only the last one does. */
    private fun pauseAfter(kind: ParagraphKind, last: Boolean): Int = when {
        kind == ParagraphKind.HEADING -> maxOf(config.sentencePauseMs, config.headingPauseMs)
        !last -> config.sentencePauseMs
        kind == ParagraphKind.LIST_ITEM -> maxOf(config.sentencePauseMs, config.listItemPauseMs)
        else -> maxOf(config.sentencePauseMs, config.paragraphPauseMs)
    }

    private fun ParagraphKind.unitKind(): UnitKind = when (this) {
        ParagraphKind.BODY -> UnitKind.BODY
        ParagraphKind.HEADING -> UnitKind.HEADING
        ParagraphKind.LIST_ITEM -> UnitKind.LIST_ITEM
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
        const val LINE_BREAKS = "\n\r\t"
    }
}

/** [start] until [end] without leading and trailing whitespace; empty when it is all whitespace. */
private fun trim(text: String, start: Int, end: Int): IntRange {
    var s = start
    var e = end
    while (s < e && text[s].isWhitespace()) s++
    while (e > s && text[e - 1].isWhitespace()) e--
    return s until e
}

/** Splits [range] into pieces of at most maxUnitChars, at the last whitespace before the limit. */
private fun capLength(text: String, range: IntRange, maxUnitChars: Int): List<IntRange> {
    if (range.isEmpty()) return emptyList()
    val pieces = ArrayList<IntRange>()
    var start = range.first
    val end = range.last + 1
    while (end - start > maxUnitChars) {
        val limit = start + maxUnitChars
        var cut = limit
        while (cut > start && !text[cut].isWhitespace()) cut--
        if (cut == start) cut = limit
        if (Character.isLowSurrogate(text[cut]) && cut > start) cut--
        pieces.add(trim(text, start, cut))
        start = trim(text, cut, end).first
    }
    pieces.add(start until end)
    return pieces.filterNot { it.isEmpty() }
}

/** A page after scoring and layout, with the summary its neighbours use. Built by [ReadingUnitBuilder.analyze]. */
class AnalyzedPage internal constructor(
    val text: PageText,
    val layout: PageLayout,
    val profile: PageProfile,
    val quality: PageQuality,
) {
    val pageIndex: Int get() = text.pageIndex
}
