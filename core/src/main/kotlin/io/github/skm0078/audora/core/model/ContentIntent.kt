// File: core/src/main/kotlin/io/github/skm0078/audora/core/model/ContentIntent.kt
// Purpose: What a book is, as a listener hears it (a story, a poem, the news), so the voice reads it to suit (ADR-056).

package io.github.skm0078.audora.core.model

/**
 * What kind of reading a book calls for, told from its text in English, Hindi and Bengali (ADR-056). Each has a
 * delivery profile in `DeliveryConfig`: its pace, its pauses and whether lines are read with feeling. [GENERAL]
 * reads as Audora always has, so a wrong guess is never worse than no guess.
 */
enum class ContentIntent {
    /** Fiction for adults: narration and dialogue. Lines with feeling, dialogue a touch quicker. */
    STORY,

    /** A storybook: short sentences, "Once upon a time", "एक बार", "একদিন". Slower, warmer, more feeling. */
    CHILDREN,

    /** Short ragged lines, couplets: read slowly, with long pauses, no acted feeling. */
    POETRY,

    /** Scripture, prayers and hymns: unhurried and even, never acted. */
    SCRIPTURE,

    /** A news report: datelines, agencies, "said on Monday". Brisk and even. */
    NEWS,

    /** Official text: acts, notices, government reports, tables of figures. Even, slightly slower. */
    REPORT,

    /** Textbooks, manuals and papers: numbered sections, examples, citations. Clear, with room after headings. */
    STUDY,

    /** Nothing stands out, or too little was read to tell: Audora's plain reading. */
    GENERAL,
    ;

    /** Whether single sentences may be read with feeling (louder, softer, sadder); never in factual or sacred text. */
    val feelings: Boolean get() = this == STORY || this == CHILDREN || this == GENERAL

    companion object {
        /** [name] back to an intent; null for anything else (an older or newer app's value). */
        fun of(name: String?): ContentIntent? = entries.firstOrNull { it.name == name }
    }
}
