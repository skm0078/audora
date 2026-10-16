// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/ReaderState.kt
// Purpose: Everything the reader screen draws, derived from the open PDF, the shown page and the player.

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.pdf.render.ViewRect

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

sealed interface PageContent {
    data object Loading : PageContent

    /** Empty for a page without text (a scan, before OCR in Phase 3). */
    data class Ready(val units: List<ReadingUnit>) : PageContent

    data object Failed : PageContent
}

/**
 * @property page the zero-based page on screen. Not necessarily the page being read: the listener can browse.
 * @property following the screen turns pages with the reading position; browsing elsewhere stops that until
 *   the listener starts reading again or goes back to the reading position.
 * @property view the page drawn as in the PDF, or its sentences as text; the listener's choice, remembered.
 * @property pageImage the shown page drawn, in the page view.
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
) {
    val pageCount: Int? get() = (document as? DocumentState.Open)?.pageCount

    /** Where the player is in THIS book; null when it holds nothing or another book. */
    val readingPosition: ReadingPosition? get() = playback.position?.takeIf { it.bookId == bookId }

    /** This book is being read aloud (or buffering to be). */
    val isReading: Boolean get() = readingPosition != null && playback.wantsToPlay

    val playerReady: Boolean
        get() = document is DocumentState.Open && playback.connection == PlaybackSnapshot.Connection.CONNECTED

    /** The reading position is on another page than the one shown. */
    val readingElsewhere: Boolean get() = readingPosition?.let { it.page != page } ?: false

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
