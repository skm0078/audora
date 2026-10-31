// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/ReaderModel.kt
// Purpose: Reader logic: opens the book, shows a page (drawn or as text), starts and steers reading.

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.config.ReaderConfig
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.FocusSoundOptions
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.Pace
import io.github.skm0078.audora.core.model.ReadingModes
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.SleepOptions
import io.github.skm0078.audora.core.model.SpeechStyle
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.model.VoiceShaping
import io.github.skm0078.audora.core.notes.Bookmark
import io.github.skm0078.audora.core.progress.TimeLeft
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.resume.ReAnchoring
import io.github.skm0078.audora.core.resume.ResumeAnchor
import io.github.skm0078.audora.core.share.SnippetBox
import io.github.skm0078.audora.pdf.PIPELINE_VERSION
import io.github.skm0078.audora.pdf.document.PdfOpenError
import io.github.skm0078.audora.pdf.outline.ChapterEntry
import io.github.skm0078.audora.pdf.outline.ChapterList
import io.github.skm0078.audora.pdf.outline.ChapterStarts
import io.github.skm0078.audora.pdf.render.SentenceHitTest
import io.github.skm0078.audora.pdf.render.ViewPoint
import io.github.skm0078.audora.playback.contract.StudyAction
import io.github.skm0078.audora.playback.report.SoundedWrongReason
import io.github.skm0078.audora.playback.report.SoundedWrongReport
import io.github.skm0078.audora.ui.reader.flip.PageNeighbours
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val views: ViewPreference = ViewPreference.None,
    private val pipelineVersion: Int = PIPELINE_VERSION,
    private val textSettings: Flow<Int> = emptyFlow(),
    shownPage: Int? = null,
    private val now: () -> Long = System::currentTimeMillis,
    private val bookStart: BookStart = BookStart.None,
) {
    private val mutableState = MutableStateFlow(ReaderState(bookId))
    val state: StateFlow<ReaderState> = mutableState.asStateFlow()

    private val logContext = "book=${bookId.logLabel}"
    private var book: Book? = null
    private var document: ReaderDocument? = null
    private var password: String? = null

    /** The password the book was opened with, for a job that reopens it (a clip, plan item 41); never stored. */
    val openPassword: String? get() = password

    private var openJob: Job? = null
    private var pageJob: Job? = null
    private var renderJob: Job? = null

    // One page drawing at a time: the page view and the scrubber's thumbnails share one PDFium renderer.
    private val renderLock = Mutex()

    /** The page view's width in pixels, once the screen has laid it out; pages are drawn at this width. */
    private var pageWidthPx: Int? = null
    private var started = false

    /** The pages beside the shown one, drawn and read ahead for a turn by hand (ADR 2026-10-28-page-flip). */
    private val neighbours = PageNeighbours(config.flip)
    private var aheadJob: Job? = null

    /** The page is zoomed in: nothing is drawn ahead, as each page would be drawn at the zoomed size. */
    private var zoomed = false

    /**
     * The page to show again instead of the resume point, with no "Resumed at" notice: the page on screen when the
     * book was opened again for new text settings, or [shownPage] when the screen was rebuilt (a rotation, a theme
     * switch, the app coming back after Android closed it).
     */
    private var keepPage: Int? = shownPage

    /** Where this book was left (its resume anchor), when the player is not already on this book. */
    private var savedPosition: ReadingPosition? = null

    /**
     * "Resume" in the "Reading stopped unexpectedly" prompt (P1-07): a user action (C7), so reading starts by
     * itself once the book is open and the player connected. One shot: never repeats on reconnects.
     */
    private var pendingAutoResume = autoResume

    /** Loads the voice while the book is open and quiet, so the first Play does not wait for it. */
    private val voiceWarmUp = ReaderVoiceWarmUp(scope, config.voiceWarmUpDelayMs) { lang ->
        AudoraLog.i(TAG, "Asking the player to load the voice, book=${bookId.logLabel} lang=${lang.name}")
        player.warmVoice(bookId, lang)
    }

    private val timeLeft = TimeLeft(config.timeLeft, config.speech)
    private val reAnchoring = ReAnchoring()

    /** The pages shown so far, for the time-left estimate; the first shown are forgotten first. */
    private val samples = object : LinkedHashMap<Int, List<ReadingUnit>>(INITIAL_SAMPLES, LOAD_FACTOR, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, List<ReadingUnit>>?) =
            size > config.timeLeft.samplePages
    }

    fun start() {
        if (started) return
        started = true
        scope.launch { player.state.collect(::onPlayback) }
        scope.launch { views.loadView()?.let(::applyView) }
        // Skip citation numbers changed (ADR-064): the sentences are built again, as the playback service does.
        scope.launch { textSettings.drop(1).collect { reopenForTextSettings() } }
        scope.launch {
            state.map { it.voiceToWarm }.distinctUntilChanged().collect { lang ->
                AudoraLog.d(TAG, "Voice to load ahead: ${lang?.name ?: "none"}, $logContext")
                voiceWarmUp.offer(lang)
            }
        }
        // What the warm-up waits on, for Share logs; collected apart so these changes never restart its wait.
        scope.launch {
            state.map { "player=${it.playback.connection} playing=${it.playback.wantsToPlay} ${it.pageForLog}" }
                .distinctUntilChanged()
                .collect { AudoraLog.d(TAG, "Warm-up waits on: $it, $logContext") }
        }
        scope.launch {
            state.map { it.playback.connection == PlaybackSnapshot.Connection.CONNECTED }.distinctUntilChanged()
                .filter { connected -> !connected }
                .collect { voiceWarmUp.rearm() }
        }
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

    /**
     * A tap on the page away from any sentence: in the outer edge of the page, it turns the page that way (not while
     * zoomed in, where the page's edge is off screen). True if it turned.
     */
    fun turnAtEdge(point: ViewPoint): Boolean {
        val way = if (zoomed) 0 else config.flip.edgeTurn(point.u)
        if (way != 0) showPage(state.value.page + way)
        return way != 0
    }

    /** Back to the page being read; the screen follows reading again. */
    fun showReadingPosition() {
        val position = state.value.readingPosition ?: return
        mutableState.update { it.copy(following = true) }
        loadPage(position.page)
    }

    /**
     * Tap on a sentence: read from there. A greyed one (skipped as unreadable, RI-9) would only play the cue, so the
     * screen first says why it is skipped and offers to read it anyway ([readUnreadable]).
     */
    fun readFrom(unit: ReadingUnit) {
        if (state.value.isUnreadable(unit)) {
            mutableState.update { it.copy(unreadableTapped = unit) }
            return
        }
        startReading(ReadingPosition(bookId, unit.page, unit.charStart), unit.index)
    }

    /** "Read it anyway" on a greyed sentence: turns "Skip unreadable text" off and reads from that sentence. */
    fun readUnreadable() {
        val unit = state.value.unreadableTapped ?: return
        mutableState.update { it.copy(unreadableTapped = null) }
        AudoraLog.i(TAG, "Skip unreadable turned off from a greyed sentence, $logContext page=${unit.page}")
        player.setReadingModes(state.value.playback.readingModes.copy(skipUnreadable = false))
        startReading(ReadingPosition(bookId, unit.page, unit.charStart), unit.index)
    }

    fun dismissUnreadable() = mutableState.update { it.copy(unreadableTapped = null) }

    /** Long-press on a sentence: offer "This sounded wrong" and its reasons (RI-8b). */
    fun askReport(unit: ReadingUnit) = mutableState.update { it.copy(reporting = unit) }

    /**
     * The listener picked [reason]: the report goes to the log, with where the sentence is but never its text, and the
     * fixes for it with the settings in use are offered ([SoundedWrongFixes]).
     */
    fun report(reason: SoundedWrongReason) {
        val unit = state.value.reporting ?: return
        player.reportSoundedWrong(SoundedWrongReport.of(bookId, unit, reason))
        val fixes = SoundedWrongFixes.offered(reason, state.value.playback)
        mutableState.update { it.copy(reporting = null, soundedWrong = SoundedWrongOffer(unit, reason, fixes)) }
    }

    /** A setting offered for a sentence that sounded wrong: changed at once, for the sentences read from now. */
    fun applySoundedWrongFix(fix: SoundedWrongFix.Setting) {
        val playback = state.value.playback
        val modes = playback.readingModes
        AudoraLog.i(TAG, "Sounded wrong fix ${fix.logName}, $logContext")
        when (fix) {
            SoundedWrongFix.SkipUnreadable -> setReadingModes(modes.copy(skipUnreadable = true))
            is SoundedWrongFix.Tables -> setReadingModes(modes.copy(tables = fix.mode))
            is SoundedWrongFix.Footnotes -> setReadingModes(modes.copy(footnotes = fix.mode))
            SoundedWrongFix.SkipBrackets -> setReadingModes(modes.copy(skipBrackets = true))
            is SoundedWrongFix.ClausePauses -> setClausePauses(fix.on)
            is SoundedWrongFix.SentencePause -> setPace(fix.pace)
        }
    }

    fun dismissSoundedWrong() = mutableState.update { it.copy(soundedWrong = null) }

    fun dismissReport() = mutableState.update { it.copy(reporting = null) }

    /**
     * "Share part of the page" on [unit] (ADR 2026-10-28-page-snippet): the page view shows a box around its lines,
     * or across the middle of a page whose lines have no positions, for the listener to drag.
     */
    fun startSnippet(unit: ReadingUnit) {
        if (state.value.view != ReaderView.PAGE) setView(ReaderView.PAGE)
        val current = state.value
        val image = current.shownPageImage
        val lines = image?.let { shown ->
            unit.bounds.orEmpty().map(shown.transform::toView)
                .filter { it.right > it.left && it.bottom > it.top }
                .map { SnippetBox(it.left, it.top, it.right, it.bottom) }
        }.orEmpty()
        val box = SnippetBox.around(lines) ?: SnippetBox.middle()
        mutableState.update { it.copy(snippet = box) }
    }

    /** The box dragged; ignored once the snippet is shared or cancelled. */
    fun moveSnippet(box: SnippetBox) = mutableState.update { if (it.snippet == null) it else it.copy(snippet = box) }

    fun endSnippet() = mutableState.update { it.copy(snippet = null) }

    /** The sentence under a tap on the page image at [point], if the tap is on (or right next to) one. */
    fun sentenceAt(point: ViewPoint): ReadingUnit? {
        val current = state.value
        val image = current.shownPageImage
        val units = (current.content as? PageContent.Ready)?.units
        if (image == null || units == null) return null
        return SentenceHitTest.unitAt(units, image.transform.toPage(point), config.pageTapSlopPoints)
    }

    /**
     * A bookmark (plan item 29), found again on the current pages the way resume is: reads from its sentence while
     * this book is being read, else shows its page.
     */
    fun goToBookmark(bookmark: Bookmark) = goToPlace(bookmark.anchor)

    /** Goes to a saved place (a bookmark, highlight or voice note), found again the way resume finds its sentence. */
    fun goToPlace(anchor: ResumeAnchor) {
        val opened = document ?: return
        scope.launch {
            val units = (opened.units(anchor.page) as? Outcome.Success)?.value.orEmpty()
            val point = reAnchoring.locate(anchor, pipelineVersion, units)
            AudoraLog.i(TAG, "Saved place, $logContext page=${anchor.page} match=${point.match}")
            if (state.value.isReading) {
                startReading(ReadingPosition(bookId, point.page, point.charStart))
            } else {
                showPage(point.page)
            }
        }
    }

    /**
     * A page's sentences for the search index (plan item 16), from the reader's own document so scanned pages come
     * from the OCR cache; null when the page cannot be read now (no document, OCR models missing or OCR failed).
     */
    suspend fun sentencesForSearch(page: Int): List<ReadingUnit>? {
        val opened = document ?: return null
        // Never OCR for search: a scanned page not read yet is left out (the index retries it next time).
        val read = (opened.pageIfReady(page) as? Outcome.Success)?.value
        return read?.units?.takeIf { read.origin == PageOrigin.TEXT || read.origin == PageOrigin.OCR }
    }

    /** What "Bookmark here" marks: the sentence being read on the shown page, else the page's first sentence. */
    fun sentenceHere(): ReadingUnit? = state.value.sentenceHere

    /** "Use for this book only" (plan item 4): the book keeps its own speed, pace and voices, or goes back to all. */
    fun setOwnSettings(on: Boolean) = player.setOwnSettings(on)

    fun setBookVoice(lang: Lang, voice: VoiceId) = player.setBookVoice(lang, voice)

    /** A row of the chapter list: reads from its page while this book is being read, else shows it. */
    fun goToChapter(chapter: ChapterEntry) {
        if (state.value.isReading) {
            startReading(ReadingPosition.startOfPage(bookId, chapter.page))
        } else {
            showPage(chapter.page)
        }
    }

    fun setView(view: ReaderView) {
        if (view == state.value.view) return
        applyView(view)
        scope.launch { views.saveView(view) }
    }

    /** The screen laid the page view out [widthPx] wide (again after a rotation): draw the page at that width. */
    fun setPageWidth(widthPx: Int) {
        if (widthPx <= 0 || widthPx == pageWidthPx) return
        pageWidthPx = widthPx
        renderPage(state.value.page)
    }

    /**
     * The page view was zoomed in (said before the zoomed width) or back to its width (said after it): pages are
     * drawn ahead only at the page view's own width. Back at it, the page on screen (if drawn already; else when it
     * is) is kept and its neighbours drawn ahead.
     */
    fun setZoomed(on: Boolean) {
        if (on == zoomed) return
        zoomed = on
        val open = document
        val width = pageWidthPx
        val current = state.value
        val shown = (current.pageImage as? PageImageState.Ready)?.takeIf { it.page == current.page }
        when {
            on -> {
                aheadJob?.cancel()
                dropNeighbourImages()
            }

            open != null && width != null && shown != null -> {
                neighbours.keepImage(shown.page, width, shown.image)
                drawAhead(open, shown.page, width)
            }
        }
    }

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

    /**
     * A book with its own settings keeps its speed itself (in the service); only the global one is saved here, and
     * only when [save] (a voice command's change is not: it may have been heard by mistake).
     */
    fun setSpeed(speed: Float, save: Boolean = true) {
        val offered = config.nearestSpeed(speed)
        player.setSpeed(offered)
        if (save && !state.value.playback.ownSettings) scope.launch { speeds.save(offered) }
    }

    /** Global for now: the service keeps it with the speech style, so it is not saved here (ADR-067). */
    fun setPitch(pitch: Float) = player.setPitch(config.speech.clampPitch(pitch))

    fun setPace(pace: Pace) = player.setPace(pace)

    fun setClausePauses(on: Boolean) = player.setClausePauses(on)

    fun setReadingModes(modes: ReadingModes) = player.setReadingModes(modes)

    fun setVoiceShaping(shaping: VoiceShaping) = player.setVoiceShaping(shaping)

    fun setSleepTimer(setting: SleepTimerSetting) = player.setSleepTimer(setting)

    fun setSleepOptions(options: SleepOptions) = player.setSleepOptions(options)

    fun setFocusSound(options: FocusSoundOptions) = player.setFocusSound(options)

    /** "Undo jump" (plan item 78); the screen follows reading back to where it was. */
    fun undoJump() {
        if (state.value.undoJumpTo == null) return
        mutableState.update { it.copy(following = true) }
        player.undoJump()
    }

    /**
     * "What did it say?": the service replays the sentence before the one playing a little slower
     * ([ReaderConfig.replayFactor]), then reads on at the listener's speed. The saved speed is never touched, so
     * leaving the reader mid-replay changes nothing.
     */
    fun replayPrevious() {
        if (!state.value.playerReady) return
        mutableState.update { it.copy(following = true) }
        player.replayPrevious(config.replayFactor)
    }

    /** Long-press on next / previous (the UI pass's full player): a paragraph on, or back; the screen follows. */
    fun skipParagraph(forward: Boolean) {
        if (!state.value.playerReady) return
        mutableState.update { it.copy(following = true) }
        player.skipParagraph(forward) {
            val refusal = if (forward) ParagraphSkipRefusal.NO_NEXT else ParagraphSkipRefusal.NO_PREVIOUS
            mutableState.update { it.copy(skipRefused = refusal) }
        }
    }

    fun skipRefusalShown() = mutableState.update { it.copy(skipRefused = null) }

    fun resumedShown() = mutableState.update { it.copy(resumedAt = null) }

    /** "Hear them again": reads from the recap's first sentence. */
    fun hearRecapAgain() {
        val recap = state.value.previouslyOn ?: return
        recapClosed()
        startReading(recap.from)
    }

    /** "Go on": reads on from where the book was left, unless it is already reading. */
    fun goOnFromRecap() {
        recapClosed()
        val position = savedPosition ?: return
        if (!state.value.isReading) startReading(position)
    }

    fun recapClosed() = mutableState.update { it.copy(previouslyOn = null) }

    fun openingLineShown() = mutableState.update { it.copy(firstOpen = false) }

    /** The skipped-pages notice's "Read from the start": page 1 now, and whenever this book opens unread. */
    fun readFromStart() {
        AudoraLog.i(TAG, "Read from the start, $logContext")
        mutableState.update { it.copy(skippedFrontMatter = null, startPage = 0) }
        showPage(0)
        scope.launch { bookStart.readFromStart(bookId) }
    }

    /** The skipped-pages notice's OK: the notice goes; the book stays where it opened. */
    fun frontMatterNoticeShown() = mutableState.update { it.copy(skippedFrontMatter = null) }

    /**
     * Study loops (plan item 31): marks A or B at the sentence being read in this book, turns looping on or off, or
     * clears the range. A mark needs this book to be the one being read.
     */
    fun study(action: StudyAction, loop: Boolean = state.value.study?.loop ?: true) {
        val marking = action == StudyAction.MARK_START || action == StudyAction.MARK_END
        if (marking && state.value.readingPosition == null) return
        player.study(action, loop)
    }

    /**
     * "Read a selection only" (plan item 31): reads [start] through [end] (the selection's last character) of this
     * book once, or over and over with [loop]. A user action, so it starts reading (C7).
     */
    fun readRange(start: ReadingPosition, end: ReadingPosition, loop: Boolean) {
        if (!state.value.playerReady || start.bookId != bookId || end.bookId != bookId) return
        mutableState.update { it.copy(following = true) }
        player.playRange(start, end, loop, password)
    }

    /**
     * A user action outside this screen asked to resume this book (the widget's Resume, plan item 35) while its reader
     * is already open: reading starts once the book is open and the player connected, exactly once, as a "Resume" in
     * the unexpected-stop prompt does (C7).
     */
    fun requestAutoResume() {
        AudoraLog.i(TAG, "Resume requested, $logContext")
        pendingAutoResume = true
        autoResumeIfDue()
    }

    /** Releases the PDF. Reading continues: the service has its own copy. */
    fun close() {
        voiceWarmUp.stop()
        openJob?.cancel()
        pageJob?.cancel()
        renderJob?.cancel()
        aheadJob?.cancel()
        neighbours.clear()
        document?.close()
        document = null
    }

    /** [unitIndex]: the tapped unit's, so a continued table's first row starts there, not at its summary. */
    private fun startReading(position: ReadingPosition, unitIndex: Int? = null) {
        if (!state.value.playerReady) {
            AudoraLog.w(TAG, "Start ignored, $logContext player not ready")
            return
        }
        mutableState.update { it.copy(following = true) }
        player.start(position, password, unitIndex)
        if (position.page != state.value.page) loadPage(position.page)
    }

    private fun reopenForTextSettings() {
        if (document == null) return
        AudoraLog.i(TAG, "Reading text settings changed, reopening, $logContext")
        keepPage = state.value.page
        samples.clear()
        open(password)
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
            mutableState.update { it.copy(title = found.title, author = found.author) }
            when (val opened = opener.open(found.documentUri, password, logContext)) {
                is Outcome.Success -> onOpened(opened.value, password)
                is Outcome.Failure -> mutableState.update { it.copy(document = documentStateFor(opened.error)) }
            }
        }
    }

    private suspend fun onOpened(opened: ReaderDocument, password: String?) {
        document?.close()
        document = opened
        neighbours.clear()
        this.password = password
        mutableState.update { it.copy(document = DocumentState.Open(opened.pageCount)) }
        if (!opened.hasPageImages) applyView(ReaderView.TEXT)
        val playing = state.value.readingPosition
        // Opened again for new text settings: the listener already knows where they are.
        val reopened = keepPage != null
        if (playing == null) {
            // The pages read to find the place, kept so its first words need no second read (or OCR) of the page.
            val read = HashMap<Int, List<ReadingUnit>>()
            savedPosition = resume.locate(bookId, opened.pageCount) { page ->
                (opened.units(page) as? Outcome.Success)?.value?.also { read[page] = it }
            }?.let { ReadingPosition(bookId, it.page, it.charStart) }
            savedPosition?.takeIf { !reopened }?.let { position -> sayWhereResumed(position, read[position.page]) }
        }
        val skipped = if (!reopened && playing == null && savedPosition == null) openUnread(opened) else null
        val startPage = keepPage?.also { keepPage = null } ?: playing?.page ?: savedPosition?.page ?: skipped?.page ?: 0
        loadPage(startPage.coerceIn(0, opened.pageCount - 1))
        loadChapters(opened)
        books.markOpened(bookId, opened.pageCount)
        autoResumeIfDue()
    }

    /**
     * A book with nothing read yet: its first open shows the opening line, and it opens past its contents, copyright
     * and preface pages and says so: never silently (ADR 2026-10-31-skip-front-matter). A page shown meanwhile (a
     * bookmark opened from Notes) wins over the search.
     */
    private suspend fun openUnread(opened: ReaderDocument): SkippedFrontMatter? {
        if (book?.lastOpenedAtMillis == null) mutableState.update { it.copy(firstOpen = true) }
        val shownBefore = state.value.page
        val found = bookStart.find(bookId, opened)?.takeIf { state.value.page == shownBefore } ?: return null
        AudoraLog.i(TAG, "Opening past front matter, $logContext $found")
        mutableState.update { it.copy(skippedFrontMatter = found, startPage = found.page) }
        return found
    }

    /** After days away, the recap ([previouslyOn]); otherwise the one-line "Picked up on page N". */
    private suspend fun sayWhereResumed(position: ReadingPosition, units: List<ReadingUnit>?) {
        val page = units.orEmpty()
        val recap = resume.savedAtMillis(bookId)?.let { previouslyOn(position, page, it, now()) }
        mutableState.update {
            if (recap != null) it.copy(previouslyOn = recap) else it.copy(resumedAt = resumedAt(position, page))
        }
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
        AudoraLog.i(TAG, "Auto-resumed, $logContext")
    }

    private fun onPlayback(snapshot: PlaybackSnapshot) {
        val previous = state.value.playback
        mutableState.update { it.copy(playback = snapshot) }
        // Once per connection, and only once the service has said what its style is: the speed shown before that is a
        // default, not the service's. A book with its own speed (plan item 4) already reads at it.
        val styleArrived = snapshot.connection == PlaybackSnapshot.Connection.CONNECTED && snapshot.styleKnown &&
            !(previous.connection == PlaybackSnapshot.Connection.CONNECTED && previous.styleKnown)
        if (styleArrived && !snapshot.ownSettings) applySavedSpeed(snapshot.speed)
        autoResumeIfDue()
        if (snapshot.errorCode != null && snapshot.errorCode != previous.errorCode) {
            AudoraLog.w(TAG, "Playback error, $logContext code=${snapshot.errorCode}")
        }
        val current = state.value
        val position = current.readingPosition ?: return
        val pageCount = current.pageCount ?: return
        if (current.following && position.page != current.page && position.page < pageCount) loadPage(position.page)
        refreshTimeLeft()
    }

    private fun loadChapters(opened: ReaderDocument) {
        scope.launch {
            val outline = opened.outline()
            val chapters = ChapterList.from(outline, opened.pageCount, config.chapterListDepth)
            val starts = ChapterStarts.from(outline, opened.pageCount, config.chapterListDepth)
            AudoraLog.i(TAG, "Chapter list, $logContext rows=${chapters.size} chapters=${starts.size}")
            mutableState.update { it.copy(chapters = chapters, chapterStarts = starts) }
            refreshTimeLeft()
        }
    }

    /** Recomputes the time left; cheap (a sum over the pages seen), so it runs on every position change. */
    private fun refreshTimeLeft() {
        val current = state.value
        val pageCount = current.pageCount ?: return
        val position = current.readingPosition ?: ReadingPosition.startOfPage(bookId, current.page)
        val style = current.playback.let { SpeechStyle(it.speed, it.pace, it.clausePauses, it.readingModes) }
        fun until(endPage: Int) = timeLeft.msUntil(position.page, position.charStart, endPage, samples, style)
        val chapterEnd = current.chapterStarts.takeIf { it.isNotEmpty() }
            ?.let { starts -> starts.firstOrNull { it > position.page } ?: pageCount }
        val estimate = until(pageCount)?.let { TimeLeftEstimate(it, chapterEnd?.let(::until)) }
        if (estimate != current.timeLeft) mutableState.update { it.copy(timeLeft = estimate) }
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
        // A page read ahead for a turn by hand shows at once (ADR 2026-10-28-page-flip).
        val ahead = neighbours.content(page)
        // A box picked on one page means nothing on another.
        mutableState.update { shown ->
            shown.copy(
                page = page,
                content = ahead ?: PageContent.Loading,
                snippet = shown.snippet.takeIf { page == shown.page },
            )
        }
        if (ahead != null) {
            samples[page] = ahead.units
            refreshTimeLeft()
            renderPage(page)
            return
        }
        pageJob = scope.launch {
            val content = when (val shown = open.page(page)) {
                is Outcome.Success -> shown.value.let { PageContent.Ready(it.units, it.origin, it.ocrConfidence) }

                is Outcome.Failure -> {
                    AudoraLog.w(TAG, "Page failed, $logContext page=$page error=${shown.error}")
                    PageContent.Failed
                }
            }
            if (content is PageContent.Ready) {
                samples[page] = content.units
                if (content.isFinal) neighbours.keepContent(page, content)
            }
            mutableState.update { if (it.page == page) it.copy(content = content) else it }
            refreshTimeLeft()
        }
        renderPage(page)
    }

    private fun applyView(view: ReaderView) {
        // An EPUB or text book has no page images (ADR-042): it always shows as text, the saved choice is kept.
        val shown = if (document?.hasPageImages == false) ReaderView.TEXT else view
        mutableState.update { it.copy(view = shown) }
        renderPage(state.value.page)
    }

    /**
     * Draws [page] for the page view; nothing in the text view, so its bitmap is not held there. A page drawn ahead at
     * this width shows at once, without a spinner; then the pages beside it are drawn ahead.
     */
    private fun renderPage(page: Int) {
        renderJob?.cancel()
        aheadJob?.cancel()
        val open = document
        val width = pageWidthPx
        if (open == null || width == null || state.value.view != ReaderView.PAGE) {
            dropNeighbourImages()
            mutableState.update { it.copy(pageImage = PageImageState.None) }
            return
        }
        neighbours.useWidth(width)
        neighbours.keepAround(page, open.pageCount)
        val ahead = neighbours.image(page, width)
        if (ahead != null) {
            mutableState.update { it.copy(pageImage = PageImageState.Ready(page, ahead)) }
            drawAhead(open, page, width)
            return
        }
        mutableState.update { it.copy(pageImage = PageImageState.Loading) }
        renderJob = scope.launch {
            val rendered = renderLock.withLock { open.render(page, width, config.pageMaxPixels) }
            val image = when (rendered) {
                is Outcome.Success -> PageImageState.Ready(page, rendered.value)

                is Outcome.Failure -> {
                    AudoraLog.w(TAG, "Page render failed, $logContext page=$page error=${rendered.error}")
                    PageImageState.Failed
                }
            }
            if (image is PageImageState.Ready && !zoomed) neighbours.keepImage(page, width, image.image)
            mutableState.update { if (it.page == page) it.copy(pageImage = image) else it }
            if (image is PageImageState.Ready) drawAhead(open, page, width)
        }
    }

    /**
     * Draws and reads the pages beside [page] in the background, one at a time behind [renderLock], so the shown
     * page and the scrubber always go first. Never at a zoomed width (each page would be several times bigger), and
     * never runs OCR for a page ahead: a scanned page not read yet still loads when turned to.
     */
    private fun drawAhead(open: ReaderDocument, page: Int, width: Int) {
        if (zoomed) return
        aheadJob = scope.launch {
            for (next in neighbours.wanted(page, open.pageCount)) {
                if (neighbours.image(next, width) == null) {
                    val rendered = renderLock.withLock { open.render(next, width, config.pageMaxPixels) }
                    (rendered as? Outcome.Success)?.let { neighbours.keepImage(next, width, it.value) }
                }
                if (neighbours.content(next) == null) {
                    val read = (open.pageIfReady(next) as? Outcome.Success)?.value
                    read?.let { PageContent.Ready(it.units, it.origin, it.ocrConfidence) }
                        ?.takeIf { it.isFinal }
                        ?.let { neighbours.keepContent(next, it) }
                }
                publishNeighbours()
            }
        }
    }

    private fun dropNeighbourImages() {
        neighbours.dropImages()
        publishNeighbours()
    }

    private fun publishNeighbours() = mutableState.update { it.copy(nearbyPages = neighbours.images()) }

    /**
     * [page] drawn [widthPx] wide for the page scrubber's thumbnails (plan item 17), through the page view's render
     * path and [renderLock], capped by [ReaderConfig.pageMaxPixels]. Never touches the page view's image. Null with no
     * open document, a width of 0 or less, or when the page can't be drawn (logged with book and page).
     */
    suspend fun thumbnail(page: Int, widthPx: Int): PageImage? {
        val open = document
        if (open == null || widthPx <= 0) return null
        return when (val rendered = renderLock.withLock { open.render(page, widthPx, config.pageMaxPixels) }) {
            is Outcome.Success -> rendered.value

            is Outcome.Failure -> {
                AudoraLog.w(TAG, "Thumbnail failed, $logContext page=$page error=${rendered.error}")
                null
            }
        }
    }

    private fun documentStateFor(error: PdfOpenError): DocumentState = when (error) {
        PdfOpenError.PasswordRequired -> DocumentState.NeedsPassword(wrongPassword = false)

        PdfOpenError.IncorrectPassword -> DocumentState.NeedsPassword(wrongPassword = true)

        is PdfOpenError.Damaged -> DocumentState.Failed(ReaderFailure.DAMAGED)

        is PdfOpenError.Unreadable -> DocumentState.Failed(
            if (error.cause is DrmLockedException) ReaderFailure.LOCKED else ReaderFailure.UNREADABLE,
        )

        is PdfOpenError.EngineUnavailable -> DocumentState.Failed(ReaderFailure.ENGINE_UNAVAILABLE)
    }

    private companion object {
        const val TAG = "Audora.Reader"
        const val INITIAL_SAMPLES = 16
        const val LOAD_FACTOR = 0.75f
    }
}
