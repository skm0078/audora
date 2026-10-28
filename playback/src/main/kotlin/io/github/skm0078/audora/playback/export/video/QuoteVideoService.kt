// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/export/video/QuoteVideoService.kt
// Purpose: Short foreground service that reads a card's quote in the book's voice, writes it as a video over the
//          card and offers it to share (ADR 2026-10-28-quote-video).

package io.github.skm0078.audora.playback.export.video

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import io.github.skm0078.audora.core.config.ClipConfig
import io.github.skm0078.audora.core.config.QuoteVideoConfig
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookTitles
import io.github.skm0078.audora.core.library.Library
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.notes.BookSettingsStore
import io.github.skm0078.audora.core.notes.PronunciationStore
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.share.CardQuote
import io.github.skm0078.audora.core.share.VideoTimeline
import io.github.skm0078.audora.playback.R
import io.github.skm0078.audora.playback.backup.Backups
import io.github.skm0078.audora.playback.export.JobSlots
import io.github.skm0078.audora.playback.export.ShareSheet
import io.github.skm0078.audora.playback.jobs.ForegroundJobs
import io.github.skm0078.audora.playback.jobs.jobWakeLock
import io.github.skm0078.audora.playback.jobs.leaveForeground
import io.github.skm0078.audora.playback.jobs.releaseIfHeld
import io.github.skm0078.audora.playback.studio.BookRendering
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

/**
 * One video at a time, on its own thread; a request while one is made is dropped (logged). The quote is read by
 * [QuoteVoice], drawn by [QuoteVideoPainter] over the backdrop the card screen wrote ([QuoteVideos.backdropFile]) and
 * encoded by [QuoteVideoWriter] into `cacheDir/shared-videos/`, then handed to the share sheet ([ShareSheet]) through
 * the clip FileProvider with a one-off read grant. The [ClipConfig.keptVideos] newest stay for an app that reads one
 * late.
 */
@AndroidEntryPoint
class QuoteVideoService : Service() {
    @Inject
    lateinit var library: Library

    @Inject
    lateinit var bookSettings: BookSettingsStore

    @Inject
    lateinit var pronunciations: PronunciationStore

    private val clipConfig = ClipConfig()
    private lateinit var notices: VideoNotices

    /** This instance started the running job ([JobSlots.VIDEO]); a refused or finished start lets it stop. */
    @Volatile
    private var mine = false

    override fun onCreate() {
        super.onCreate() // Hilt injects fields here.
        notices = VideoNotices(this).apply { createChannel() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(VideoNotices.RUNNING_ID, notices.making(), ForegroundJobs.type())
        val request = QuoteVideos.requestOf(intent)
        when {
            request == null -> AudoraLog.w(TAG, "Bad video request ${intent?.action}")

            // The restart that applies a restore would cut the video off.
            Backups.restoreUnderWay -> notices.showFailed(getString(R.string.long_job_restoring))

            mine -> AudoraLog.w(TAG, "A video is already being made here; book=${request.book.logLabel} dropped")

            else -> {
                // Set first: the job may end (and clear it) before launch returns.
                mine = true
                if (!JobSlots.VIDEO.launch(THREAD_NAME) { makeVideo(request) }) {
                    mine = false
                    AudoraLog.w(TAG, "A video is already being made; book=${request.book.logLabel} dropped")
                }
            }
        }
        if (!mine) stopSelf(startId)
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        AudoraLog.w(TAG, "Android's time limit for long jobs reached; stopping the video")
        JobSlots.VIDEO.interrupt()
        leaveForeground()
    }

    override fun onDestroy() {
        if (mine) JobSlots.VIDEO.interrupt()
        super.onDestroy()
    }

    /** On the worker thread. */
    private fun makeVideo(request: QuoteVideoRequest) {
        val wakeLock = jobWakeLock(WAKE_LOCK_TAG)
            .apply { acquire(clipConfig.wakeLockMs) }
        try {
            val book = runBlocking { library.book(request.book) }
            if (book == null) notices.showFailed("the book is gone") else videoOf(book, request)
        } catch (interrupted: InterruptedException) {
            AudoraLog.i(TAG, "Video cancelled, book=${request.book.logLabel}")
        } finally {
            wakeLock.releaseIfHeld()
            mine = false
            leaveForeground()
        }
    }

    private fun videoOf(book: Book, request: QuoteVideoRequest) {
        val config = if (request.sharp) QuoteVideoConfig.sharp() else QuoteVideoConfig()
        val backdrop = VideoFiles.backdropAt(QuoteVideos.backdropFile(this), config)
            ?: return notices.showFailed("no backdrop from the card screen")
        try {
            val factory = BookRendering.Factory(this, bookSettings, pronunciations)
            when (val spoken = QuoteVoice(this, factory, config).speak(book, request.quote, request.page)) {
                is Outcome.Failure -> notices.showFailed(spoken.error)
                is Outcome.Success -> write(book, request, config, backdrop, spoken.value)
            }
        } finally {
            backdrop.recycle()
        }
    }

    private fun write(
        book: Book,
        request: QuoteVideoRequest,
        config: QuoteVideoConfig,
        backdrop: Bitmap,
        spoken: SpokenQuote,
    ) {
        val timeline = VideoTimeline(spoken.audio.durationMs, spoken.wordStartsMs, config)
        val text = QuoteVideoText(
            request.quote,
            QuoteVideoFonts.of(this, CardQuote.scriptOf(request.quote)),
            request.ink,
            request.accent,
        )
        val painter = QuoteVideoPainter(text, config.widthPx, config.heightPx)
        val folder = File(cacheDir, QuoteVideos.FOLDER).apply { mkdirs() }
        val target = File(folder, "listenlock-${System.currentTimeMillis()}.mp4")
        var shown = -1
        val writer = QuoteVideoWriter(config) { done, total ->
            val percent = done * FULL / total
            // Every few percent: a notification per frame would be throttled by the system anyway.
            if (percent >= shown + PROGRESS_STEP) {
                shown = percent
                notices.showProgress(percent)
            }
        }
        val label = AiVoiceLabel(getString(R.string.video_ai_label))
        when (
            val written = writer.write(
                target,
                spoken.audio,
                timeline,
                QuoteVideoCard(backdrop, painter, timeline),
                label,
            )
        ) {
            is Outcome.Failure -> notices.showFailed(written.error.detail)

            is Outcome.Success -> {
                VideoFiles.prune(folder, keep = target, kept = clipConfig.keptVideos)
                share(book, request, written.value.file)
            }
        }
    }

    private fun share(book: Book, request: QuoteVideoRequest, file: File) {
        val title = book.title.takeUnless { it == BookTitles.UNTITLED } ?: getString(R.string.export_untitled)
        val text = getString(R.string.video_share_text, request.quote, title)
        val chooser = ShareSheet.chooserFor(this, file, MIME_TYPE, text, getString(R.string.video_chooser_title))
        AudoraLog.i(TAG, "Video ready, book=${book.id.logLabel} bytes=${file.length()}")
        notices.showReady(chooser)
        ShareSheet.open(this, chooser, TAG)
    }

    private companion object {
        const val TAG = "Video.Service"
        const val THREAD_NAME = "quote-video"
        const val WAKE_LOCK_TAG = "Audora:QuoteVideo"
        const val MIME_TYPE = "video/mp4"
        const val FULL = 100
        const val PROGRESS_STEP = 5
    }
}
