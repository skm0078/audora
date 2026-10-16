// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/ReaderModel.kt
// Purpose: Reader logic: opens the book, shows a page, starts and steers reading through the PlaybackPort.

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.config.ReaderConfig
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.pdf.document.PdfOpenError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Plain state holder (no Compose here), one per open book, living as long as the reader screen. Main thread.
 *
 * The reader never synthesizes anything: it shows the page's sentences from its own copy of the PDF and asks
 * the playback service to read from an position (C7: only ever on a tap). The PDF's password stays in this
 * object's memory and goes to the service with each start request; it is never saved or logged.
 */
// One small function per control on the screen; each constructor parameter is a seam the tests fake.
@Suppress("TooManyFunctions", "LongParameterList")
class ReaderModel(
    private val bookId: BookId,
    private val books: ReaderBooks,
    private val opener: ReaderDocumentOpener,
    private val player: PlaybackPort,
    private val speeds: SpeedPreference,
    private val config: ReaderConfig,
    private val scope: CoroutineScope,
    private val resume: ReaderResume = ReaderResume.None,
    autoResume: Boolean = false,
) {
    private val mutableState = MutableStateFlow(ReaderState(bookId))
    val state: StateFlow<ReaderState> = mutableState.asStateFlow()

    private val logContext = "book=${bookId.logLabel}"
    private var book: Book? = null
    private var document: ReaderDocument? = null
    private var password: String? = null
    private var openJob: Job? = null
    private var pageJob: Job? = null
    private var started = false

    /** Where this book was left (its resume anchor), when the player is not already on this book. */
    private var savedPosition: ReadingPosition? = null

    /**
     * "Resume" in the "Reading stopped unexpectedly" prompt (P1-07): a user action (C7), so reading starts by
     * itself once the book is open and the player connected. One shot: never repeats on reconnects.
     */
    private var pendingAutoResume = autoResume

    fun start() {
        if (started) return
        started = true
        scope.launch { player.state.collect(::onPlayback) }
        open(password = null)
    }

    fun submitPassword(password: String) = open(password)

    fun dismissPassword() = mutableState.update { it.copy(document = DocumentState.Locked) }

    fun askForPassword() = mutableState.update { it.copy(document = DocumentState.NeedsPassword(false)) }

    fun retry() = open(password = null)

    fun showPage(page: Int) {
        val pageCount = state.value.pageCount ?: return
        val target = page.coerceIn(0, pageCount - 1)
        mutableState.update { it.copy(following = it.readingPosition?.page == target) }
        loadPage(target)
    }

    fun nextPage() = showPage(state.value.page + 1)

    fun previousPage() = showPage(state.value.page - 1)

    /** Back to the page being read; the screen follows reading again. */
    fun showReadingPosition() {
        val position = state.value.readingPosition ?: return
        mutableState.update { it.copy(following = true) }
        loadPage(position.page)
    }

    /** Tap on a sentence: read from there. */
    fun readFrom(unit: ReadingUnit) = startReading(ReadingPosition(bookId, unit.page, unit.charStart))

    /**
     * Pauses while reading. Otherwise resumes where reading stopped if that is on the page shown, or starts at
     * the top of the page shown: what is on screen is what gets read ("start from page N").
     */
    fun playOrPause() {
        val current = state.value
        when {
            !current.playerReady -> AudoraLog.w(TAG, "Play ignored, $logContext player not ready")
            current.isReading -> player.pause()
            current.readingPosition?.page == current.page -> player.play()
            savedPosition?.page == current.page -> startReading(requireNotNull(savedPosition))
            else -> startReading(ReadingPosition.startOfPage(bookId, current.page))
        }
    }

    fun skipForward() = player.skipToNextSentence()

    fun skipBack() = player.skipToPreviousSentence()

    fun setSpeed(speed: Float) {
        val offered = config.nearestSpeed(speed)
        player.setSpeed(offered)
        scope.launch { speeds.save(offered) }
    }

    fun setSleepTimer(setting: SleepTimerSetting) = player.setSleepTimer(setting)

    /** Releases the PDF. Reading continues: the service has its own copy. */
    fun close() {
        openJob?.cancel()
        pageJob?.cancel()
        document?.close()
        document = null
    }

    private fun startReading(position: ReadingPosition) {
        if (!state.value.playerReady) {
            AudoraLog.w(TAG, "Start ignored, $logContext player not ready")
            return
        }
        mutableState.update { it.copy(following = true) }
        player.start(position, password)
        if (position.page != state.value.page) loadPage(position.page)
    }

    private fun open(password: String?) {
        openJob?.cancel()
        mutableState.update { it.copy(document = DocumentState.Loading) }
        openJob = scope.launch {
            val found = book ?: books.find(bookId)
            if (found == null) {
                AudoraLog.w(TAG, "Not in library, $logContext")
                mutableState.update { it.copy(document = DocumentState.NotInLibrary) }
                return@launch
            }
            book = found
            mutableState.update { it.copy(title = found.title) }
            when (val opened = opener.open(found.documentUri, password, logContext)) {
                is Outcome.Success -> onOpened(opened.value, password)
                is Outcome.Failure -> mutableState.update { it.copy(document = documentStateFor(opened.error)) }
            }
        }
    }

    private suspend fun onOpened(opened: ReaderDocument, password: String?) {
        document?.close()
        document = opened
        this.password = password
        mutableState.update { it.copy(document = DocumentState.Open(opened.pageCount)) }
        val playing = state.value.readingPosition
        if (playing == null) {
            savedPosition =
                resume.locate(bookId, opened.pageCount) { page -> (opened.units(page) as? Outcome.Success)?.value }
                    ?.let { ReadingPosition(bookId, it.page, it.charStart) }
        }
        val startPage = playing?.page ?: savedPosition?.page ?: 0
        loadPage(startPage.coerceIn(0, opened.pageCount - 1))
        books.markOpened(bookId, opened.pageCount)
        autoResumeIfDue()
    }

    private fun autoResumeIfDue() {
        val current = state.value
        if (!pendingAutoResume || document == null || !current.playerReady) return
        pendingAutoResume = false
        when {
            current.isReading -> Unit
            current.readingPosition != null -> player.play()
            else -> startReading(savedPosition ?: ReadingPosition.startOfPage(bookId, current.page))
        }
        AudoraLog.i(TAG, "Auto-resumed after an unexpected stop, $logContext")
    }

    private fun onPlayback(snapshot: PlaybackSnapshot) {
        val previous = state.value.playback
        mutableState.update { it.copy(playback = snapshot) }
        if (snapshot.connection == PlaybackSnapshot.Connection.CONNECTED &&
            previous.connection != PlaybackSnapshot.Connection.CONNECTED
        ) {
            applySavedSpeed(snapshot.speed)
        }
        autoResumeIfDue()
        if (snapshot.errorCode != null && snapshot.errorCode != previous.errorCode) {
            AudoraLog.w(TAG, "Playback error, $logContext code=${snapshot.errorCode}")
        }
        val current = state.value
        val position = current.readingPosition ?: return
        val pageCount = current.pageCount ?: return
        if (current.following && position.page != current.page && position.page < pageCount) loadPage(position.page)
    }

    private fun applySavedSpeed(playerSpeed: Float) {
        scope.launch {
            val saved = speeds.load()?.let(config::nearestSpeed) ?: config.defaultSpeed
            if (saved != playerSpeed) player.setSpeed(saved)
        }
    }

    /**
     * Reads the shown page again if it waits for the OCR models (P3-05): called when they finish downloading, so the
     * scanned page appears without the listener turning pages.
     */
    fun reloadIfWaitingForOcr() {
        val current = mutableState.value
        if ((current.content as? PageContent.Ready)?.origin == PageOrigin.NEEDS_OCR_MODELS) loadPage(current.page)
    }

    private fun loadPage(page: Int) {
        val open = document ?: return
        pageJob?.cancel()
        mutableState.update { it.copy(page = page, content = PageContent.Loading) }
        pageJob = scope.launch {
            val content = when (val shown = open.page(page)) {
                is Outcome.Success -> PageContent.Ready(shown.value.units, shown.value.origin)

                is Outcome.Failure -> {
                    AudoraLog.w(TAG, "Page failed, $logContext page=$page error=${shown.error}")
                    PageContent.Failed
                }
            }
            mutableState.update { if (it.page == page) it.copy(content = content) else it }
        }
    }

    private fun documentStateFor(error: PdfOpenError): DocumentState = when (error) {
        PdfOpenError.PasswordRequired -> DocumentState.NeedsPassword(wrongPassword = false)
        PdfOpenError.IncorrectPassword -> DocumentState.NeedsPassword(wrongPassword = true)
        is PdfOpenError.Damaged -> DocumentState.Failed(ReaderFailure.DAMAGED)
        is PdfOpenError.Unreadable -> DocumentState.Failed(ReaderFailure.UNREADABLE)
        is PdfOpenError.EngineUnavailable -> DocumentState.Failed(ReaderFailure.ENGINE_UNAVAILABLE)
    }

    private companion object {
        const val TAG = "Audora.Reader"
    }
}
