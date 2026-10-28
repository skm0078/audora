// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/reader/NoReaderActions.kt
// Purpose: A reader that does nothing, for tests to delegate to and override only the actions they watch.

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.pdf.render.ViewPoint

internal object NoReaderActions : ReaderActions {
    override fun onBack() = Unit
    override fun onPreviousPage() = Unit
    override fun onNextPage() = Unit
    override fun onGoToPage(page: Int) = Unit
    override fun onShowReadingPosition() = Unit
    override fun onSentenceTapped(unit: ReadingUnit) = Unit
    override fun onPageTapped(point: ViewPoint) = Unit
    override fun onViewChanged(view: ReaderView) = Unit
    override fun onPageWidth(widthPx: Int) = Unit
    override fun onPlayPause() = Unit
    override fun onSkipBack() = Unit
    override fun onSkipForward() = Unit
    override fun onSpeed(speed: Float) = Unit
    override fun onSleepTimer(setting: SleepTimerSetting) = Unit
    override fun onPasswordSubmitted(password: String) = Unit
    override fun onPasswordDismissed() = Unit
    override fun onAskForPassword() = Unit
    override fun onRetry() = Unit
}
