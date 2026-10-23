// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/document/IntentClassifier.kt
// Purpose: Tells a book's intent (story, storybook, poem, scripture, news, report, study) from its signals (ADR-056).

package io.github.skm0078.audora.pdf.document

import io.github.skm0078.audora.core.config.DeliveryConfig
import io.github.skm0078.audora.core.model.ContentIntent

/**
 * A decision list, first match wins: too little text (null: not told yet), SCRIPTURE, POETRY, NEWS, REPORT, STUDY,
 * STORY or CHILDREN, else GENERAL. Sacred and verse texts come first because a psalm also has short lines and a
 * Gita page also has "उवाच" speech; news before reports because news about a ministry uses its words, and before
 * stories because a report quotes people and says "said". A poem has no dialogue to speak of: a novel's short
 * dialogue lines are ragged too. Rates are per 1,000 words; thresholds in [DeliveryConfig].
 */
class IntentClassifier(private val config: DeliveryConfig = DeliveryConfig()) {
    fun classify(signals: IntentSignals): ContentIntent? = with(signals) {
        when {
            words < config.minWords -> null
            rate(sacredWords) >= config.scriptureRate || rate(verseMarks) >= config.verseRate -> ContentIntent.SCRIPTURE
            isPoem() -> ContentIntent.POETRY
            rate(newsCues) >= config.newsRate -> ContentIntent.NEWS
            isReport() -> ContentIntent.REPORT
            isStudy() -> ContentIntent.STUDY
            isStory() -> if (isStorybook()) ContentIntent.CHILDREN else ContentIntent.STORY
            else -> ContentIntent.GENERAL
        }
    }

    private fun IntentSignals.isPoem(): Boolean = lines >= config.minPoemLines &&
        lineWords <= lines * config.poemLineWords &&
        shortLines >= lines * config.poemShortLineShare &&
        dialogueSentences < maxOf(1, bodySentences) * config.storyDialogueShare

    private fun IntentSignals.isReport(): Boolean = rate(officialTerms) >= config.reportRate ||
        digits >= maxOf(1, digits + letters) * config.reportDigitShare

    private fun IntentSignals.isStudy(): Boolean = rate(citations) >= config.studyCitationRate ||
        rate(furniture) >= config.studyFurnitureRate ||
        (rate(structure) >= config.studyStructureRate && rate(teachCues) >= config.studyCueRate)

    private fun IntentSignals.isStory(): Boolean = rate(storyOpeners) >= config.storyOpenerRate ||
        rate(speechVerbs) >= config.storyVerbRate ||
        dialogueSentences >= maxOf(1, bodySentences) * config.storyDialogueShare

    private fun IntentSignals.isStorybook(): Boolean = rate(storyOpeners) >= config.storyOpenerRate ||
        (bodySentences > 0 && bodyWords <= bodySentences * config.childSentenceWords)

    private fun IntentSignals.rate(count: Int): Float = count * WORDS_PER_RATE / maxOf(1, words)

    private companion object {
        const val WORDS_PER_RATE = 1_000f
    }
}
