// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/report/SoundedWrongReport.kt
// Purpose: "This sounded wrong" (RI-8b): what a listener flags about one sentence, as one log line without its text.

package io.github.skm0078.audora.playback.report

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingModes
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.UnitKind
import io.github.skm0078.audora.core.model.VoiceId
import java.util.Locale

/** The reasons offered after a long-press on a sentence, in the order shown. */
enum class SoundedWrongReason {
    /** Noise or garbage was read out. */
    JUNK_READ,
    WRONG_WORD,
    TABLE,
    NOTE,
    PAUSE,
    OTHER,
}

/**
 * One flagged sentence. It rides in the log share (Settings → Share logs), so it carries where the sentence is and
 * how it was judged, never its text: the log may be sent to someone else (AGENTS.md, Logging).
 */
data class SoundedWrongReport(
    val bookId: BookId,
    val page: Int,
    val unitIndex: Int,
    val kind: UnitKind,
    val speakScore: Float?,
    val lang: Lang,
    val reason: SoundedWrongReason,
) {
    /**
     * The log line. [voice] is the voice reading [lang] when the report arrived (null when not known yet); [modes]
     * the reading modes then, which decide whether this kind of unit was read, announced or skipped.
     */
    fun logLine(voice: VoiceId?, modes: ReadingModes): String = listOf(
        "book" to bookId.logLabel,
        "page" to page.toString(),
        "unit" to unitIndex.toString(),
        "kind" to kind.name,
        "score" to (speakScore?.let { String.format(Locale.ROOT, SCORE_FORMAT, it) } ?: NONE),
        "lang" to lang.name,
        "engine" to (voice?.engine?.name?.lowercase(Locale.ROOT) ?: NONE),
        "voice" to (voice?.name ?: NONE),
        "reason" to reason.name,
        "modes" to modes.encode(),
    ).joinToString(separator = " ", prefix = "Sounded wrong, ") { (key, value) -> "$key=$value" }

    companion object {
        private const val SCORE_FORMAT = "%.2f"
        private const val NONE = "none"

        fun of(bookId: BookId, unit: ReadingUnit, reason: SoundedWrongReason) =
            SoundedWrongReport(bookId, unit.page, unit.index, unit.kind, unit.speakScore, unit.lang, reason)
    }
}
