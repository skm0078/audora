// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/export/video/QuoteVideoService.kt
// Purpose: Short foreground service that reads a card's quote in the book's voice, writes it as a video over the
//          card and offers it to share (ADR 2026-10-28-quote-video).

package io.github.skm0078.audora.playback.export.video

import android.app.Service
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
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
import io.github.skm0078.audora.playback.export.AudioClips
import io.github.skm0078.audora.playback.export.JobSlots
import io.github.skm0078.audora.playback.studio.BookRendering
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

/**
 * One video at a time, on its own thread; a request while one is made is dropped (logged). The quote is read by
 * [QuoteVoice], drawn by [QuoteVideoPainter] over the backdrop the card screen wrote ([QuoteVideos.backdropFile]) and
 * encoded by [QuoteVideoWriter] into `cacheDir/shared-videos/`, then handed to the share sheet through the clip
 * FileProvider with a one-off read grant. The [ClipConfig.keptVideos] newest stay for an app that reads one late.
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
        startForeground(VideoNotices.RUNNING_ID, notices.making(), foregroundType())
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
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (mine) JobSlots.VIDEO.interrupt()
        super.onDestroy()
    }

    /** On the worker thread. */
    private fun makeVideo(request: QuoteVideoRequest) {
        val wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            .apply { acquire(clipConfig.wakeLockMs) }
        try {
            val book = runBlocking { library.book(request.book) }
            if (book == null) notices.showFailed("the book is gone") else videoOf(book, request)
        } catch (interrupted: InterruptedException) {
            AudoraLog.i(TAG, "Video cancelled, book=${request.book.logLabel}")
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
            mine = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
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
        val uri = AudioClips.uriFor(this, file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, getString(R.string.video_share_text, request.quote, title))
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, getString(R.string.video_chooser_title))
        AudoraLog.i(TAG, "Video ready, book=${book.id.logLabel} bytes=${file.length()}")
        notices.showReady(chooser)
        try {
            // Opens at once while ListenLock is on screen; from the background Android blocks it, and the notice is
            // there.
            startActivity(Intent(chooser).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            AudoraLog.w(TAG, "No app to share the video with", e)
        }
    }

    private companion object {
        const val TAG = "Video.Service"
        const val THREAD_NAME = "quote-video"
        const val WAKE_LOCK_TAG = "Audora:QuoteVideo"
        const val MIME_TYPE = "video/mp4"
        const val FULL = 100
        const val PROGRESS_STEP = 5

        fun foregroundType(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }
    }
}
