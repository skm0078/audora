// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/studio/StudioService.kt
// Purpose: Foreground service that renders one book ahead while the phone charges (plan item 6, ADR-039).

package io.github.skm0078.audora.playback.studio

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import dagger.hilt.android.AndroidEntryPoint
import io.github.skm0078.audora.core.config.StudioConfig
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.BookTitles
import io.github.skm0078.audora.core.library.Library
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.notes.BookSettingsStore
import io.github.skm0078.audora.core.notes.PronunciationStore
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.resume.ResumeAnchorStore
import io.github.skm0078.audora.pdf.document.PdfPageError
import io.github.skm0078.audora.playback.R
import io.github.skm0078.audora.playback.backup.Backups
import io.github.skm0078.audora.playback.book.BookText
import io.github.skm0078.audora.playback.book.LibraryBookSource
import io.github.skm0078.audora.playback.book.OpenedBook
import io.github.skm0078.audora.playback.export.JobSlots
import io.github.skm0078.audora.playback.jobs.LongJobKind
import io.github.skm0078.audora.playback.jobs.LongJobs
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * One book at a time, on its own thread (PDFium and the renderer are thread-confined). Synthesis outside the playback
 * service is allowed for user-started foreground jobs since ADR-034; studio mode is one (ADR-039), never WorkManager.
 *
 * It renders exactly what playback would: the book's own speed, pace and voices over the listener's style, its
 * pronunciation fixes, the reading modes and chapter cues, so playback finds the audio under the same
 * [StudioSignature]. It waits (wake lock released) while the phone is not charging, unless the listener taps
 * "Start now" on the notification, and pauses while any audio plays, so listening never stutters.
 *
 * Stop, Android 15's 6-hour limit for this kind of job and the service being destroyed all interrupt the thread;
 * pages already rendered stay and are used.
 */
@AndroidEntryPoint
class StudioService : Service() {
    @Inject
    lateinit var library: Library

    @Inject
    lateinit var resumeAnchors: ResumeAnchorStore

    @Inject
    lateinit var bookSettings: BookSettingsStore

    @Inject
    lateinit var pronunciations: PronunciationStore

    private val config = StudioConfig()
    private lateinit var notifications: StudioNotifications

    /** This instance started the running job ([JobSlots.STUDIO]); a refused or finished start lets it stop. */
    @Volatile
    private var mine = false

    /** Set once Android's time limit stopped the service: progress must not re-post the removed notification. */
    @Volatile
    private var stopping = false

    @Volatile
    private var gate: ChargingGate? = null

    override fun onCreate() {
        super.onCreate() // Hilt injects fields here.
        notifications = StudioNotifications(this, config).apply { createChannel() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            StudioMode.ACTION_START -> start(intent.getStringExtra(StudioMode.EXTRA_BOOK)?.let(::BookId))
            StudioMode.ACTION_STOP -> stop("stopped by the user")
            StudioMode.ACTION_ON_BATTERY -> gate?.allowBattery()
            else -> AudoraLog.w(TAG, "Unknown command ${intent?.action}")
        }
        if (!mine) stopSelf(startId)
        // Never restarted by the system: studio mode starts only from a user action (C7).
        return START_NOT_STICKY
    }

    /** Android 15+: this kind of foreground job used up its daily 6 hours; stop now, keep what is rendered. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopping = true
        stop("Android's time limit for background jobs")
        // Android throws if the service is still in the foreground a few seconds after this; the worker may be
        // inside a long native call, so stop now and let it finish its notice on its own thread.
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        // Only this instance's own run: a refused start's instance must not cut off another's.
        if (mine) stop("service destroyed")
        super.onDestroy()
    }

    private fun start(id: BookId?) {
        // Foreground first, always: Android requires it within seconds of startForegroundService. While a run is going
        // its own notification is re-posted, so a second tap does not drop "Start now".
        val shown = notifications.lastRunning?.takeIf { mine } ?: notifications.preparing()
        startForeground(StudioNotifications.RUNNING_ID, shown, foregroundType())
        val untitled = getString(R.string.studio_untitled)
        when {
            id == null -> AudoraLog.w(TAG, "Studio request without a book")

            // The restart that applies a restore would cut the run off.
            Backups.restoreUnderWay ->
                notifications.showFailed(untitled, getString(R.string.long_job_restoring), publish = false)

            // This instance's run goes on (a second tap, or Start now's start): never refuse it into a stop.
            mine -> AudoraLog.i(TAG, "Studio mode already running here; book=${id.logLabel} not started")

            else -> {
                // Set first: the job may end (and clear it) before launch returns.
                mine = true
                LongJobs.begin(
                    LongJobKind.STUDIO,
                    id,
                    getString(R.string.studio_preparing_title),
                    getString(R.string.studio_preparing),
                )
                if (!JobSlots.STUDIO.launch(THREAD_NAME) { runStudio(id) }) {
                    mine = false
                    AudoraLog.w(TAG, "Studio mode already running; book=${id.logLabel} not started")
                    notifications.showFailed(untitled, getString(R.string.studio_busy))
                }
            }
        }
    }

    private fun stop(why: String) {
        if (JobSlots.STUDIO.busy) AudoraLog.i(TAG, "Stopping studio mode: $why")
        JobSlots.STUDIO.interrupt()
    }

    /** On the worker thread. */
    private fun runStudio(id: BookId) {
        val wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            .apply { setReferenceCounted(false) } // The gate's receiver and the worker both take it (ChargingGate).
        try {
            val books = LibraryBookSource(this, library)
            when (val opened = runBlocking { books.open(id, password = null) }) {
                is Outcome.Success -> opened.value.text.use { renderBook(opened.value, wakeLock) }

                is Outcome.Failure -> {
                    AudoraLog.w(TAG, "Studio mode not started, book=${id.logLabel}: ${opened.error.code}")
                    notifications.showFailed(getString(R.string.studio_untitled), opened.error.message)
                }
            }
        } catch (interrupted: InterruptedException) {
            AudoraLog.i(TAG, "Studio mode interrupted before it started, book=${id.logLabel}")
        } finally {
            gate?.close()
            gate = null
            if (wakeLock.isHeld) wakeLock.release()
            // The worker is done: the service goes with it.
            mine = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            // A late progress update may have re-posted it after the service left the foreground.
            getSystemService(NotificationManager::class.java)?.cancel(StudioNotifications.RUNNING_ID)
            stopSelf()
        }
    }

    private fun renderBook(book: OpenedBook, wakeLock: PowerManager.WakeLock) {
        val id = book.book.id
        val title = book.book.title.takeUnless { it == BookTitles.UNTITLED } ?: getString(R.string.studio_untitled)
        try {
            val factory = BookRendering.Factory(this, bookSettings, pronunciations)
            BookVoice(this, factory, id, book.text, RunSettings(acted = true)).use { voice ->
                render(id, title, book.text, voice, wakeLock)
            }
        } catch (interrupted: InterruptedException) {
            AudoraLog.i(TAG, "Studio mode stopped, book=${id.logLabel}")
            notifications.showStopped(title)
        }
    }

    private fun render(id: BookId, title: String, text: BookText, voice: BookVoice, wakeLock: PowerManager.WakeLock) {
        val rendering = voice.rendering
        val key = rendering.key
        val store = StudioMode.store(this)
        store.dropBooksNotIn(runBlocking { library.books().first() }.mapTo(HashSet()) { it.id })
        val fromPage = runBlocking { resumeAnchors.get(id) }?.page ?: 0
        val progress = Progress(title, StudioProgress(0L, 0L, fromPage, fromPage, text.pageCount, 0L, 0L))
        // At once, not at the first finished sentence: the card says which page it starts on and that it measures.
        progress.show()
        val chargingGate = ChargingGate(this, config, wakeLock, progress::onWaiting).also { gate = it }
        val run = StudioRun(
            voice = rendering.studioVoice(),
            store = store,
            gate = chargingGate,
            config = config,
            freeBytes = { filesDir.usableSpace },
            listener = progress,
        )
        AudoraLog.i(TAG, "Studio started, book=${id.logLabel}, from page=$fromPage, key=${key.signature.hash}")
        when (val result = run.run(key, PagePlays(text, voice), fromPage)) {
            is Outcome.Success -> notifications.showDone(title, result.value)
            is Outcome.Failure -> notifications.showFailed(title, result.error.message)
        }
    }

    /**
     * The units playback plays on each page, with the same failure rules. StudioRun asks once per page, in order, right
     * before rendering it, so each page goes to the quote and speaker tracking first, as playback's PageLoader does.
     */
    private class PagePlays(private val text: BookText, private val voice: BookVoice) : StudioBook {
        override val pageCount: Int get() = text.pageCount

        override fun plays(page: Int): StudioPagePlays = when (val read = text.reading(page)) {
            is Outcome.Success -> {
                voice.page(read.value)
                StudioPagePlays.Units(voice.rendering.reading.plays(read.value.units))
            }

            is Outcome.Failure -> when (read.error) {
                is PdfPageError.ExtractionFailed, is PdfPageError.PageOutOfRange -> StudioPagePlays.Skipped
                is PdfPageError.ReopenFailed, PdfPageError.BookClosed -> StudioPagePlays.BookUnreadable
            }
        }
    }

    /** On the worker thread: the gate and the run both call in from it. */
    private inner class Progress(private val title: String, private var latest: StudioProgress) : StudioListener {
        private var waiting: ChargingGate.Wait? = null
        private var shownPage = -1
        private var shownAt = 0L

        /** Every sentence; shown when the page changes or [StudioConfig.progressEveryMs] passed. */
        override fun onProgress(progress: StudioProgress) {
            latest = progress
            val due = SystemClock.elapsedRealtime() - shownAt >= config.progressEveryMs
            if (progress.page != shownPage || due) show()
        }

        fun onWaiting(wait: ChargingGate.Wait?) {
            waiting = wait
            show()
        }

        fun show() {
            shownPage = latest.page
            shownAt = SystemClock.elapsedRealtime()
            if (!stopping) notifications.showRunning(title, latest, waiting)
        }
    }

    private companion object {
        const val TAG = "Studio.Service"
        const val THREAD_NAME = "studio-mode"

        const val WAKE_LOCK_TAG = "Audora:StudioMode"

        /** mediaProcessing is the type made for this (Android 15+); dataSync before it existed. */
        fun foregroundType(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }
    }
}
