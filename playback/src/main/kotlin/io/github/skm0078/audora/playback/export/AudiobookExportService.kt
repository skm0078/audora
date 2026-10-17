// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/export/AudiobookExportService.kt
// Purpose: Foreground service that saves one library book as audiobook files, with progress and Cancel (ADR-034).

package io.github.skm0078.audora.playback.export

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import dagger.hilt.android.AndroidEntryPoint
import io.github.skm0078.audora.core.config.AudiobookConfig
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.BookTitles
import io.github.skm0078.audora.core.library.Library
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.SpeechStyle
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.playback.R
import io.github.skm0078.audora.playback.book.LibraryBookSource
import io.github.skm0078.audora.playback.book.OpenedBook
import io.github.skm0078.audora.playback.reading.UnitReading
import io.github.skm0078.audora.playback.voice.EngineRenderer
import io.github.skm0078.audora.playback.voice.ReadingSetup
import io.github.skm0078.audora.playback.voice.SpeechStyleStore
import io.github.skm0078.audora.playback.voice.SpeechVoices
import io.github.skm0078.audora.playback.voice.VoiceEngines
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

/**
 * One export at a time, on its own thread (PDFium and the exporter are thread-confined). Cancel, Android 15's
 * 6-hour limit for this kind of job, and the service being destroyed all interrupt that thread; finished chapters
 * stay. A partial wake lock keeps the CPU rendering with the screen off, released when the job ends or after
 * [AudiobookConfig.maxExportMs] at most.
 *
 * Its voices are its own [VoiceEngines], separate from the playback service's, so listening and exporting can
 * overlap at the cost of a second copy of the voice model in memory. Files are read at the voice's own rate
 * (audiobook apps have their own speed control, and a faster model rate costs clarity, ADR-033) with the
 * listener's pace.
 */
@AndroidEntryPoint
class AudiobookExportService : Service() {
    @Inject
    lateinit var library: Library

    private val config = AudiobookConfig()
    private lateinit var notifications: ExportNotifications

    @Volatile
    private var worker: Thread? = null

    override fun onCreate() {
        super.onCreate() // Hilt injects fields here.
        notifications = ExportNotifications(this).apply { createChannel() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            AudiobookExport.ACTION_START -> start(intent.getStringExtra(AudiobookExport.EXTRA_BOOK)?.let(::BookId))
            AudiobookExport.ACTION_CANCEL -> cancel("cancelled by the user")
            else -> AudoraLog.w(TAG, "Unknown command ${intent?.action}")
        }
        if (worker == null) stopSelf(startId)
        // Never restarted by the system: an export starts only from a user action (C7).
        return START_NOT_STICKY
    }

    /** Android 15+: this kind of foreground job used up its daily 6 hours; stop now, keep finished chapters. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        cancel("Android's time limit for background jobs")
    }

    override fun onDestroy() {
        cancel("service destroyed")
        super.onDestroy()
    }

    private fun start(id: BookId?) {
        // Foreground first, always: Android requires it within seconds of startForegroundService.
        startForeground(
            ExportNotifications.RUNNING_ID,
            notifications.running(getString(R.string.export_untitled), chapter = null, chapterCount = 0, percent = 0),
            foregroundType(),
        )
        if (id == null) {
            AudoraLog.w(TAG, "Export request without a book")
            return
        }
        if (worker != null) {
            AudoraLog.w(TAG, "Export already running; book=${id.logLabel} not started")
            notifications.showFailed(getString(R.string.export_untitled), getString(R.string.export_busy))
            return
        }
        worker = Thread({ runExport(id) }, THREAD_NAME).apply { start() }
    }

    private fun cancel(why: String) {
        worker?.let { thread ->
            AudoraLog.i(TAG, "Stopping the export: $why")
            thread.interrupt()
        }
    }

    /** On the worker thread. */
    private fun runExport(id: BookId) {
        val wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            .apply { acquire(config.maxExportMs) }
        try {
            val books = LibraryBookSource(this, library)
            when (val opened = runBlocking { books.open(id, password = null) }) {
                is Outcome.Success -> opened.value.text.use { exportBook(opened.value) }

                is Outcome.Failure -> {
                    AudoraLog.w(TAG, "Export not started, book=${id.logLabel}: ${opened.error.code}")
                    notifications.showFailed(getString(R.string.export_untitled), opened.error.message)
                }
            }
        } catch (interrupted: InterruptedException) {
            AudoraLog.i(TAG, "Export interrupted before it started, book=${id.logLabel}")
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
            onWorkerDone()
        }
    }

    private fun exportBook(book: OpenedBook) {
        val title = book.book.title.takeUnless { it == BookTitles.UNTITLED } ?: getString(R.string.export_untitled)
        val logContext = "book=${book.book.id.logLabel}"
        val chapters = AudioChapters.plan(book.text.chapterStarts(), book.text.pageCount, config)
        val folder = AudiobookFolder.relativePath(title, getString(R.string.export_untitled), config)
        AudoraLog.i(TAG, "Export started, $logContext, pages=${book.text.pageCount}, chapters=${chapters.size}")
        val shownFolder = folder.trimEnd('/')
        val voices = SpeechVoices(VoiceEngines(this))
        try {
            val style = SpeechStyleStore.forContext(this)
            val chapterPages = book.text.chapterStarts().toSet()
            val renderer = EngineRenderer(
                voices,
                style = { style.current.copy(rate = SpeechStyle.NORMAL_RATE) },
                styleConfig = style.config,
                reading = ReadingSetup(chapterPages = { chapterPages }),
            )
            val sink = AacAudioSink(MediaStoreChapterFiles(contentResolver, folder), chapters.size, config)
            val listener = ProgressListener(title, chapters)
            val exporter =
                AudiobookExporter(renderer, config, UnitReading(style.current.modes, chapterPages = chapterPages))
            when (val result = exporter.export(book.text, chapters, sink, listener, logContext)) {
                is Outcome.Success -> notifications.showDone(title, result.value, shownFolder)
                is Outcome.Failure -> notifications.showFailed(title, result.error.message)
            }
        } catch (interrupted: InterruptedException) {
            AudoraLog.i(TAG, "Export cancelled, $logContext")
            notifications.showCancelled(title, shownFolder)
        } finally {
            voices.release()
        }
    }

    private fun onWorkerDone() {
        worker = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private inner class ProgressListener(private val title: String, chapters: List<AudioChapter>) : ExportListener {
        private val progress = ExportProgress(chapters, config.progressStepPercent)
        private val chapterCount = chapters.size

        override fun onPageDone(chapter: AudioChapter, page: Int) {
            val percent = progress.onPageDone() ?: return
            notifications.showRunning(notifications.running(title, chapter.number, chapterCount, percent))
        }
    }

    private companion object {
        const val TAG = "Export.Service"
        const val THREAD_NAME = "audiobook-export"
        const val WAKE_LOCK_TAG = "Audora:AudiobookExport"

        /** mediaProcessing is the type made for this (Android 15+); dataSync before it existed. */
        fun foregroundType(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }
    }
}
