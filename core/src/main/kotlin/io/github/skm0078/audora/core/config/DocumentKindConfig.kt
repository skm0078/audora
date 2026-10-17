// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/DocumentKindConfig.kt
// Purpose: Thresholds for telling a novel, paper, textbook, gazette or form from a sample of its pages (RI-10).

package io.github.skm0078.audora.core.config

/**
 * `DocumentKindClassifier`'s decision list. Rates are per 1,000 words of the sampled pages.
 *
 * @property samplePages how many pages, spread over the book, are read to classify it.
 * @property minWords below this many words in the sample the book is not classified (a scan read without OCR).
 * @property formTableShare a FORM has at least this share of its units as table rows,
 * @property formDigitShare or at least this share of its letters and digits as digits (lists of figures).
 * @property maxPaperPages a PAPER has at most this many pages (a longer book that cites is a textbook or report),
 * @property paperCitationRate and cites at least this often ("[4]", "(Kot and Schaffer, 1986)", "Murray (1989)"),
 * @property paperFurnitureRate or has at least this many captions and equations.
 * @property gazetteTermRate a GAZETTE uses official vocabulary ("Regulation", "अधिसूचना", "সরকার") at least this often.
 * @property minTextbookPages a TEXTBOOK has at least this many pages,
 * @property textbookStructureRate and at least this many headings and list items.
 * @property minNovelPages a NOVEL has at least this many pages,
 * @property novelSentenceWords sentences of at least this many words on average,
 * @property novelMaxStructureRate and at most this many headings, list items and table rows.
 */
data class DocumentKindConfig(
    val samplePages: Int = 12,
    val minWords: Int = 150,
    val formTableShare: Float = 0.35f,
    val formDigitShare: Float = 0.2f,
    val maxPaperPages: Int = 50,
    val paperCitationRate: Float = 2f,
    val paperFurnitureRate: Float = 3.5f,
    val gazetteTermRate: Float = 6f,
    val minTextbookPages: Int = 12,
    val textbookStructureRate: Float = 6f,
    val minNovelPages: Int = 20,
    val novelSentenceWords: Float = 14f,
    val novelMaxStructureRate: Float = 3f,
) {
    init {
        require(samplePages >= 1 && minWords >= 0) { "samplePages must be >= 1 and minWords >= 0" }
        val shares = listOf(formTableShare, formDigitShare)
        require(shares.all { it in 0f..1f }) { "document kind shares must be in 0..1, were $shares" }
        val rates = listOf(paperCitationRate, paperFurnitureRate, gazetteTermRate, textbookStructureRate)
        require(rates.all { it >= 0f } && novelMaxStructureRate >= 0f) { "document kind rates must be >= 0" }
        require(minTextbookPages >= 1 && minNovelPages >= 1 && maxPaperPages >= 1) { "page limits must be >= 1" }
        require(novelSentenceWords > 0f) { "novelSentenceWords must be > 0, was $novelSentenceWords" }
    }
}
