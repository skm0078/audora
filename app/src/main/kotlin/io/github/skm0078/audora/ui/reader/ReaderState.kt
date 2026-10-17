// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/ReaderState.kt
// Purpose: Everything the reader screen draws, derived from the open PDF, the shown page and the player.

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.ReadingUnit
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

enum class ReaderFailure { DAMAGED, UNREADABLE, ENGINE_UNAVAILABLE }

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
    ) : PageContent

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
 * @property reporting the sentence a long-press picked for "This sounded wrong" (RI-8b), while its reasons show.
 * @property chapters the chapter list (plan item 11), from the PDF outline; empty without one.
 * @property chapterStarts the pages chapters start on, as the end-of-chapter sleep timer counts them.
 * @property unreadableTapped a greyed sentence the listener tapped (RI-9), while the screen explains why it is skipped.
 */
data class ReaderState(
    val bookId: BookId,
    val title: String? = null,
    val document: DocumentState = DocumentState.Loading,
    val page: Int = 0,
    val content: PageContent = PageContent.Loading,
    val following: Boolean = true,
    val playback: PlaybackSnapshot = PlaybackSnapshot.Connecting,
    val view: ReaderView = ReaderView.PAGE,
    val pageImage: PageImageState = PageImageState.None,
    val reporting: ReadingUnit? = null,
    val unreadableTapped: ReadingUnit? = null,
    val chapters: List<ChapterEntry> = emptyList(),
    val chapterStarts: List<Int> = emptyList(),
    val timeLeft: TimeLeftEstimate? = null,
) {
    val pageCount: Int? get() = (document as? DocumentState.Open)?.pageCount

    /** Where the player is in THIS book; null when it holds nothing or another book. */
    val readingPosition: ReadingPosition? get() = playback.position?.takeIf { it.bookId == bookId }

    /** This book is being read aloud (or buffering to be). */
    val isReading: Boolean get() = readingPosition != null && playback.wantsToPlay

    val playerReady: Boolean
        get() = document is DocumentState.Open && playback.connection == PlaybackSnapshot.Connection.CONNECTED

    /** Where "Undo jump" goes back to in this book (plan item 78), if anywhere. */
    val undoJumpTo: ReadingPosition? get() = playback.undoJumpTo?.takeIf { it.bookId == bookId }

    /** The A–B study range in this book (plan item 31), if one is marked. */
    val study: StudyRangeState? get() = playback.study?.takeIf { it.start.bookId == bookId }

    /** The reading position is on another page than the one shown. */
    val readingElsewhere: Boolean get() = readingPosition?.let { it.page != page } ?: false

    /** The chapter-list row being read (or shown, when nothing of this book is read), if any. */
    val currentChapter: Int? get() = ChapterList.currentIndex(chapters, readingPosition?.page ?: page)

    /** Index in the shown page's units of the sentence being read, if it is on this page. */
    val spokenUnitIndex: Int?
        get() {
            val position = readingPosition?.takeIf { it.page == page } ?: return null
            val units = (content as? PageContent.Ready)?.units ?: return null
            // Containment, not equality: robust if the service's units were cut slightly differently.
            return units.indexOfFirst { position.charStart in it.charStart until it.charEnd }
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
            val image = (pageImage as? PageImageState.Ready)?.takeIf { it.page == page }?.image ?: return emptyList()
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
            val image = (pageImage as? PageImageState.Ready)?.takeIf { it.page == page }?.image ?: return emptyList()
            val index = spokenUnitIndex ?: return emptyList()
            val units = (content as? PageContent.Ready)?.units ?: return emptyList()
            return units[index].bounds.orEmpty().map(image.transform::toView)
        }
}
