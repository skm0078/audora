// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/export/AudiobookExportService.kt
// Purpose: Foreground service that saves a book as audiobook files, with progress, Cancel and Continue (ADR-034).

package io.github.skm0078.audora.playback.export

import android.app.NotificationManager
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
import io.github.skm0078.audora.core.notes.BookSettingsStore
import io.github.skm0078.audora.core.notes.PronunciationStore
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.playback.R
import io.github.skm0078.audora.playback.backup.Backups
import io.github.skm0078.audora.playback.book.LibraryBookSource
import io.github.skm0078.audora.playback.book.OpenedBook
import io.github.skm0078.audora.playback.jobs.LongJobKind
import io.github.skm0078.audora.playback.jobs.LongJobs
import io.github.skm0078.audora.playback.studio.BookRendering
import io.github.skm0078.audora.playback.voice.SpeechStyleStore
import io.github.skm0078.audora.playback.voice.VoiceEngines
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

/**
 * One export at a time, on its own thread (PDFium and the exporter are thread-confined). Cancel, Android 15's
 * 6-hour limit for this kind of job, and the service being destroyed all interrupt that thread; finished chapters
 * stay, and an [ExportSession] record lets the next export of the same book go on from them (Continue after Android's
 * limit, or saving again after Cancel). Saving a book again with other settings replaces its earlier files. A partial
 * wake lock keeps the CPU rendering with the screen off, renewed on every finished page and released when the job
 * ends, so a stuck export lets go within [AudiobookConfig.wakeLockMs].
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

    @Inject
    lateinit var bookSettings: BookSettingsStore

    @Inject
    lateinit var pronunciations: PronunciationStore

    private val config = AudiobookConfig()
    private lateinit var notifications: ExportNotifications

    /** This instance started the running job ([JobSlots.EXPORT]); a refused or finished start lets it stop. */
    @Volatile
    private var mine = false

    /** Set once Android's time limit stopped the service: progress must not re-post the removed notification. */
    @Volatile
    private var stopping = false

    /** Set when Android's time limit stopped the export, so it offers Continue instead of reporting a cancel. */
    @Volatile
    private var timedOut = false

    private var wakeLock: PowerManager.WakeLock? = null

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
        if (!mine) stopSelf(startId)
        // Never restarted by the system: an export starts only from a user action (C7).
        return START_NOT_STICKY
    }

    /** Android 15+: this kind of foreground job used up its daily 6 hours; stop now, keep finished chapters. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        timedOut = true
        stopping = true
        cancel("Android's time limit for background jobs")
        // Android throws if the service is still in the foreground a few seconds after this; the worker may be
        // inside a long native call, so stop now and let it post its notice on its own thread.
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        // Only this instance's own export: a refused start's instance must not cut off another's.
        if (mine) cancel("service destroyed")
        super.onDestroy()
    }

    private fun start(id: BookId?) {
        val untitled = getString(R.string.export_untitled)
        // Foreground first, always: Android requires it within seconds of startForegroundService.
        startForeground(
            ExportNotifications.RUNNING_ID,
            notifications.running(untitled, chapter = null, chapterCount = 0, percent = 0),
            foregroundType(),
        )
        // Taken first, whatever happens next, so a refused start never leaves the password behind.
        val password = id?.let(PasswordHandoff.shared::take)
        when {
            id == null -> AudoraLog.w(TAG, "Export request without a book")

            // The restart that applies a restore would cut the export off.
            Backups.restoreUnderWay ->
                notifications.showFailed(untitled, getString(R.string.long_job_restoring), publish = false)

            // This instance's export goes on: exporting another book is refused without stopping it.
            mine -> notifications.showFailed(untitled, getString(R.string.export_busy), publish = false)

            else -> {
                timedOut = false
                // Set first: the job may end (and clear it) before launch returns.
                mine = true
                LongJobs.begin(
                    LongJobKind.AUDIOBOOK,
                    id,
                    getString(R.string.export_running_title, untitled),
                    getString(R.string.export_preparing),
                )
                if (!JobSlots.EXPORT.launch(THREAD_NAME) { runExport(id, password) }) {
                    mine = false
                    AudoraLog.w(TAG, "Export already running; book=${id.logLabel} not started")
                    notifications.showFailed(untitled, getString(R.string.export_busy))
                }
            }
        }
    }

    private fun cancel(why: String) {
        if (JobSlots.EXPORT.busy) AudoraLog.i(TAG, "Stopping the export: $why")
        JobSlots.EXPORT.interrupt()
    }

    /** On the worker thread. */
    private fun runExport(id: BookId, password: String?) {
        val lock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            .apply {
                setReferenceCounted(false) // Each renewal replaces the timeout; one release lets go.
                acquire(config.wakeLockMs)
            }
        wakeLock = lock
        try {
            val books = LibraryBookSource(this, library)
            when (val opened = runBlocking { books.open(id, password) }) {
                is Outcome.Success -> opened.value.text.use { exportBook(opened.value) }

                is Outcome.Failure -> {
                    AudoraLog.w(TAG, "Export not started, book=${id.logLabel}: ${opened.error.code}")
                    notifications.showFailed(getString(R.string.export_untitled), opened.error.message)
                }
            }
        } catch (interrupted: InterruptedException) {
            AudoraLog.i(TAG, "Export interrupted before it started, timedOut=$timedOut, book=${id.logLabel}")
            if (timedOut) notifications.showTimedOut(id, getString(R.string.export_untitled))
        } finally {
            if (lock.isHeld) lock.release()
            wakeLock = null
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
        val factory = BookRendering.Factory(this, bookSettings, pronunciations)
        try {
            ExportVoice(this, SpeechStyleStore.forContext(this), factory, book.book.id, book.text).use { voice ->
                val pitch = voice.rendering.style.pitch
                val session = ExportSession(
                    ExportRecords(File(filesDir, RECORDS_FOLDER)),
                    MediaStoreSavedFiles(contentResolver),
                    bookId = book.book.id.value,
                    // The book's voices, fixes and pitch are in the key: changing any starts again, never mixes.
                    key = exportKey(withPitch(voice.rendering.key.signature.hash, pitch), folder, chapters),
                    chapters,
                    logContext,
                )
                // Each file is recorded as pending once created, so a crash mid-chapter never strands a hidden file.
                val files = MediaStoreChapterFiles(contentResolver, folder).recordedIn(session)
                val sink = AacAudioSink(files, chapters.size, config, pitch)
                val listener = ProgressListener(title, chapters, session)
                val exporter = voice.exporter(config) { cast ->
                    AudoraLog.i(TAG, "Export cast=$cast, $logContext")
                    listener.castChanged(exportCastNote(cast))
                }
                when (val result = exporter.export(book.text, session.remaining, sink, listener, logContext)) {
                    is Outcome.Success ->
                        notifications.showDone(title, session.finish(result.value), shownFolder, listener.note)

                    is Outcome.Failure -> notifications.showFailed(title, result.error.message)
                }
            }
        } catch (interrupted: InterruptedException) {
            AudoraLog.i(TAG, "Export stopped, timedOut=$timedOut, $logContext")
            if (timedOut) {
                notifications.showTimedOut(book.book.id, title)
            } else {
                notifications.showCancelled(title, shownFolder)
            }
        }
    }

    private fun onWorkerDone() {
        mine = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        // A late progress update may have re-posted it after the service left the foreground.
        getSystemService(NotificationManager::class.java)?.cancel(ExportNotifications.RUNNING_ID)
        stopSelf()
    }

    private inner class ProgressListener(
        private val title: String,
        chapters: List<AudioChapter>,
        private val session: ExportSession,
    ) : ExportListener {
        private val progress = ExportProgress(chapters, config.progressStepPercent).apply { skip(session.keptPages) }
        private val chapterCount = chapters.size
        private var shownChapter: Int? = null
        private var shownPercent = 0

        /** The character-voice note (plan item 63), shown under the progress from the moment it is known. */
        @Volatile
        var note: String? = null
            private set

        override fun onPageDone(chapter: AudioChapter, page: Int) {
            wakeLock?.acquire(config.wakeLockMs)
            val percent = progress.onPageDone() ?: return
            shownChapter = chapter.number
            shownPercent = percent
            show()
        }

        /** On the worker, before the page's progress: re-posts the progress at once rather than at the next step. */
        fun castChanged(note: String?) {
            if (note == this.note) return
            this.note = note
            show()
        }

        private fun show() {
            if (stopping) return
            notifications.showRunning(notifications.running(title, shownChapter, chapterCount, shownPercent, note))
        }

        override fun onChapterDone(chapter: ExportedChapter) = session.onChapterDone(chapter)
    }

    private companion object {
        const val TAG = "Export.Service"
        const val THREAD_NAME = "audiobook-export"
        const val WAKE_LOCK_TAG = "Audora:AudiobookExport"
        const val RECORDS_FOLDER = "audiobook-exports"

        /** mediaProcessing is the type made for this (Android 15+); dataSync before it existed. */
        fun foregroundType(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }
    }
}
