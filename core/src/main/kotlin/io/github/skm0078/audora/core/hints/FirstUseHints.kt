// File: core/src/main/kotlin/io/github/skm0078/audora/core/hints/FirstUseHints.kt
// Purpose: The first-use hints (ADR 2026-10-30-first-use-hints): which ones a moment calls for, and which one, if
//          any, shows. Pure, so the "once each, one a session, never when muted" rules are unit-tested.

package io.github.skm0078.audora.core.hints

/**
 * A feature shown at the moment it would help. Each shows once ever; its key is saved, so it must never change.
 * The scanned-page hint of the findability plan is the reader's existing scanned-page card, which already offers the
 * download, so it is not one of these.
 */
enum class FirstUseHint(val key: String) {
    /** A book from Drive or another app: keep a copy so it reads without a connection. */
    KEEP_OFFLINE("keep_offline"),

    /** The speed is past what the voice speaks clearly and smart speed is off. */
    SMART_SPEED("smart_speed"),

    /** The book reads as a story and no voice is cast for its dialogue or characters. */
    CAST("cast"),

    /** The book has a highlight: highlights become quiz questions. */
    QUIZ("quiz"),

    /** The reading reached the book's last page. */
    FINISHED("finished"),

    /** A long book half read while the phone charges: prepare the rest ahead. */
    PREPARE_AHEAD("prepare_ahead"),

    /** The library has been visited a few times with one book in it. */
    MORE_BOOKS("more_books"),
    ;

    companion object {
        fun fromKey(key: String): FirstUseHint? = entries.firstOrNull { it.key == key }
    }
}

/** What the reader knows now, each fact already decided, so the order of the hints lives in one place. */
data class ReaderHintFacts(
    val cloudBook: Boolean = false,
    val fastWithoutSmartSpeed: Boolean = false,
    val storyWithoutCast: Boolean = false,
    val hasHighlight: Boolean = false,
    val onLastPage: Boolean = false,
    val longHalfReadWhileCharging: Boolean = false,
) {
    /** The hints these facts call for, most useful first. */
    fun candidates(): List<FirstUseHint> = buildList {
        if (cloudBook) add(FirstUseHint.KEEP_OFFLINE)
        if (fastWithoutSmartSpeed) add(FirstUseHint.SMART_SPEED)
        if (storyWithoutCast) add(FirstUseHint.CAST)
        if (hasHighlight) add(FirstUseHint.QUIZ)
        if (onLastPage) add(FirstUseHint.FINISHED)
        if (longHalfReadWhileCharging) add(FirstUseHint.PREPARE_AHEAD)
    }
}

/**
 * What the listener has seen, and whether this app start already showed a hint.
 *
 * @property showing the hint shown this app start, kept while it still applies so the card does not flicker away
 *   when another fact changes; null once answered or before any.
 */
data class HintHistory(
    val seen: Set<FirstUseHint> = emptySet(),
    val muted: Boolean = false,
    val shownThisStart: Boolean = false,
    val showing: FirstUseHint? = null,
)

/**
 * The hint to show among [candidates], or null: the one already showing while it still applies; else, unless muted
 * or one already showed since the app started, the first not seen.
 */
fun pickHint(candidates: List<FirstUseHint>, history: HintHistory): FirstUseHint? = when {
    history.muted -> null
    history.showing != null -> history.showing.takeIf { it in candidates }
    history.shownThisStart -> null
    else -> candidates.firstOrNull { it !in history.seen }
}
