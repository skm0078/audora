// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/reading/UnitReading.kt
// Purpose: Applies the reading modes: which units of a page are played, and what the voice says for each (ADR-035).

package io.github.skm0078.audora.playback.reading

import io.github.skm0078.audora.core.config.ReadingUnitConfig
import io.github.skm0078.audora.core.config.SpeakabilityConfig
import io.github.skm0078.audora.core.model.CaptionMode
import io.github.skm0078.audora.core.model.EquationMode
import io.github.skm0078.audora.core.model.FootnoteMode
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingModes
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.TableMode
import io.github.skm0078.audora.core.model.UnitKind
import io.github.skm0078.audora.speech.normalize.TableSpeech
import io.github.skm0078.audora.speech.normalize.TextRole
import io.github.skm0078.audora.speech.normalize.TextSkips

/** What the voice is given for one played unit. */
sealed interface Speech {
    /** Speak [text] (written text; the normalizers still run) as [role], after a short cue when [cueBefore]. */
    data class Text(val text: String, val role: TextRole, val cueBefore: Boolean = false) : Speech

    /** Only the cue: a unit too garbled to read (RI-2b). */
    data object Cue : Speech
}

/**
 * The one place the reading modes live, used alike by the playlist ([plays]), the renderer ([speechOf]), the
 * audiobook export and the listening score, so all four agree. Pure; build one per decision, it is cheap.
 *
 * A unit left out of [plays] is still a resume point: resume starts at the first unit that ends after the saved
 * offset, which is then the next unit played (`PageCursor`). Decisions per mode:
 * - **Unreadable** (`speakScore` below [SpeakabilityConfig.skipBelow], "Skip unreadable text" on): a run of them plays
 *   one cue, so a page of OCR noise is one soft sound, not twenty. Tables and equations have their own modes and are
 *   never judged as junk (a row of numbers scores like symbol soup).
 * - **Tables:** Summary plays the header unit as the table's size; Row by row adds each row with its column names;
 *   Skip leaves the table out (its caption is a caption).
 * - **Footnotes:** read where printed (after the page's text) or left out.
 * - **Equations:** a run of consecutive equation lines is one equation. Announce plays only its last line, as
 *   "Equation 3" (the number printed at its end) or "Equation"; As written reads every line with maths symbols as
 *   words; Skip leaves the run out.
 * - **Captions:** Announce plays a cue before a caption's label unit ("Fig. 2."), then reads it; Skip leaves it out.
 * - **Chapter starts:** a heading that opens a chapter page (the PDF outline's, [chapterPages]) gets a cue.
 * - **Skip switches** (plan item 2): (parentheses), [brackets] and links leave the text of every unit but tables and
 *   equations; a unit left empty is not played. "Page numbers" says "Page 12" before the page's first unit (index 0;
 *   when that unit is itself skipped, the page goes unannounced).
 * - **Skim** (plan item 64): headings, captions and table summaries, and the first sentence of each paragraph, list
 *   item or aside: one that follows a paragraph's end (a pause longer than a sentence's, [ReadingUnitConfig]), a
 *   change of kind, or opens the page. Table rows, footnotes and equations are left out.
 */
class UnitReading(
    private val modes: ReadingModes,
    private val speakability: SpeakabilityConfig = SpeakabilityConfig(),
    private val chapterPages: Set<Int> = emptySet(),
    private val unitConfig: ReadingUnitConfig = ReadingUnitConfig(),
) {
    private val skips = TextSkips(modes.skipParentheses, modes.skipBrackets, modes.skipLinks)

    /** The units of one page (in order) that go into the playlist, in order. */
    fun plays(units: List<ReadingUnit>): List<ReadingUnit> = units.filterIndexed { i, unit ->
        val previous = units.getOrNull(i - 1)
        when {
            !playsKind(unit, next = units.getOrNull(i + 1)) -> false
            modes.skim && !skimKeeps(unit, previous, unitConfig.sentencePauseMs) -> false
            leftEmpty(unit) -> false
            isUnreadable(unit) -> previous == null || !isUnreadable(previous)
            else -> true
        }
    }

    /** The units [plays] leaves out as unreadable junk (for the per-page log line). */
    fun unreadableCount(units: List<ReadingUnit>): Int = units.count(::isUnreadable)

    /** What the voice says for [unit], one that [plays] kept. */
    fun speechOf(unit: ReadingUnit): Speech {
        if (isUnreadable(unit)) return Speech.Cue
        val speech = textOf(unit)
        return if (modes.announcePages && unit.index == 0) {
            speech.copy(text = "${pageWords(unit)} ${speech.text}")
        } else {
            speech
        }
    }

    private fun textOf(unit: ReadingUnit): Speech.Text {
        val table = unit.table
        return when (unit.kind) {
            UnitKind.TABLE_SUMMARY, UnitKind.TABLE_ROW -> Speech.Text(
                if (table == null) unit.text else TableSpeech.row(table, unit.text, unit.lang),
                TextRole.BODY,
            )

            UnitKind.EQUATION -> when (modes.equations) {
                EquationMode.ANNOUNCE -> Speech.Text(announceEquation(unit), TextRole.BODY)
                EquationMode.AS_WRITTEN, EquationMode.SKIP -> Speech.Text(unit.text, TextRole.EQUATION)
            }

            UnitKind.CAPTION -> Speech.Text(
                skips.apply(unit.text),
                TextRole.BODY,
                cueBefore = CAPTION_LABEL.containsMatchIn(unit.text),
            )

            UnitKind.HEADING -> Speech.Text(skips.apply(unit.text), TextRole.HEADING, cueBefore = opensChapter(unit))

            UnitKind.BODY, UnitKind.LIST_ITEM, UnitKind.FOOTNOTE, UnitKind.ASIDE -> Speech.Text(
                skips.apply(unit.text),
                TextRole.BODY,
            )
        }
    }

    /** Whether [unit] is junk that "Skip unreadable text" replaces with a cue. */
    fun isUnreadable(unit: ReadingUnit): Boolean {
        val judged = modes.skipUnreadable && !unit.kind.isTable && unit.kind != UnitKind.EQUATION
        val score = unit.speakScore
        return judged && score != null && score < speakability.skipBelow
    }

    private fun playsKind(unit: ReadingUnit, next: ReadingUnit?): Boolean = when (unit.kind) {
        UnitKind.TABLE_SUMMARY -> modes.tables != TableMode.SKIP

        UnitKind.TABLE_ROW -> modes.tables == TableMode.ROWS

        UnitKind.FOOTNOTE -> modes.footnotes == FootnoteMode.READ

        UnitKind.CAPTION -> modes.captions == CaptionMode.ANNOUNCE

        UnitKind.EQUATION -> when (modes.equations) {
            EquationMode.ANNOUNCE -> next?.kind != UnitKind.EQUATION
            EquationMode.AS_WRITTEN -> true
            EquationMode.SKIP -> false
        }

        UnitKind.BODY, UnitKind.HEADING, UnitKind.LIST_ITEM, UnitKind.ASIDE -> true
    }

    /** A unit of plain text the skip switches leave with nothing to say (a sentence that was all aside). */
    private fun leftEmpty(unit: ReadingUnit): Boolean =
        !skips.isNone && !unit.kind.isTable && unit.kind != UnitKind.EQUATION && skips.apply(unit.text).isBlank()

    /** "Page 12." in the unit's language; the page as the notification counts it (from 1), not the printed label. */
    private fun pageWords(unit: ReadingUnit): String {
        val stop = if (unit.lang == Lang.EN) "." else "।"
        return "${PAGE_WORD.getValue(unit.lang)} ${unit.page + 1}$stop"
    }

    /** The heading at the top of a chapter's first page (index 0: running headers are already gone). */
    private fun opensChapter(unit: ReadingUnit): Boolean = unit.index == 0 && unit.page in chapterPages

    /** "Equation 3." from the "(3)" printed at the end of the run's last line, or just "Equation." */
    private fun announceEquation(unit: ReadingUnit): String {
        val word = EQUATION_WORD.getValue(unit.lang)
        val stop = if (unit.lang == Lang.EN) "." else "।"
        val number = EQUATION_NUMBER.find(unit.text)?.groupValues?.get(1)
        return if (number == null) "$word$stop" else "$word $number$stop"
    }

    private companion object {
        val EQUATION_WORD = mapOf(Lang.EN to "Equation", Lang.HI to "समीकरण", Lang.BN to "সমীকরণ")
        val PAGE_WORD = mapOf(Lang.EN to "Page", Lang.HI to "पृष्ठ", Lang.BN to "পৃষ্ঠা")

        /** "(3)", "(2.14)", "(4a)" at the end of a line; Devanagari and Bengali digits count too. */
        val EQUATION_NUMBER = Regex("""\(\s*(\p{Nd}+(?:\.\p{Nd}+)*[a-z]?)\s*\)\s*$""")

        /** A caption's label opens it: "Fig. 2", "Figure 3:", "Table IV", "चित्र 4", "সারণি ২". */
        val CAPTION_LABEL = Regex(
            """^\s*(?:fig(?:ure)?s?\.?|tab(?:le)?s?\.?|चित्र|तालिका|सारणी|চিত্র|সারণি|সারণী)\s*(?:\p{Nd}|[IVXLC]+\b)""",
            RegexOption.IGNORE_CASE,
        )
    }
}

/** Skim mode's choice (plan item 64); [previous] is the unit before on the page, played or not. */
private fun skimKeeps(unit: ReadingUnit, previous: ReadingUnit?, sentencePauseMs: Int): Boolean = when (unit.kind) {
    UnitKind.HEADING, UnitKind.CAPTION, UnitKind.TABLE_SUMMARY -> true

    UnitKind.TABLE_ROW, UnitKind.FOOTNOTE, UnitKind.EQUATION -> false

    UnitKind.BODY, UnitKind.LIST_ITEM, UnitKind.ASIDE ->
        previous == null || previous.kind != unit.kind || previous.pauseAfterMs > sentencePauseMs
}
