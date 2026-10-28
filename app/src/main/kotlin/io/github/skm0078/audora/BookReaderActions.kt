// File: app/src/main/kotlin/io/github/skm0078/audora/BookReaderActions.kt
// Purpose: The reader screen's actions wired to its ReaderModel, notes and the app's jobs (moved out of MainActivity,
//          which had grown past detekt's class size).

package io.github.skm0078.audora

import android.content.Context
import android.widget.Toast
import androidx.compose.ui.graphics.asAndroidBitmap
import io.github.skm0078.audora.core.model.FocusSoundOptions
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.Pace
import io.github.skm0078.audora.core.model.ReadingModes
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.SleepOptions
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.model.VoiceShaping
import io.github.skm0078.audora.core.notes.Bookmark
import io.github.skm0078.audora.core.notes.Highlight
import io.github.skm0078.audora.core.share.SnippetBox
import io.github.skm0078.audora.pdf.outline.ChapterEntry
import io.github.skm0078.audora.pdf.render.ViewPoint
import io.github.skm0078.audora.playback.contract.StudyAction
import io.github.skm0078.audora.playback.export.AudioClips
import io.github.skm0078.audora.playback.export.AudiobookExport
import io.github.skm0078.audora.playback.export.ClipPassage
import io.github.skm0078.audora.playback.jobs.LongJobKind
import io.github.skm0078.audora.playback.report.SoundedWrongReason
import io.github.skm0078.audora.speech.ocr.OcrModels
import io.github.skm0078.audora.ui.jobs.startLongJob
import io.github.skm0078.audora.ui.reader.BookNotesModel
import io.github.skm0078.audora.ui.reader.PageImageState
import io.github.skm0078.audora.ui.reader.ReaderActions
import io.github.skm0078.audora.ui.reader.ReaderModel
import io.github.skm0078.audora.ui.reader.ReaderView
import io.github.skm0078.audora.ui.reader.SleepTimerSetting
import io.github.skm0078.audora.ui.reader.SoundedWrongFix
import io.github.skm0078.audora.ui.share.PageSnippets
import io.github.skm0078.audora.ui.share.ShareEntry

/** One per open book; [playWithPermission] asks for the notification permission before work that posts one. */
@Suppress("TooManyFunctions") // One per control on the reader screen.
internal class BookReaderActions(
    private val context: Context,
    private val model: ReaderModel,
    private val notes: BookNotesModel,
    private val playWithPermission: (() -> Unit) -> Unit,
    private val ocrModels: OcrModels,
    private val navigateBack: () -> Unit,
) : ReaderActions {
    override fun onBack() = navigateBack()

    override fun onPreviousPage() = model.previousPage()

    override fun onNextPage() = model.nextPage()

    override fun onGoToPage(page: Int) = model.showPage(page)

    override fun onShowReadingPosition() = model.showReadingPosition()

    override fun onSentenceTapped(unit: ReadingUnit) = tapped(unit)

    // Asks for the notification permission only when the tap is on a sentence; away from one, an edge tap turns.
    override fun onPageTapped(point: ViewPoint) {
        val unit = model.sentenceAt(point)
        if (unit != null) tapped(unit) else model.turnAtEdge(point)
    }

    // While a highlighted passage's end is being picked, a tap picks it instead of reading.
    private fun tapped(unit: ReadingUnit) {
        if (notes.state.value.rangeFrom != null) notes.endRange(unit) else readFrom(unit)
    }

    // A greyed sentence first explains why it is skipped (RI-9); the permission is asked if it is read anyway.
    private fun readFrom(unit: ReadingUnit) {
        if (model.state.value.isUnreadable(unit)) {
            model.readFrom(unit)
        } else {
            playWithPermission { model.readFrom(unit) }
        }
    }

    override fun onSentenceLongPressed(unit: ReadingUnit) = notes.openMenu(unit)

    override fun onPageLongPressed(point: ViewPoint) {
        model.sentenceAt(point)?.let(notes::openMenu)
    }

    override fun onReportSentence(unit: ReadingUnit) = model.askReport(unit)

    // The clip's progress and its Share button are in a notification, so it asks for that permission first.
    override fun onShareClip(unit: ReadingUnit) = playWithPermission {
        AudioClips.share(context, model.state.value.bookId, unit.page, unit.index, model.openPassword)
    }

    override fun onShareHighlight(highlight: Highlight) = playWithPermission {
        val passage = ClipPassage(highlight.anchor.charStart, highlight.endPage, highlight.endChar)
        AudioClips.sharePassage(
            context,
            model.state.value.bookId,
            highlight.anchor.page,
            passage,
            model.openPassword,
        )
    }

    override fun onSharePicture(lines: String, page: Int) =
        ShareEntry.quote(context, model.state.value.bookId, lines, page)

    override fun onShareVideo(lines: String, page: Int) =
        ShareEntry.video(context, model.state.value.bookId, lines, page)

    override fun onSharePagePart(unit: ReadingUnit) = model.startSnippet(unit)

    override fun onSnippetChanged(box: SnippetBox) = model.moveSnippet(box)

    override fun onSnippetCancel() = model.endSnippet()

    // The page as drawn on screen is cut, so a locked PDF needs no password again and nothing is rendered twice.
    override fun onSnippetShare() {
        val current = model.state.value
        val box = current.snippet ?: return
        val image = (current.pageImage as? PageImageState.Ready)?.takeIf { it.page == current.page }?.image
        model.endSnippet()
        if (image == null || !PageSnippets.hold(image.imageBitmap().asAndroidBitmap(), box)) {
            Toast.makeText(context, R.string.share_card_failed, Toast.LENGTH_LONG).show()
            return
        }
        ShareEntry.snippet(context, current.bookId, current.page)
    }

    // From the reader, a password-protected PDF can be saved too: its password goes along in memory (ADR-034).
    override fun onSaveAudiobook() = context.startLongJob(LongJobKind.AUDIOBOOK) {
        playWithPermission {
            AudiobookExport.start(context, model.state.value.bookId, model.openPassword)
        }
    }

    override fun onBookmark(bookmark: Bookmark) = model.goToBookmark(bookmark)

    override fun onBookmarkHere() {
        model.sentenceHere()?.let(notes::bookmark)
    }

    override fun onQuickBookmark() {
        model.sentenceHere()?.let(notes::quickBookmark)
    }

    override fun onOwnSettings(on: Boolean) = model.setOwnSettings(on)

    override fun onBookVoice(lang: Lang, voice: VoiceId) = model.setBookVoice(lang, voice)

    // The fixes dialog that follows says where the report went and how it leaves the phone.
    override fun onReport(reason: SoundedWrongReason) = model.report(reason)

    override fun onSoundedWrongFix(fix: SoundedWrongFix) {
        val unit = model.state.value.soundedWrong?.unit ?: return
        when (fix) {
            is SoundedWrongFix.Setting -> model.applySoundedWrongFix(fix)

            SoundedWrongFix.FixWord -> {
                model.dismissSoundedWrong()
                notes.fix(unit)
            }

            SoundedWrongFix.HearAgain -> readFrom(unit)
        }
    }

    override fun onSoundedWrongDone() = model.dismissSoundedWrong()

    override fun onReportDismissed() = model.dismissReport()

    override fun onReadUnreadable() = playWithPermission(model::readUnreadable)

    override fun onUnreadableDismissed() = model.dismissUnreadable()

    override fun onChapter(chapter: ChapterEntry) = model.goToChapter(chapter)

    override fun onViewChanged(view: ReaderView) = model.setView(view)

    override fun onPageWidth(widthPx: Int) = model.setPageWidth(widthPx)

    override fun onPageZoomed(zoomed: Boolean) = model.setZoomed(zoomed)

    override fun onPlayPause() {
        if (model.state.value.isReading) model.playOrPause() else playWithPermission(model::playOrPause)
    }

    override fun onSkipBack() = model.skipBack()

    override fun onSkipForward() = model.skipForward()

    override fun onSkipParagraph(forward: Boolean) = model.skipParagraph(forward)

    override fun onReplayPrevious() = model.replayPrevious()

    override fun onSpeed(speed: Float) = model.setSpeed(speed)

    override fun onPace(pace: Pace) = model.setPace(pace)

    override fun onClausePauses(on: Boolean) = model.setClausePauses(on)

    override fun onReadingModes(modes: ReadingModes) = model.setReadingModes(modes)

    override fun onSleepTimer(setting: SleepTimerSetting) = model.setSleepTimer(setting)

    override fun onSleepOptions(options: SleepOptions) = model.setSleepOptions(options)

    override fun onFocusSound(options: FocusSoundOptions) = model.setFocusSound(options)

    override fun onVoiceShaping(shaping: VoiceShaping) = model.setVoiceShaping(shaping)

    override fun onPitch(pitch: Float) = model.setPitch(pitch)

    override fun onUndoJump() = model.undoJump()

    override fun onStudy(action: StudyAction, loop: Boolean) = model.study(action, loop)

    override fun onPasswordSubmitted(password: String) = model.submitPassword(password)

    override fun onPasswordDismissed() = model.dismissPassword()

    override fun onAskForPassword() = model.askForPassword()

    override fun onRetry() = model.retry()

    override fun onDownloadOcrModels() = ocrModels.download()
}
