// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/epub/BookCopyService.kt
// Purpose: Foreground service that saves a book's accessible copy or talking book, with progress and Cancel.

package io.github.skm0078.audora.playback.epub

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import dagger.hilt.android.AndroidEntryPoint
import io.github.skm0078.audora.core.config.EpubConfig
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
import io.github.skm0078.audora.playback.export.JobSlots
import io.github.skm0078.audora.playback.studio.BookRendering
import io.github.skm0078.audora.playback.studio.StudioMode
import io.github.skm0078.audora.playback.voice.SpeechVoices
import io.github.skm0078.audora.playback.voice.VoiceEngines
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

/**
 * One copy at a time, on its own thread (PDFium is thread-confined). Cancel, Android 15's 6-hour limit for this kind
 * of job and the service being destroyed all interrupt that thread. Cancel deletes a talking book's staged chapters;
 * the time limit keeps them and its notification offers Continue, which opens Audora (that resets Android's budget)
 * and starts the copy again from the missing chapters. A partial wake lock, renewed at every step, keeps OCR and
 * the voice working with the screen off. The talking book uses its own voices, like the audiobook export (a second
 * copy of the voice model in memory while playback also runs).
 */
@AndroidEntryPoint
class BookCopyService : Service() {
    @Inject
    lateinit var library: Library

    @Inject
    lateinit var bookSettings: BookSettingsStore

    @Inject
    lateinit var pronunciations: PronunciationStore

    private val config = EpubConfig()
    private lateinit var notifications: CopyNotifications

    /** This instance started the running job ([JobSlots.COPY]); a refused or finished start lets it stop. */
    @Volatile
    private var mine = false

    /** Set once Android's time limit stopped the service: progress must not re-post the removed notification. */
    @Volatile
    private var stopping = false

    /** Set before the time limit interrupts the worker, so the stop is reported as that, not as Cancel. */
    @Volatile
    private var timedOut = false

    /** The running notification last shown, so a second request while busy re-posts it. */
    @Volatile
    private var lastRunning: Notification? = null

    override fun onCreate() {
        super.onCreate() // Hilt injects fields here.
        notifications = CopyNotifications(this).apply { createChannel() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            BookCopies.ACTION_START -> start(
                intent.getStringExtra(BookCopies.EXTRA_BOOK)?.let(::BookId),
                intent.getStringExtra(BookCopies.EXTRA_KIND)?.let { name -> CopyKind.entries.find { it.name == name } },
            )

            BookCopies.ACTION_CANCEL -> JobSlots.COPY.interrupt()

            else -> AudoraLog.w(TAG, "Unknown command ${intent?.action}")
        }
        if (!mine) stopSelf(startId)
        // Never restarted by the system: a copy starts only from a user action (C7).
        return START_NOT_STICKY
    }

    /** Android 15+: this kind of foreground job used up its daily 6 hours. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        AudoraLog.w(TAG, "Android's time limit for long jobs reached; stopping the copy")
        timedOut = true
        stopping = true
        JobSlots.COPY.interrupt()
        // Android throws if the service is still in the foreground a few seconds after this; the worker may be
        // inside a long native call, so stop now and let it finish its notice on its own thread.
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        JobSlots.COPY.interrupt()
        super.onDestroy()
    }

    private fun start(id: BookId?, kind: CopyKind?) {
        // Foreground first, always: Android requires it within seconds of startForegroundService.
        val preparing = getString(R.string.copy_preparing)
        val notice = lastRunning?.takeIf { mine } ?: notifications.running(preparing, preparing, percent = null)
        startForeground(CopyNotifications.RUNNING_ID, notice, foregroundType())
        when {
            id == null || kind == null -> AudoraLog.w(TAG, "Copy request without a book or kind")

            // The restart that applies a restore would cut the copy off.
            Backups.restoreUnderWay -> notifications.showResult(preparing, getString(R.string.long_job_restoring))

            else -> {
                timedOut = false
                // Set first: the job may end (and clear it) before launch returns.
                mine = true
                if (!JobSlots.COPY.launch(THREAD_NAME) { runCopy(id, kind) }) {
                    mine = false
                    notifications.showResult(preparing, getString(R.string.copy_busy))
                }
            }
        }
    }

    /** On the worker thread. */
    private fun runCopy(id: BookId, kind: CopyKind) {
        val wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            .apply {
                setReferenceCounted(false) // Each renewal replaces the last timeout instead of stacking holds.
                acquire(config.wakeLockMs)
            }
        val keepAwake = { wakeLock.acquire(config.wakeLockMs) }
        try {
            val books = LibraryBookSource(this, library)
            when (val opened = runBlocking { books.open(id, password = null) }) {
                is Outcome.Success -> opened.value.text.use { copy(opened.value, kind, keepAwake) }
                is Outcome.Failure -> notifications.showFailed(CopyError.NotOpened(opened.error.message))
            }
        } catch (interrupted: InterruptedException) {
            if (timedOut) {
                AudoraLog.i(TAG, "Copy stopped by the time limit, book=${id.logLabel}; staged chapters kept")
                notifications.showTimedOut(id, kind)
            } else {
                AudoraLog.i(TAG, "Copy cancelled, book=${id.logLabel}")
                CopyJob.dropStaging(this)
                notifications.showCancelled()
            }
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
            lastRunning = null
            mine = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            // A late progress update may have re-posted it after the service left the foreground.
            getSystemService(NotificationManager::class.java)?.cancel(CopyNotifications.RUNNING_ID)
            stopSelf()
        }
    }

    private fun copy(book: OpenedBook, kind: CopyKind, keepAwake: () -> Unit) {
        val title = book.book.title.takeUnless { it == BookTitles.UNTITLED } ?: getString(R.string.export_untitled)
        val running = if (kind == CopyKind.ACCESSIBLE) R.string.copy_running else R.string.copy_talking_running
        val heading = getString(running, title)
        AudoraLog.i(TAG, "Copy started, book=${book.book.id.logLabel} kind=$kind pages=${book.text.pageCount}")
        val job = CopyJob(this, book, title, config) { step, percent ->
            keepAwake()
            val running = notifications.running(heading, step, percent)
            lastRunning = running
            if (!stopping) notifications.showRunning(running)
        }
        val result = when (kind) {
            CopyKind.ACCESSIBLE -> job.accessibleCopy()
            CopyKind.TALKING_BOOK -> talkingBook(book, job, keepAwake)
        }
        when (result) {
            is Outcome.Success -> {
                AudoraLog.i(TAG, "Copy saved, book=${book.book.id.logLabel} kind=$kind")
                notifications.showDone(title, result.value, config.folder)
            }

            is Outcome.Failure -> {
                AudoraLog.w(TAG, "Copy failed, book=${book.book.id.logLabel} kind=$kind: ${result.error}")
                notifications.showFailed(result.error)
            }
        }
    }

    private fun talkingBook(book: OpenedBook, job: CopyJob, keepAwake: () -> Unit): Outcome<SavedCopy, CopyError> {
        val voices = SpeechVoices(VoiceEngines(this))
        try {
            val factory = BookRendering.Factory(this, bookSettings, pronunciations)
            val rendering = runBlocking { factory.prepare(book.book.id, book.text, voices, normalRate = true) }
            return job.talkingBook(rendering, StudioMode.store(this), onChapter = keepAwake)
        } finally {
            voices.release()
        }
    }

    private companion object {
        const val TAG = "Copy.Service"
        const val THREAD_NAME = "book-copy"
        const val WAKE_LOCK_TAG = "Audora:BookCopy"

        /** mediaProcessing is the type made for this (Android 15+); dataSync before it existed. */
        fun foregroundType(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }
    }
}
