// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/NumberingWords.kt
// Purpose: Words a letter or Roman numeral after them labels ("Appendix A.", "Article V."), for :pdf and :speech.

package io.github.skm0078.audora.core.config

/**
 * Capitalised English words that a letter or Roman numeral after them labels: "Appendix A.", "Article V.",
 * "Team B.". That letter is not an initial, so the full stop after it may end a sentence. The sentence splitter
 * (:pdf, [SentenceConfig.letterLabels]) and the speech normalizer's initials rule (:speech) read this one list, so
 * a full stop the splitter ends a unit on is one the voice reads as a sentence end too, and the other way round.
 */
object NumberingWords {
    val ALL: Set<String> = setOf(
        "Act", "Annex", "Annexure", "Appendix", "Article", "Block", "Book", "Case", "Category", "Chapter", "Class",
        "Corollary", "Definition", "Example", "Exercise", "Exhibit", "Figure", "Form", "Grade", "Group", "Item",
        "Lemma", "Lesson", "Level", "Model", "Option", "Paper", "Part", "Phase", "Plan", "Point", "Problem",
        "Proposition", "Question", "Room", "Scene", "Schedule", "Section", "Sector", "Stage", "Standard", "Step",
        "Table", "Team", "Theorem", "Type", "Unit", "Vitamin", "Volume", "War", "Zone",
    )
}
