// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/download/DownloadJobs.kt
// Purpose: Every model download (voices, tutor, dictionaries, text recognition, punctuation, voice commands, acted
//          voices, the story brain) in one list, for the Jobs screen: what it is, how far it is, and a way to stop it.

package io.github.skm0078.audora.speech.download

import android.content.Context
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.speech.acted.ActedVoiceDownloadWorker
import io.github.skm0078.audora.speech.brain.StoryBrainDownloadWorker
import io.github.skm0078.audora.speech.dictionary.DictionaryDownloadWorker
import io.github.skm0078.audora.speech.kws.VoiceCommandDownloadWorker
import io.github.skm0078.audora.speech.listen.ListenDownloadWorker
import io.github.skm0078.audora.speech.ocr.OcrModelDownloadWorker
import io.github.skm0078.audora.speech.punct.PunctuationDownloadWorker
import io.github.skm0078.audora.speech.voices.VoiceDownloadWorker
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** What a download fetches; the Jobs screen names it from this and [DownloadJob.item]. */
enum class DownloadKind(internal val worker: Class<*>) {
    VOICE(VoiceDownloadWorker::class.java),
    TUTOR(ListenDownloadWorker::class.java),
    DICTIONARY(DictionaryDownloadWorker::class.java),
    TEXT_RECOGNITION(OcrModelDownloadWorker::class.java),
    PUNCTUATION(PunctuationDownloadWorker::class.java),
    VOICE_COMMANDS(VoiceCommandDownloadWorker::class.java),
    ACTED_VOICE(ActedVoiceDownloadWorker::class.java),
    STORY_BRAIN(StoryBrainDownloadWorker::class.java),
}

/**
 * One download as the Jobs screen shows it.
 *
 * @property item which one of its kind: a voice id, or a language's BCP 47 tag for the tutor and dictionaries; null
 *   for kinds with one download, and for a download queued before items were recorded.
 * @property bytes received so far, and [total] expected; both 0 while it waits (for a network, or its turn).
 */
data class DownloadJob(
    val id: UUID,
    val kind: DownloadKind,
    val item: String?,
    val running: Boolean,
    val bytes: Long = 0L,
    val total: Long = 0L,
)

/**
 * Downloads are WorkManager jobs, each owned by its own feature. They are found here by their worker's class, which
 * WorkManager tags every job with, so a download queued by an older version of the app shows too. Each feature adds
 * [tagsFor] when it queues one, so the screen can say which voice or language it is.
 */
object DownloadJobs {
    private const val TAG = "Speech.DownloadJobs"
    private const val ITEM_PREFIX = "audora.download.item="

    /** Every worker's progress uses these two keys (bytes received, bytes expected). */
    private const val KEY_BYTES = "bytes"
    private const val KEY_TOTAL = "total"

    private val byWorker: Map<String, DownloadKind> = DownloadKind.entries.associateBy { it.worker.name }

    /** The tag that names [item] on a download's request; add it with `addTag`. */
    fun itemTag(item: String): String = ITEM_PREFIX + item

    /** Downloads waiting or running now, in the order WorkManager lists them. */
    fun observe(context: Context): Flow<List<DownloadJob>> {
        val query = WorkQuery.Builder.fromTags(byWorker.keys.toList())
            .addStates(listOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED))
            .build()
        return WorkManager.getInstance(context).getWorkInfosFlow(query).map { infos -> infos.mapNotNull(::jobOf) }
    }

    /**
     * Stops [job]. Like each feature's own Cancel for the tutor, dictionaries and models, what was received is kept,
     * so downloading it again goes on from there.
     */
    fun cancel(context: Context, job: DownloadJob) {
        WorkManager.getInstance(context).cancelWorkById(job.id)
        AudoraLog.i(TAG, "Download cancelled from the Jobs screen, kind=${job.kind} item=${job.item}")
    }

    internal fun jobOf(info: WorkInfo): DownloadJob? {
        val kind = info.tags.firstNotNullOfOrNull(byWorker::get) ?: return null
        val item = info.tags.firstOrNull { it.startsWith(ITEM_PREFIX) }?.removePrefix(ITEM_PREFIX)
        val running = info.state == WorkInfo.State.RUNNING
        return DownloadJob(
            id = info.id,
            kind = kind,
            item = item,
            running = running,
            bytes = if (running) info.progress.getLong(KEY_BYTES, 0L) else 0L,
            total = if (running) info.progress.getLong(KEY_TOTAL, 0L) else 0L,
        )
    }
}

/** [this] request with the tag that names [item] on the Jobs screen. */
fun OneTimeWorkRequest.Builder.forItem(item: String): OneTimeWorkRequest.Builder = addTag(DownloadJobs.itemTag(item))
