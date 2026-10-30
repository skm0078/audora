// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/jobs/JobLeftovers.kt
// Purpose: Cleans up what unfinished jobs left (half downloads, unfinished audiobooks and talking books) at app start
//          and when the listener asks, and tells the Jobs screen what is waiting and what was cleaned (ADR
//          2026-10-29-jobs-card-and-leftovers).

package io.github.skm0078.audora.playback.jobs

import android.content.Context
import android.content.SharedPreferences
import io.github.skm0078.audora.core.config.LeftoverConfig
import io.github.skm0078.audora.core.files.LeftoverKind
import io.github.skm0078.audora.core.files.LeftoverPolicy
import io.github.skm0078.audora.core.files.LeftoverTally
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.BookOutputStore
import io.github.skm0078.audora.core.library.Library
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.playback.epub.CopyJob
import io.github.skm0078.audora.playback.epub.StagedChapters
import io.github.skm0078.audora.playback.epub.TalkingBookLeftovers
import io.github.skm0078.audora.playback.export.AudiobookLeftovers
import io.github.skm0078.audora.playback.export.ExportRecords
import io.github.skm0078.audora.playback.export.JobSlots
import io.github.skm0078.audora.playback.export.MediaStoreSavedFiles
import io.github.skm0078.audora.speech.download.DownloadJobs
import io.github.skm0078.audora.speech.download.DownloadLeftovers
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * What the Jobs screen shows about leftovers: what is [waiting] now (what Clean up now would delete), and the last
 * clean-up that deleted anything, [lastCleaned] at [lastCleanedAtMs] (null if none yet).
 */
data class LeftoverState(
    val waiting: LeftoverTally,
    val lastCleaned: LeftoverTally,
    val lastCleanedAtMs: Long?,
    val keepDays: Int,
)

/**
 * One owner per kind of leftover, each run with its own kind of job held off: an export's files in
 * [JobSlots.EXPORT], a talking book's in [JobSlots.COPY], and no download's file while any download is queued or
 * running. A kind whose job runs is skipped (and not counted as waiting): its files are live work. The library's
 * books and Made's files are read first, so a book's leftovers go at once when the book is gone and nothing Made
 * lists is ever deleted. Everything here blocks on the disk and MediaStore, so it runs on [Dispatchers.IO].
 */
class JobLeftovers internal constructor(
    private val downloads: DownloadLeftovers,
    private val audiobooks: AudiobookLeftovers,
    private val talkingBooks: TalkingBookLeftovers,
    private val report: LeftoverReport,
    private val config: LeftoverConfig = LeftoverConfig(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutableState = MutableStateFlow<LeftoverState?>(null)

    /** Null until first measured ([refresh]); then after every clean-up. */
    val state: StateFlow<LeftoverState?> = mutableState.asStateFlow()

    /** At app start, after [LeftoverConfig.startDelayMs]: what was kept long enough, or whose book is gone. */
    suspend fun cleanAtStart(library: Library, outputs: BookOutputStore) {
        delay(config.startDelayMs)
        val cleaned = guarded("clean up at start", LeftoverTally.NONE) {
            clean(Known.read(library, outputs), asked = false)
        }
        AudoraLog.i(TAG, "Clean-up at start: $cleaned")
    }

    /** The Jobs screen's Clean up now: everything waiting, whatever its age. */
    suspend fun cleanNow(library: Library, outputs: BookOutputStore): LeftoverTally =
        guarded("clean up now", LeftoverTally.NONE) { clean(Known.read(library, outputs), asked = true) }
            .also { AudoraLog.i(TAG, "Clean-up asked for: $it") }

    /** Measures what is waiting, for the Jobs screen. */
    suspend fun refresh(library: Library, outputs: BookOutputStore) = guarded("measure", Unit) {
        val known = Known.read(library, outputs)
        withContext(Dispatchers.IO) { publish(measure(known)) }
    }

    /**
     * A clean-up or a measure that the disk, MediaStore or the database refused is logged and skipped: the files stay
     * (the safe side) and the next start tries again. Never a crash for housekeeping.
     */
    private suspend fun <T> guarded(what: String, fallback: T, block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        failed(what, e, fallback)
    } catch (e: SecurityException) {
        failed(what, e, fallback)
    } catch (e: IllegalArgumentException) {
        failed(what, e, fallback)
    } catch (e: IllegalStateException) {
        failed(what, e, fallback)
    }

    /** What [kind]'s stopped or failed job of [book] left, for its card's Delete unfinished files; 0 if nothing. */
    suspend fun bytesFor(kind: LongJobKind, book: BookId, outputs: BookOutputStore): Long = guarded("measure", 0L) {
        val made = Known.made(outputs)
        withContext(Dispatchers.IO) {
            when (kind) {
                LongJobKind.AUDIOBOOK -> JobSlots.EXPORT.runIfFree { audiobooks.bytesFor(book.value, made) }
                LongJobKind.COPY -> JobSlots.COPY.runIfFree { talkingBooks.bytesFor(book.value) }
                LongJobKind.STUDIO -> null // Studio keeps playable audio, deleted from its own Details.
            } ?: 0L
        }
    }

    /** Deletes what [kind]'s stopped or failed job of [book] left, at the listener's word. */
    suspend fun discard(kind: LongJobKind, book: BookId, library: Library, outputs: BookOutputStore): LeftoverTally =
        guarded("delete unfinished files", LeftoverTally.NONE) {
            val known = Known.read(library, outputs)
            withContext(Dispatchers.IO) {
                val deleted = when (kind) {
                    LongJobKind.AUDIOBOOK -> JobSlots.EXPORT.runIfFree { audiobooks.discard(book.value, known.made) }
                    LongJobKind.COPY -> JobSlots.COPY.runIfFree { talkingBooks.discard(book.value) }
                    LongJobKind.STUDIO -> null
                } ?: LeftoverTally.NONE
                AudoraLog.i(TAG, "Unfinished files of job=$kind book=${book.logLabel} deleted: $deleted")
                record(deleted)
                publish(measure(known))
                deleted
            }
        }

    internal suspend fun clean(known: Known, asked: Boolean): LeftoverTally = withContext(Dispatchers.IO) {
        val policy = LeftoverPolicy(config, clock())
        val audiobookHashes = known.books.mapTo(HashSet(), ExportRecords::hashOf)
        val stagingPrefixes = known.books.mapTo(HashSet(), StagedChapters::bookPrefix)
        val cleaned = listOfNotNull(
            downloads.clean(policy, asked),
            JobSlots.EXPORT.runIfFree { audiobooks.clean(policy, audiobookHashes, known.made, asked) },
            JobSlots.COPY.runIfFree { talkingBooks.clean(policy, stagingPrefixes, asked) },
        ).fold(LeftoverTally.NONE, LeftoverTally::plus)
        record(cleaned)
        publish(measure(known))
        cleaned
    }

    private fun measure(known: Known): LeftoverTally = listOfNotNull(
        downloads.measure(),
        JobSlots.EXPORT.runIfFree { audiobooks.measure(known.made) },
        JobSlots.COPY.runIfFree { talkingBooks.measure() },
    ).fold(LeftoverTally.NONE, LeftoverTally::plus)

    private fun record(cleaned: LeftoverTally) {
        if (!cleaned.isEmpty) report.save(cleaned, clock())
    }

    private fun publish(waiting: LeftoverTally) {
        val last = report.load()
        mutableState.value = LeftoverState(waiting, last?.first ?: LeftoverTally.NONE, last?.second, config.keepDays)
    }

    /** The library's books (ids) and every file Made lists (locations), read once per clean-up. */
    internal class Known(val books: Set<String>, val made: Set<String>) {
        companion object {
            suspend fun read(library: Library, outputs: BookOutputStore) =
                Known(library.books().first().mapTo(HashSet()) { it.id.value }, made(outputs))

            suspend fun made(outputs: BookOutputStore): Set<String> =
                outputs.observeAll().first().mapTo(HashSet()) { it.location }
        }
    }

    companion object {
        private const val TAG = "Jobs.Leftovers"

        private fun <T> failed(what: String, e: Exception, fallback: T): T {
            AudoraLog.e(TAG, "Could not $what; files kept", e)
            return fallback
        }

        @Volatile
        private var instance: JobLeftovers? = null

        fun get(context: Context): JobLeftovers = instance ?: synchronized(this) {
            instance ?: create(context.applicationContext).also { instance = it }
        }

        private fun create(context: Context) = JobLeftovers(
            downloads = DownloadLeftovers(context.noBackupFilesDir) { DownloadJobs.anyActive(context) },
            audiobooks = AudiobookLeftovers(ExportRecords.of(context), MediaStoreSavedFiles(context.contentResolver)),
            talkingBooks = TalkingBookLeftovers(CopyJob.stagingRoot(context)),
            report = LeftoverReport.of(context),
        )
    }
}

/** The last clean-up that deleted anything, kept so the Jobs screen can say what went and when. */
internal interface LeftoverReport {
    fun save(cleaned: LeftoverTally, atMs: Long)

    /** The last clean-up and when it ran; null before the first. */
    fun load(): Pair<LeftoverTally, Long>?

    companion object {
        fun of(context: Context): LeftoverReport = PreferencesLeftoverReport(
            context.getSharedPreferences(PreferencesLeftoverReport.FILE, Context.MODE_PRIVATE),
        )
    }
}

private class PreferencesLeftoverReport(private val prefs: SharedPreferences) : LeftoverReport {
    override fun save(cleaned: LeftoverTally, atMs: Long) {
        val editor = prefs.edit().putLong(KEY_AT, atMs).putLong(KEY_BYTES, cleaned.bytes)
        LeftoverKind.entries.forEach { editor.putInt(keyOf(it), cleaned.count(it)) }
        editor.apply()
    }

    override fun load(): Pair<LeftoverTally, Long>? {
        val at = prefs.getLong(KEY_AT, 0L).takeIf { it > 0L } ?: return null
        val counts = LeftoverKind.entries.associateWith { prefs.getInt(keyOf(it), 0) }.filterValues { it > 0 }
        return LeftoverTally(counts, prefs.getLong(KEY_BYTES, 0L)) to at
    }

    private fun keyOf(kind: LeftoverKind) = "count_${kind.name.lowercase()}"

    companion object {
        const val FILE = "job_leftovers"
        private const val KEY_AT = "last_cleaned_at"
        private const val KEY_BYTES = "last_cleaned_bytes"
    }
}
