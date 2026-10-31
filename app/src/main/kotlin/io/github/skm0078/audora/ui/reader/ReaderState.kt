// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/ReaderState.kt
// Purpose: Everything the reader screen draws, derived from the open PDF, the shown page and the player.

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.share.SnippetBox
import io.github.skm0078.audora.pdf.outline.ChapterEntry
import io.github.skm0078.audora.pdf.outline.ChapterList
import io.github.skm0078.audora.pdf.render.ViewRect
import io.github.skm0078.audora.playback.reading.UnitReading

sealed interface DocumentState {
    data object Loading : DocumentState

    /** Removed from the library meanwhile (or never there, after a restore from saved state). */
    data object NotInLibrary : DocumentState

    data class NeedsPassword(val wrongPassword: Boolean) : DocumentState

    /** The password prompt was dismissed; the screen offers to show it again. */
    data object Locked : DocumentState

    data class Failed(val reason: ReaderFailure) : DocumentState

    data class Open(val pageCount: Int) : DocumentState
}

enum class ReaderFailure { DAMAGED, UNREADABLE, LOCKED, ENGINE_UNAVAILABLE }

/** Where the shown page's words come from: drives the "OCR" badge and the scanned-page notice (P3-05). */
enum class PageOrigin {
    /** The PDF's own text. */
    TEXT,

    /** Recognised from the page image. */
    OCR,

    /** The page needs OCR, but the text recognition files are not downloaded yet. */
    NEEDS_OCR_MODELS,

    /** The page needs OCR and it failed (logged). */
    OCR_FAILED,
}

sealed interface PageContent {
    data object Loading : PageContent

    /**
     * [units] is empty for a page without text (a blank page, or a scan that could not be read yet: see [origin]).
     * [ocrConfidence] is OCR's confidence in the page, 0–100, when it was read by OCR (RI-9).
     */
    data class Ready(
        val units: List<ReadingUnit>,
        val origin: PageOrigin = PageOrigin.TEXT,
        val ocrConfidence: Int? = null,
    ) : PageContent {
        /** Read for good: not waiting for the OCR models or failed, so it may be kept for a later turn to it. */
        val isFinal: Boolean get() = origin == PageOrigin.TEXT || origin == PageOrigin.OCR
    }

    data object Failed : PageContent
}

/**
 * Listening time left at the listener's speed and pace (plan item 24), from the reading position (or the top of the
 * page shown); [chapterMs] is null in a book without chapters.
 */
data class TimeLeftEstimate(val bookMs: Long, val chapterMs: Long?)

/**
 * @property page the zero-based page on screen. Not necessarily the page being read: the listener can browse.
 * @property following the screen turns pages with the reading position; browsing elsewhere stops that until
 *   the listener starts reading again or goes back to the reading position.
 * @property view the page drawn as in the PDF, or its sentences as text; the listener's choice, remembered.
 * @property pageImage the shown page drawn, in the page view.
 * @property nearbyPages the pages beside the shown one drawn ahead, by page, for the page view to slide in under a
 *   finger (ADR 2026-10-28-page-flip).
 * @property reporting the sentence a long-press picked for "This sounded wrong" (RI-8b), while its reasons show.
 * @property soundedWrong the reason picked for it and the fixes offered, while they show.
 * @property chapters the chapter list (plan item 11), from the PDF outline; empty without one.
 * @property chapterStarts the pages chapters start on, as the end-of-chapter sleep timer counts them.
 * @property unreadableTapped a greyed sentence the listener tapped (RI-9), while the screen explains why it is skipped.
 * @property snippet while part of the shown page is picked to share as a picture, its box (ADR
 *   2026-10-28-page-snippet); cleared when the page turns.
 */
data class ReaderState(
    val bookId: BookId,
    val title: String? = null,
    /** The book's author when known, for the credit beside shared lines. */
    val author: String? = null,
    val document: DocumentState = DocumentState.Loading,
    val page: Int = 0,
    val content: PageContent = PageContent.Loading,
    val following: Boolean = true,
    val playback: PlaybackSnapshot = PlaybackSnapshot.Connecting,
    val view: ReaderView = ReaderView.PAGE,
    val pageImage: PageImageState = PageImageState.None,
    val nearbyPages: Map<Int, PageImage> = emptyMap(),
    val reporting: ReadingUnit? = null,
    val soundedWrong: SoundedWrongOffer? = null,
    val unreadableTapped: ReadingUnit? = null,
    val chapters: List<ChapterEntry> = emptyList(),
    val chapterStarts: List<Int> = emptyList(),
    val timeLeft: TimeLeftEstimate? = null,
    /** A paragraph skip found no paragraph that way: shown once, then cleared ([ReaderModel.skipRefusalShown]). */
    val skipRefused: ParagraphSkipRefusal? = null,
    /** The book opened where it was left: said once, then cleared ([ReaderModel.resumedShown]). */
    val resumedAt: ResumedAt? = null,
    /** Opened after days away: the recap shown instead of [resumedAt], until a button or a tap outside clears it. */
    val previouslyOn: PreviouslyOn? = null,
    /** The book's first open: its opening line shows once the first page has words ([ReaderModel.openingLineShown]). */
    val firstOpen: Boolean = false,
    /**
     * The book opened past its front matter (ADR 2026-10-31-skip-front-matter): the notice saying so shows until
     * its OK or "Read from the start" ([ReaderModel.frontMatterNoticeShown], [ReaderModel.readFromStart]).
     */
    val skippedFrontMatter: SkippedFrontMatter? = null,
    /** The page the book opened on for its first open: where its opening line comes from. */
    val startPage: Int = 0,
    val snippet: SnippetBox? = null,
) {
    val pageCount: Int? get() = (document as? DocumentState.Open)?.pageCount

    /** The drawn image of the page shown, once it is ready; null while it renders or shows another page. */
    val shownPageImage: PageImage? get() = (pageImage as? PageImageState.Ready)?.takeIf { it.page == page }?.image

    /** Where the player is in THIS book; null when it holds nothing or another book. */
    val readingPosition: ReadingPosition? get() = playback.position?.takeIf { it.bookId == bookId }

    /** This book is being read aloud (or buffering to be). */
    val isReading: Boolean get() = readingPosition != null && playback.wantsToPlay

    /** The shown page and how far its text is, for the log: "page=2 Loading", "page=2 ready", "page=2 empty". */
    val pageForLog: String
        get() = "page=$page " + when (val shown = content) {
            is PageContent.Ready -> if (shown.units.isEmpty()) "empty" else "ready"
            else -> shown::class.simpleName.orEmpty()
        }

    val playerReady: Boolean
        get() = document is DocumentState.Open && playback.connection == PlaybackSnapshot.Connection.CONNECTED

    /**
     * The language whose voice the first Play would need, while loading it ahead makes sense: the shown page's
     * sentences are known, the player is connected and nothing plays (a second model loading beside one that reads
     * would slow it). Null otherwise (ADR 2026-10-29-speed-quick-wins).
     */
    val voiceToWarm: Lang?
        get() = (content as? PageContent.Ready)?.units?.firstOrNull()?.lang
            ?.takeIf { playerReady && !playback.wantsToPlay }

    /**
     * The first open's opening line: the first sentence with a letter in it on the page the book opened on (past any
     * skipped front matter), once that page is read.
     */
    val openingLine: ReadingUnit?
        get() = (content as? PageContent.Ready)?.units?.takeIf { firstOpen && page == startPage }
            ?.firstOrNull { unit -> unit.text.any(Char::isLetter) }

    /** Where "Undo jump" goes back to in this book (plan item 78), if anywhere. */
    val undoJumpTo: ReadingPosition? get() = playback.undoJumpTo?.takeIf { it.bookId == bookId }

    /** The A–B study range in this book (plan item 31), if one is marked. */
    val study: StudyRangeState? get() = playback.study?.takeIf { it.start.bookId == bookId }

    /** The reading position is on another page than the one shown. */
    val readingElsewhere: Boolean get() = readingPosition?.let { it.page != page } ?: false

    /**
     * What "Bookmark here" and the player's bookmark button mark: the sentence being read on the shown page, else the
     * page's first sentence; null while the page loads.
     */
    val sentenceHere: ReadingUnit?
        get() {
            val units = (content as? PageContent.Ready)?.units ?: return null
            return spokenUnitIndex?.let(units::getOrNull) ?: units.firstOrNull()
        }

    /** The text of zero-based [page] when it is the page shown and loaded, its sentences in order; else null. */
    fun pageText(page: Int): String? {
        val units = (content as? PageContent.Ready)?.units?.takeIf { page == this.page } ?: return null
        return units.map { it.text }.filter(String::isNotBlank).joinToString(" ").takeIf(String::isNotBlank)
    }

    /** The chapter-list row being read (or shown, when nothing of this book is read), if any. */
    val currentChapter: Int? get() = ChapterList.currentIndex(chapters, readingPosition?.page ?: page)

    /** Index in the shown page's units of the sentence being read, if it is on this page. */
    val spokenUnitIndex: Int?
        get() {
            val position = readingPosition?.takeIf { it.page == page } ?: return null
            val units = (content as? PageContent.Ready)?.units ?: return null
            // Containment, not equality: robust if the service's units were cut slightly differently. A unit with no
            // text (a continued table's summary, sharing its first row's charStart) is never the one shown as read.
            return units.indexOfFirst { it.text.isNotBlank() && position.charStart in it.charStart until it.charEnd }
                .takeIf { it >= 0 }
        }

    /**
     * Whether playback skips [unit] as unreadable with the listener's reading modes (RI-2b): the screen greys it
     * (RI-9). The same rule the service applies, so what is greyed is exactly what plays a cue.
     */
    fun isUnreadable(unit: ReadingUnit): Boolean = UnitReading(playback.readingModes).isUnreadable(unit)

    /** The lines of every unreadable sentence on the page image, to grey (RI-9); empty as for [spokenHighlight]. */
    val unreadableHighlight: List<ViewRect>
        get() {
            val image = shownPageImage ?: return emptyList()
            val units = (content as? PageContent.Ready)?.units ?: return emptyList()
            val reading = UnitReading(playback.readingModes)
            return units.filter(reading::isUnreadable).flatMap { it.bounds.orEmpty() }.map(image.transform::toView)
        }

    /**
     * The spoken sentence's lines on the page image (P4-03): one rect per line, in fractions of the image.
     * Empty when the sentence is not on the page shown, the image is not ready, or the text has no boxes (OCR).
     */
    val spokenHighlight: List<ViewRect>
        get() {
            val image = shownPageImage ?: return emptyList()
            val index = spokenUnitIndex ?: return emptyList()
            val units = (content as? PageContent.Ready)?.units ?: return emptyList()
            return units[index].bounds.orEmpty().map(image.transform::toView)
        }
}

/** Why a paragraph skip did nothing, for a short notice. */
enum class ParagraphSkipRefusal {
    /** Forward, with no paragraph after this one (the book's end). */
    NO_NEXT,

    /** Back, at the book's first sentence. */
    NO_PREVIOUS,
}
