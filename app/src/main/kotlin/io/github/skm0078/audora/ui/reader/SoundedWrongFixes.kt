// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/SoundedWrongFixes.kt
// Purpose: What "This sounded wrong" offers to do about each reason, from the settings in use, so a report changes
//          the reading now instead of only reaching the log (phone test issue 7).

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.model.FootnoteMode
import io.github.skm0078.audora.core.model.Pace
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.TableMode
import io.github.skm0078.audora.playback.report.SoundedWrongReason

/** One thing the listener can do about a sentence that sounded wrong. */
sealed interface SoundedWrongFix {
    /** Short, for the log: which fix was taken, never the sentence. */
    val logName: String

    /** Opens "Fix how a word sounds" on the sentence. */
    data object FixWord : SoundedWrongFix {
        override val logName = "fixWord"
    }

    /** Reads the sentence again, to hear whether a fix worked. */
    data object HearAgain : SoundedWrongFix {
        override val logName = "hearAgain"
    }

    /** A reading setting, changed for this book's player at once (as in the Speech sheet). */
    sealed interface Setting : SoundedWrongFix

    data object SkipUnreadable : Setting {
        override val logName = "skipUnreadable"
    }

    data class Tables(val mode: TableMode) : Setting {
        override val logName get() = "tables=${mode.name}"
    }

    data class Footnotes(val mode: FootnoteMode) : Setting {
        override val logName get() = "footnotes=${mode.name}"
    }

    /** Leaves out [notes in square brackets]. */
    data object SkipBrackets : Setting {
        override val logName = "skipBrackets"
    }

    /** Pauses inside a sentence at dashes, colons and brackets (RI-6). */
    data class ClausePauses(val on: Boolean) : Setting {
        override val logName get() = "clausePauses=$on"
    }

    /** The pause between sentences. */
    data class SentencePause(val pace: Pace) : Setting {
        override val logName get() = "pace=${pace.name}"
    }
}

/** A reason was picked for [unit]: its report is in the log, and these fixes are offered. */
data class SoundedWrongOffer(val unit: ReadingUnit, val reason: SoundedWrongReason, val fixes: List<SoundedWrongFix>)

object SoundedWrongFixes {
    /**
     * The fixes for [reason] that would change something with the settings in [playback]; "Read it again" always
     * comes last. "Something else" and junk that "Skip unreadable text" already skips have no fix but the report.
     */
    fun offered(reason: SoundedWrongReason, playback: PlaybackSnapshot): List<SoundedWrongFix> {
        val modes = playback.readingModes
        val fixes = when (reason) {
            SoundedWrongReason.JUNK_READ -> listOfNotNull(
                SoundedWrongFix.SkipUnreadable.takeUnless {
                    modes.skipUnreadable
                },
            )

            SoundedWrongReason.WRONG_WORD -> listOf(SoundedWrongFix.FixWord)

            SoundedWrongReason.TABLE -> TableMode.entries.filter { it != modes.tables }.map(SoundedWrongFix::Tables)

            SoundedWrongReason.NOTE -> listOfNotNull(
                SoundedWrongFix.Footnotes(
                    if (modes.footnotes == FootnoteMode.READ) FootnoteMode.SKIP else FootnoteMode.READ,
                ),
                SoundedWrongFix.SkipBrackets.takeUnless { modes.skipBrackets },
            )

            SoundedWrongReason.PAUSE -> listOf(SoundedWrongFix.ClausePauses(!playback.clausePauses)) +
                Pace.entries.filter { it != playback.pace }.map(SoundedWrongFix::SentencePause)

            SoundedWrongReason.OTHER -> emptyList()
        }
        return fixes + SoundedWrongFix.HearAgain
    }
}
