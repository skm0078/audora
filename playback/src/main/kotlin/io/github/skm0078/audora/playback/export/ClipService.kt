// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/export/ClipService.kt
// Purpose: Short foreground service that renders a passage into an .m4a clip and offers it to share (plan item 41).

package io.github.skm0078.audora.playback.export

import android.app.Service
import android.content.Intent
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import io.github.skm0078.audora.core.config.AudiobookConfig
import io.github.skm0078.audora.core.config.ClipConfig
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.BookTitles
import io.github.skm0078.audora.core.library.Library
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.notes.BookSettingsStore
import io.github.skm0078.audora.core.notes.PronunciationStore
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.getOrNull
import io.github.skm0078.audora.pdf.document.PdfPageError
import io.github.skm0078.audora.playback.R
import io.github.skm0078.audora.playback.backup.Backups
import io.github.skm0078.audora.playback.book.LibraryBookSource
import io.github.skm0078.audora.playback.book.OpenedBook
import io.github.skm0078.audora.playback.jobs.ForegroundJobs
import io.github.skm0078.audora.playback.jobs.jobWakeLock
import io.github.skm0078.audora.playback.jobs.leaveForeground
import io.github.skm0078.audora.playback.jobs.releaseIfHeld
import io.github.skm0078.audora.playback.queue.UnitKey
import io.github.skm0078.audora.playback.studio.BookRendering
import io.github.skm0078.audora.playback.studio.BookVoice
import io.github.skm0078.audora.playback.studio.RunSettings
import io.github.skm0078.audora.playback.studio.StudioMode
import io.github.skm0078.audora.playback.studio.StudioSentenceRenderer
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

/**
 * One clip at a time, on its own thread; a request while one renders is dropped (logged). The clip is rendered
 * exactly as playback renders the book (its voices, speed, fixes and reading modes; studio audio first when ready),
 * encoded as AAC in `cacheDir/shared-clips/` and handed to the share sheet through [ClipFileProvider] with a one-off
 * read grant. The previous clip is deleted when a new one is made, not after sharing: the receiving app may read it
 * later.
 */
@AndroidEntryPoint
class ClipService : Service() {
    @Inject
    lateinit var library: Library

    @Inject
    lateinit var bookSettings: BookSettingsStore

    @Inject
    lateinit var pronunciations: PronunciationStore

    private val config = AudiobookConfig()
    private val clipConfig = ClipConfig()
    private lateinit var notices: ClipNotices

    /** This instance started the running job ([JobSlots.CLIP]); a refused or finished start lets it stop. */
    @Volatile
    private var mine = false

    override fun onCreate() {
        super.onCreate() // Hilt injects fields here.
        notices = ClipNotices(this).apply { createChannel() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(ClipNotices.RUNNING_ID, notices.making(), ForegroundJobs.type())
        val id = intent?.getStringExtra(AudioClips.EXTRA_BOOK)?.let(::BookId)
        val page = intent?.getIntExtra(AudioClips.EXTRA_PAGE, 0) ?: 0
        val start = UnitKey(page, intent?.getIntExtra(AudioClips.EXTRA_INDEX, 0) ?: 0)
        val passage = intent?.let(::passageOf)
        when {
            intent?.action != AudioClips.ACTION_MAKE || id == null -> AudoraLog.w(
                TAG,
                "Bad clip request ${intent?.action}",
            )

            // The restart that applies a restore would cut the clip off.
            Backups.restoreUnderWay -> notices.showFailed(getString(R.string.long_job_restoring))

            // This instance's clip goes on: a second request is dropped without stopping it.
            mine -> AudoraLog.w(TAG, "A clip is already being made here; book=${id.logLabel} dropped")

            else -> {
                // Set first: the job may end (and clear it) before launch returns.
                mine = true
                if (!JobSlots.CLIP.launch(THREAD_NAME) { makeClip(id, start, passage) }) {
                    mine = false
                    AudoraLog.w(TAG, "A clip is already being made; book=${id.logLabel} dropped")
                }
            }
        }
        if (!mine) stopSelf(startId)
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        AudoraLog.w(TAG, "Android's time limit for long jobs reached; stopping the clip")
        JobSlots.CLIP.interrupt()
        // Android throws if the service is still in the foreground a few seconds after this; the worker may be
        // inside a long native call, so stop now and let it finish its notice on its own thread.
        leaveForeground()
    }

    override fun onDestroy() {
        // Only this instance's own clip: a refused start's instance must not cut off another's.
        if (mine) JobSlots.CLIP.interrupt()
        super.onDestroy()
    }

    /** A highlight's passage when the request names its end ([AudioClips.sharePassage]), else null. */
    private fun passageOf(intent: Intent): ClipPassage? {
        val endPage = intent.getIntExtra(AudioClips.EXTRA_END_PAGE, NO_PASSAGE)
        if (endPage == NO_PASSAGE) return null
        return ClipPassage(
            startChar = intent.getIntExtra(AudioClips.EXTRA_START_CHAR, 0),
            endPage = endPage,
            endChar = intent.getIntExtra(AudioClips.EXTRA_END_CHAR, Int.MAX_VALUE),
        )
    }

    /** On the worker thread. */
    private fun makeClip(id: BookId, start: UnitKey, passage: ClipPassage?) {
        // A long Kokoro clip renders for minutes: the CPU must not sleep when the screen times out.
        val wakeLock = jobWakeLock(WAKE_LOCK_TAG)
            .apply { acquire(clipConfig.wakeLockMs) }
        try {
            val books = LibraryBookSource(this, library)
            when (val opened = runBlocking { books.open(id, PasswordHandoff.shared.take(id)) }) {
                is Outcome.Success -> opened.value.text.use { clipOf(opened.value, start, passage) }
                is Outcome.Failure -> notices.showFailed(opened.error.message)
            }
        } catch (interrupted: InterruptedException) {
            AudoraLog.i(TAG, "Clip cancelled, book=${id.logLabel}")
        } finally {
            wakeLock.releaseIfHeld()
            mine = false
            leaveForeground()
        }
    }

    private fun clipOf(book: OpenedBook, start: UnitKey, passage: ClipPassage?) {
        val text = book.text
        val factory = BookRendering.Factory(this, bookSettings, pronunciations)
        BookVoice(this, factory, book.book.id, text, RunSettings(language = book.book.language)).use { voice ->
            val rendering = voice.rendering
            val renderer = StudioSentenceRenderer(rendering.live, StudioMode.store(this)) { rendering.key }
            val made = ClipMaker(renderer).make(
                start = start,
                pageCount = text.pageCount,
                pageUnits = { page ->
                    // Through the quote and speaker tracking first, as playback reads each page.
                    when (val read = text.reading(page)) {
                        is Outcome.Success -> read.value.also(voice::page).units
                        is Outcome.Failure -> emptyList<ReadingUnit>().takeIf { read.error.isPageOnly() }
                    }
                },
                plays = rendering.reading::plays,
                passage = passage,
            )
            when (made) {
                is Outcome.Success -> share(book, start, made.value, rendering.style.pitch)
                is Outcome.Failure -> notices.showFailed(getString(R.string.clip_failed_text))
            }
        }
    }

    private fun share(book: OpenedBook, start: UnitKey, clip: Clip, pitch: Float) {
        val title = book.book.title.takeUnless { it == BookTitles.UNTITLED } ?: getString(R.string.export_untitled)
        val folder = File(cacheDir, AudioClips.FOLDER).apply { mkdirs() }
        folder.listFiles()?.forEach { old ->
            if (!old.delete()) AudoraLog.w(TAG, "Could not delete old clip ${old.name}")
        }
        val name =
            getString(R.string.clip_file_name, AudiobookFolder.folderName(title, config), start.page + 1) + ".m4a"
        val file =
            encode(folder, name, start, clip, pitch) ?: return notices.showFailed(getString(R.string.clip_failed_text))
        val quote = clip.units.joinToString(" ") { it.text }
        val text = getString(R.string.clip_share_text, quote, title, start.page + 1)
        val chooser = ShareSheet.chooserFor(this, file, MIME_TYPE, text, getString(R.string.clip_chooser_title))
        AudoraLog.i(TAG, "Clip ready, book=${book.book.id.logLabel} page=${start.page}, ${clip.audio.durationMs} ms")
        notices.showReady(chooser)
        ShareSheet.open(this, chooser, TAG)
    }

    /**
     * The clip as one .m4a file at the listener's [pitch] (ADR-067); null when the encoder or storage failed (logged
     * where it failed).
     */
    private fun encode(folder: File, name: String, start: UnitKey, clip: Clip, pitch: Float): File? {
        val chapter = AudioChapter(number = 1, firstPage = start.page, lastPage = start.page)
        val writer = LocalChapterFiles(folder).create(name).getOrNull()
            ?.let { target -> AacChapterWriter.create(chapter, target, clip.audio.sampleRate, config).getOrNull() }
            ?.atPitch(pitch)
        val done = writer != null && writer.write(clip.audio) is Outcome.Success && writer.finish() is Outcome.Success
        if (!done) writer?.abort()
        return File(folder, name).takeIf { done }
    }

    private companion object {
        const val TAG = "Clip.Service"
        const val THREAD_NAME = "audio-clip"

        const val WAKE_LOCK_TAG = "Audora:AudioClip"
        const val MIME_TYPE = "audio/mp4"

        /** No [AudioClips.EXTRA_END_PAGE]: a clip to the paragraph's end, not a passage. */
        const val NO_PASSAGE = -1

        /** A page with no text is skipped (as playback skips it); a book that cannot be read stops the clip. */
        fun PdfPageError.isPageOnly(): Boolean =
            this is PdfPageError.ExtractionFailed || this is PdfPageError.PageOutOfRange
    }
}
