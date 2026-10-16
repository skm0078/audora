// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/ReaderActions.kt
// Purpose: Everything the reader screen can ask for; the activity wires it to the ReaderModel and navigation.

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.pdf.render.ViewPoint

/** One interface instead of many lambdas keeps [ReaderScreen]'s signature readable. */
@Suppress("TooManyFunctions") // One per control on the screen.
interface ReaderActions {
    fun onBack()

    fun onPreviousPage()

    fun onNextPage()

    /** Zero-based. */
    fun onGoToPage(page: Int)

    fun onShowReadingPosition()

    fun onSentenceTapped(unit: ReadingUnit)

    /** A tap on the page image, in fractions of its size: reads from the sentence there, if any. */
    fun onPageTapped(point: ViewPoint)

    fun onViewChanged(view: ReaderView)

    /** The page view's width in pixels, so pages are drawn at the screen's resolution. */
    fun onPageWidth(widthPx: Int)

    fun onPlayPause()

    fun onSkipBack()

    fun onSkipForward()

    fun onSpeed(speed: Float)

    fun onSleepTimer(setting: SleepTimerSetting)

    fun onPasswordSubmitted(password: String)

    fun onPasswordDismissed()

    fun onAskForPassword()

    fun onRetry()

    /** Download the text recognition files a scanned page needs (P3-05). */
    fun onDownloadOcrModels() = Unit
}
