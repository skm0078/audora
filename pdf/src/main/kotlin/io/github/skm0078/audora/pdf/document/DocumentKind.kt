// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/document/DocumentKind.kt
// Purpose: Classifies a book as a novel, paper, textbook, gazette or form from a sample of its pages (RI-10).

package io.github.skm0078.audora.pdf.document

import io.github.skm0078.audora.core.config.DocumentKindConfig
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.UnitKind

/**
 * What kind of book a PDF is, so playback can pick its reading defaults (tables, footnotes, citations, skimming);
 * the listener's own choice always wins (RI-10, ADR-035).
 */
enum class DocumentKind {
    /** Continuous prose: novels, stories, scripture. Few headings, long paragraphs, dialogue. */
    NOVEL,

    /** A scholarly article: citations, an abstract and references, often two columns, figures and equations. */
    PAPER,

    /** A book of numbered chapters and sections with lists, examples and notes: textbooks and manuals. */
    TEXTBOOK,

    /** An official text: acts, regulations, notifications, government reports, letters and notices. */
    GAZETTE,

    /** Mostly tables and fields: lists of names and numbers, results, salary lists, application forms. */
    FORM,

    /** None of the above stands out (a leaflet, a magazine), or too little text was read to tell. */
    GENERAL,
}

/**
 * The signals one sampled page gives, from its units (any source: text layer or OCR) and, on a text layer, its
 * extracted text (citation markers are counted there: cleanup drops them from the units).
 */
data class PageSignals(
    val words: Int,
    val bodySentences: Int,
    val bodyWords: Int,
    val headings: Int,
    val numberedHeadings: Int,
    val listItems: Int,
    val tableRows: Int,
    val footnotes: Int,
    val captions: Int,
    val equations: Int,
    val citations: Int,
    val officialTerms: Int,
    val quotes: Int,
    val digits: Int,
    val letters: Int,
) {
    companion object {
        val EMPTY = PageSignals(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)

        /**
         * [rawText]: the page's extracted text on a text layer, for citation markers; null on an OCR'd page. Units
         * scoring below [readableFrom] (`SpeakabilityConfig.skipBelow`) are junk and say nothing about the book.
         */
        fun of(allUnits: List<ReadingUnit>, rawText: String?, readableFrom: Float): PageSignals {
            val units = allUnits.filter { (it.speakScore ?: 1f) >= readableFrom }
            val text = units.joinToString(" ") { it.text }
            val body = units.filter { it.kind == UnitKind.BODY }
            val headings = units.filter { it.kind == UnitKind.HEADING }
            return PageSignals(
                words = WORD.findAll(text).count(),
                bodySentences = body.size,
                bodyWords = body.sumOf { WORD.findAll(it.text).count() },
                headings = headings.size,
                numberedHeadings = headings.count { NUMBERED_HEADING.containsMatchIn(it.text) },
                listItems = units.count(::isListItem),
                tableRows = units.count { it.kind == UnitKind.TABLE_ROW },
                footnotes = units.count { it.kind == UnitKind.FOOTNOTE },
                captions = units.count { it.kind == UnitKind.CAPTION },
                equations = units.count { it.kind == UnitKind.EQUATION },
                citations = CITATION.findAll(rawText ?: text).count(),
                officialTerms = OFFICIAL.findAll(text).count(),
                quotes = text.count { it in QUOTES },
                digits = text.count { Character.isDigit(it) },
                letters = text.count { Character.isLetter(it) },
            )
        }

        /** OCR'd pages come out as body text only, so a list item is also told by its label ("(২)", "3.", "•"). */
        private fun isListItem(unit: ReadingUnit): Boolean = when (unit.kind) {
            UnitKind.LIST_ITEM -> true
            UnitKind.BODY -> ENUMERATOR.containsMatchIn(unit.text)
            else -> false
        }

        private val ENUMERATOR = Regex("^\\s*(?:\\(?(?:\\p{Nd}{1,2}|\\p{L})\\)|\\p{Nd}{1,2}[.।]\\s|[•●▪■◆➢►]\\s?)")

        private val WORD = Regex("[\\p{L}\\p{M}\\p{Nd}]+")
        private const val QUOTES = "“”\""

        /** "2.4 Sound arguments", "Chapter 3", "अध्याय 2", "অধ্যায় ৪". */
        private val NUMBERED_HEADING = Regex(
            "^\\s*(?:\\p{Nd}+(?:\\.\\p{Nd}+)+|(?:Chapter|CHAPTER|Unit|UNIT|Lesson|अध्याय|इकाई|পাঠ|অধ্যায়)\\s+\\S+)",
        )

        /** "[4]", "[12, 13]", "[4–7]", "(Kot and Schaffer, 1986)", "(Smith et al. 2004)", "Murray (1989)". */
        private val CITATION = Regex(
            "\\[\\p{Nd}{1,3}(?:\\s?[,–-]\\s?\\p{Nd}{1,3})*]|" +
                "\\(\\p{Lu}\\p{L}+(?: et al\\.?| and \\p{Lu}\\p{L}+)?,? \\p{Nd}{4}[a-z]?\\)|" +
                "\\p{Lu}\\p{L}+(?: et al\\.?)? \\((?:1[89]|20)\\p{Nd}{2}[a-z]?\\)",
        )

        /** The vocabulary of official texts in English, Hindi and Bengali. */
        private val OFFICIAL = Regex(
            "\\b(?:Section|Sub-section|Clause|Regulations?|Notification|Ministry|Government|Gazette|Commission|" +
                "Act|Article|hereby|aforesaid)\\b|" +
                "अधिसूचना|धारा|विनियम|अधिनियम|सरकार|मंत्रालय|आयोग|राजपत्र|अनुच्छेद|शासन|विभाग|आदेश|" +
                "योजना|जिला|जनपद|कार्यालय|अधिकारी|निदेशालय|प्रतिवेदन|बजट|मुख्यमंत्री|दिनांक|" +
                "সরকার|মন্ত্রণালয়|প্রজ্ঞাপন|ধারা|আইন|বিধি|অনুচ্ছেদ|বিভাগ|আদেশ|অধিদপ্তর|" +
                "কার্যালয়|জেলা|কর্মকর্তা|বাজেট|তারিখ|পরিপত্র",
        )
    }
}

/**
 * A decision list over the sampled pages' signals, first match wins, in this order: too little text (GENERAL),
 * FORM, PAPER, GAZETTE, TEXTBOOK, NOVEL, else GENERAL. Rates are per 1,000 words of the sample. Thresholds are in
 * [DocumentKindConfig].
 */
class DocumentKindClassifier(private val config: DocumentKindConfig = DocumentKindConfig()) {
    fun classify(pages: List<PageSignals>, pageCount: Int): DocumentKind {
        val sample = Sample(pages.fold(PageSignals.EMPTY, ::plus), pageCount)
        return when {
            sample.signals.words < config.minWords || sample.units == 0 -> DocumentKind.GENERAL
            isForm(sample) -> DocumentKind.FORM
            isPaper(sample) -> DocumentKind.PAPER
            sample.rate { officialTerms } >= config.gazetteTermRate -> DocumentKind.GAZETTE
            isTextbook(sample) -> DocumentKind.TEXTBOOK
            isNovel(sample) -> DocumentKind.NOVEL
            else -> DocumentKind.GENERAL
        }
    }

    private fun isForm(sample: Sample): Boolean = with(sample.signals) {
        tableRows >= sample.units * config.formTableShare ||
            digits >= maxOf(1, digits + letters) * config.formDigitShare
    }

    private fun isPaper(sample: Sample): Boolean = sample.pageCount <= config.maxPaperPages &&
        (
            sample.rate { citations } >= config.paperCitationRate ||
                sample.rate { captions + equations } >= config.paperFurnitureRate
            )

    private fun isTextbook(sample: Sample): Boolean = sample.pageCount >= config.minTextbookPages &&
        sample.rate { headings + listItems } >= config.textbookStructureRate

    private fun isNovel(sample: Sample): Boolean = with(sample.signals) {
        val wordsPerSentence = if (bodySentences == 0) 0f else bodyWords / bodySentences.toFloat()
        sample.pageCount >= config.minNovelPages && wordsPerSentence >= config.novelSentenceWords &&
            sample.rate { headings + listItems + tableRows } <= config.novelMaxStructureRate
    }

    /** The sampled pages' signals summed, and the book's length. */
    private class Sample(val signals: PageSignals, val pageCount: Int) {
        val units =
            with(signals) { bodySentences + headings + listItems + tableRows + footnotes + captions + equations }

        /** A count per 1,000 words of the sample. */
        fun rate(count: PageSignals.() -> Int): Float = signals.count() * WORDS_PER_RATE / maxOf(1, signals.words)
    }

    private fun plus(a: PageSignals, b: PageSignals) = PageSignals(
        a.words + b.words, a.bodySentences + b.bodySentences, a.bodyWords + b.bodyWords, a.headings + b.headings,
        a.numberedHeadings + b.numberedHeadings, a.listItems + b.listItems, a.tableRows + b.tableRows,
        a.footnotes + b.footnotes, a.captions + b.captions, a.equations + b.equations, a.citations + b.citations,
        a.officialTerms + b.officialTerms, a.quotes + b.quotes, a.digits + b.digits, a.letters + b.letters,
    )

    private companion object {
        const val WORDS_PER_RATE = 1_000f
    }
}
