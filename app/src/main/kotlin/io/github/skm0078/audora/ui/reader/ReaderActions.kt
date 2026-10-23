// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/ReaderActions.kt
// Purpose: Everything the reader screen can ask for; the activity wires it to the ReaderModel and navigation.

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.model.FocusSoundOptions
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.Pace
import io.github.skm0078.audora.core.model.ReadingModes
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.SleepOptions
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.model.VoiceShaping
import io.github.skm0078.audora.core.notes.Bookmark
import io.github.skm0078.audora.pdf.outline.ChapterEntry
import io.github.skm0078.audora.pdf.render.ViewPoint
import io.github.skm0078.audora.playback.contract.StudyAction
import io.github.skm0078.audora.playback.report.SoundedWrongReason

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

    /** "This sounded wrong" (RI-8b): a long-press on a sentence offers the reasons. */
    fun onSentenceLongPressed(unit: ReadingUnit) = Unit

    /** A long-press on the page image, as [onPageTapped]: reports the sentence there, if any. */
    fun onPageLongPressed(point: ViewPoint) = Unit

    fun onReport(reason: SoundedWrongReason) = Unit

    fun onReportDismissed() = Unit

    /** "Read it anyway" on a greyed sentence (RI-9). */
    fun onReadUnreadable() = Unit

    fun onUnreadableDismissed() = Unit

    /** A row of the chapter list (plan item 11). */
    fun onChapter(chapter: ChapterEntry) = Unit

    fun onViewChanged(view: ReaderView)

    /** The page view's width in pixels, so pages are drawn at the screen's resolution. */
    fun onPageWidth(widthPx: Int)

    fun onPlayPause()

    fun onSkipBack()

    fun onSkipForward()

    /** Long-press on next / previous: a paragraph on, or back. */
    fun onSkipParagraph(forward: Boolean) = Unit

    /** "What did it say?" (plan item 5): the sentence just heard again, a little slower. */
    fun onReplayPrevious() = Unit

    fun onSpeed(speed: Float)

    fun onPace(pace: Pace) = Unit

    fun onClausePauses(on: Boolean) = Unit

    fun onReadingModes(modes: ReadingModes) = Unit

    fun onVoiceShaping(shaping: VoiceShaping) = Unit

    fun onSleepTimer(setting: SleepTimerSetting)

    fun onSleepOptions(options: SleepOptions) = Unit

    fun onFocusSound(options: FocusSoundOptions) = Unit

    fun onUndoJump() = Unit

    /** Study loops (plan item 31): A, B, loop on or off, clear. */
    fun onStudy(action: StudyAction, loop: Boolean) = Unit

    /** "This sounded wrong" from the sentence menu (RI-8b): its reasons show. */
    fun onReportSentence(unit: ReadingUnit) = Unit

    /** "Share as audio" from the sentence menu (plan item 41): this sentence to the end of its paragraph, as a clip. */
    fun onShareClip(unit: ReadingUnit) = Unit

    /** Saves this book as audiobook files, with the password it was opened with (ADR-034). */
    fun onSaveAudiobook() = Unit

    /** A bookmark picked in the bookmarks sheet (plan item 29). */
    fun onBookmark(bookmark: Bookmark) = Unit

    /** "Bookmark here": the sentence being read on the shown page, else its first. */
    fun onBookmarkHere() = Unit

    /** "Use for this book only" in the speech sheet (plan item 4). */
    fun onOwnSettings(on: Boolean) = Unit

    /** A voice picked for this book in the speech sheet (plan item 4). */
    fun onBookVoice(lang: Lang, voice: VoiceId) = Unit

    fun onPasswordSubmitted(password: String)

    fun onPasswordDismissed()

    fun onAskForPassword()

    fun onRetry()

    /** Download the text recognition files a scanned page needs (P3-05). */
    fun onDownloadOcrModels() = Unit
}
