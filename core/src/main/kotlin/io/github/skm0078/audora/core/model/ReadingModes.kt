// File: core/src/main/kotlin/io/github/skm0078/audora/core/model/ReadingModes.kt
// Purpose: What playback reads, announces or skips (ADR-035), and the study modes that repeat or pause (plan item 31).

package io.github.skm0078.audora.core.model

/** How a table is read (RI-4b). */
enum class TableMode {
    /** The caption, then the table's size ("4 rows, 5 columns."); cells are not read. */
    SUMMARY,

    /** The size, then every row with its column names: "Row 1. Name: Ram. Age: 34." */
    ROWS,

    /** Nothing of the table is read; its caption still is. */
    SKIP,
}

/** How footnotes are read (RI-5b). */
enum class FootnoteMode {
    /** Where they are printed: after the page's text. */
    READ,
    SKIP,
}

/** How a display equation is read (RI-7b). A run of consecutive equation lines is one equation. */
enum class EquationMode {
    /** "Equation 3", with the number printed beside it, or just "Equation". */
    ANNOUNCE,

    /** Every line read out, maths and logic symbols as words. */
    AS_WRITTEN,
    SKIP,
}

/** How figure and table captions are read (RI-7b). */
enum class CaptionMode {
    /** A short sound before the caption's label, then the caption. */
    ANNOUNCE,
    SKIP,
}

/** What happens after each sentence (plan item 31, study modes). */
enum class AfterSentence {
    /** Reading goes straight on. */
    CONTINUE,

    /** A silence about as long as the sentence, to say it back (shadowing), then reading goes on. */
    GAP,

    /** Reading pauses; play goes on with the next sentence. */
    STOP,
}

/**
 * What playback does with each kind of unit `:pdf` tags (ADR-035). `:pdf` never drops a unit; these decide whether it
 * is read, announced or skipped. A skipped unit is still a resume point: resuming on it starts at the next unit read.
 *
 * @property skipUnreadable units scoring below SpeakabilityConfig.skipBelow (OCR noise, broken fonts, symbol soup)
 *   play a short sound instead of being read (RI-2b).
 * @property skipParentheses, [skipBrackets] and [skipLinks] leave out (asides), [notes] and web or e-mail addresses
 *   from the text read (plan item 2); a sentence left empty is not played.
 * @property announcePages says "Page 12" before a page's first sentence (plan item 2).
 * @property skim reads only headings, captions, table summaries and the first sentence of each paragraph or list item,
 *   to get through a chapter fast (plan item 64); the rest stays on the page and is still a resume point.
 * @property sentenceRepeats how many times each sentence is read, 1 to [MAX_SENTENCE_REPEATS] (plan item 31).
 * @property afterSentence what follows each sentence, after its last repeat (plan item 31).
 */
data class ReadingModes(
    val skipUnreadable: Boolean = true,
    val tables: TableMode = TableMode.SUMMARY,
    val footnotes: FootnoteMode = FootnoteMode.READ,
    val equations: EquationMode = EquationMode.ANNOUNCE,
    val captions: CaptionMode = CaptionMode.ANNOUNCE,
    val skipParentheses: Boolean = false,
    val skipBrackets: Boolean = false,
    val skipLinks: Boolean = false,
    val announcePages: Boolean = false,
    val skim: Boolean = false,
    val sentenceRepeats: Int = 1,
    val afterSentence: AfterSentence = AfterSentence.CONTINUE,
) {
    init {
        require(sentenceRepeats in 1..MAX_SENTENCE_REPEATS) {
            "sentenceRepeats must be in 1..$MAX_SENTENCE_REPEATS, was $sentenceRepeats"
        }
    }

    /** Whether a study mode is on, so playback must stop at each sentence's end to repeat or pause. */
    val studying: Boolean get() = sentenceRepeats > 1 || afterSentence == AfterSentence.STOP

    /**
     * One line for a settings file or a media session extra: `key=VALUE` pairs separated by `;`. [decode] reads it
     * back, so adding a mode later keeps older saved values readable.
     */
    fun encode(): String = listOf(
        KEY_SKIP_UNREADABLE to skipUnreadable.toString(),
        KEY_TABLES to tables.name,
        KEY_FOOTNOTES to footnotes.name,
        KEY_EQUATIONS to equations.name,
        KEY_CAPTIONS to captions.name,
        KEY_PARENTHESES to skipParentheses.toString(),
        KEY_BRACKETS to skipBrackets.toString(),
        KEY_LINKS to skipLinks.toString(),
        KEY_PAGES to announcePages.toString(),
        KEY_SKIM to skim.toString(),
        KEY_REPEATS to sentenceRepeats.toString(),
        KEY_AFTER_SENTENCE to afterSentence.name,
    ).joinToString(PAIR_SEPARATOR) { (key, value) -> "$key$VALUE_SEPARATOR$value" }

    companion object {
        /** The most repeats offered: beyond five a learner rewinds instead. */
        const val MAX_SENTENCE_REPEATS = 5
        private const val PAIR_SEPARATOR = ";"
        private const val VALUE_SEPARATOR = "="
        private const val KEY_SKIP_UNREADABLE = "skipUnreadable"
        private const val KEY_TABLES = "tables"
        private const val KEY_FOOTNOTES = "footnotes"
        private const val KEY_EQUATIONS = "equations"
        private const val KEY_CAPTIONS = "captions"
        private const val KEY_PARENTHESES = "skipParentheses"
        private const val KEY_BRACKETS = "skipBrackets"
        private const val KEY_LINKS = "skipLinks"
        private const val KEY_PAGES = "announcePages"
        private const val KEY_SKIM = "skim"
        private const val KEY_REPEATS = "sentenceRepeats"
        private const val KEY_AFTER_SENTENCE = "afterSentence"

        /** Reads [encode]'s line; a missing, unknown or malformed value takes its default, so this never fails. */
        fun decode(line: String?): ReadingModes {
            val values = line.orEmpty().split(PAIR_SEPARATOR).mapNotNull { pair ->
                pair.split(VALUE_SEPARATOR, limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() }
            }.toMap()
            val defaults = ReadingModes()
            return ReadingModes(
                skipUnreadable = values[KEY_SKIP_UNREADABLE]?.toBooleanStrictOrNull() ?: defaults.skipUnreadable,
                tables = enumOrNull<TableMode>(values[KEY_TABLES]) ?: defaults.tables,
                footnotes = enumOrNull<FootnoteMode>(values[KEY_FOOTNOTES]) ?: defaults.footnotes,
                equations = enumOrNull<EquationMode>(values[KEY_EQUATIONS]) ?: defaults.equations,
                captions = enumOrNull<CaptionMode>(values[KEY_CAPTIONS]) ?: defaults.captions,
                skipParentheses = values[KEY_PARENTHESES]?.toBooleanStrictOrNull() ?: defaults.skipParentheses,
                skipBrackets = values[KEY_BRACKETS]?.toBooleanStrictOrNull() ?: defaults.skipBrackets,
                skipLinks = values[KEY_LINKS]?.toBooleanStrictOrNull() ?: defaults.skipLinks,
                announcePages = values[KEY_PAGES]?.toBooleanStrictOrNull() ?: defaults.announcePages,
                skim = values[KEY_SKIM]?.toBooleanStrictOrNull() ?: defaults.skim,
                sentenceRepeats = values[KEY_REPEATS]?.toIntOrNull()?.takeIf { it in 1..MAX_SENTENCE_REPEATS }
                    ?: defaults.sentenceRepeats,
                afterSentence = enumOrNull<AfterSentence>(values[KEY_AFTER_SENTENCE]) ?: defaults.afterSentence,
            )
        }

        private inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? =
            enumValues<E>().firstOrNull { it.name == name }
    }
}
