// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/studio/StudioAudioFile.kt
// Purpose: Saves a book's ready studio audio as one .m4a in Audiobooks/<title>/, to play in any app or copy (ADR-063).

package io.github.skm0078.audora.playback.studio

import android.content.Context
import io.github.skm0078.audora.core.config.AudiobookConfig
import io.github.skm0078.audora.core.config.StudioConfig
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.playback.R
import io.github.skm0078.audora.playback.export.AacChapterWriter
import io.github.skm0078.audora.playback.export.AudioChapter
import io.github.skm0078.audora.playback.export.AudiobookFolder
import io.github.skm0078.audora.playback.export.ChapterAudioWriter
import io.github.skm0078.audora.playback.export.ExportedChapter
import io.github.skm0078.audora.playback.export.MediaStoreChapterFiles

/** A saved file: [folder] is where a file manager finds it ("Audiobooks/Title"), [name] the file's own name. */
data class SavedStudioFile(val folder: String, val name: String, val durationMs: Long)

/**
 * Studio audio lives inside the app, where only ListenLock plays it. This writes what is ready, page after page, with
 * the audiobook export's encoder (AAC in .m4a) into its folder, so the listener can play it in another app, copy it
 * to a computer or share it. Nothing is rendered: it takes as long as encoding, a few minutes for hours of audio.
 * Clips at another sample rate than the first are resampled to it. Blocking: call it off the main thread.
 */
object StudioAudioFile {
    private const val TAG = "Studio.File"
    private const val EXTENSION = "m4a"
    private val config = AudiobookConfig()

    fun save(context: Context, id: BookId, title: String): Outcome<SavedStudioFile, String> {
        val store = StudioMode.store(context)
        val nothing = context.getString(R.string.studio_file_nothing)
        val ready = store.bookAudio(id) ?: return Outcome.Failure(nothing)
        val folder = AudiobookFolder.relativePath(title, context.getString(R.string.studio_untitled), config)
        val name = context.getString(R.string.studio_file_name, ready.firstPage + 1, ready.lastPage + 1) + ".$EXTENSION"
        val chapter = AudioChapter(number = 1, firstPage = ready.firstPage, lastPage = ready.lastPage)
        val written = writeAll(store.readyPages(id), LosslessPcm(StudioConfig().blockSamples)) { sampleRate ->
            create(context, folder, name, chapter, sampleRate)
        }
        return when (written) {
            null -> Outcome.Failure(nothing)

            is Outcome.Success -> {
                AudoraLog.i(TAG, "Studio audio saved as a file, book=${id.logLabel}, ms=${written.value.durationMs}")
                Outcome.Success(SavedStudioFile(folder.trimEnd('/'), written.value.name, written.value.durationMs))
            }

            is Outcome.Failure -> Outcome.Failure(context.getString(R.string.studio_file_failed))
        }
    }

    /**
     * Every clip of [pages] into one file, opened by [open] at the first clip's sample rate; null when there was no
     * clip at all. A failed write deletes the partial file.
     */
    private fun writeAll(
        pages: Sequence<StudioPage>,
        codec: LosslessPcm,
        open: (sampleRate: Int) -> ChapterAudioWriter?,
    ): Outcome<ExportedChapter, Unit>? {
        var writer: ChapterAudioWriter? = null
        val written = pages.flatMap { it.clips.asSequence() }.all { clip ->
            val audio = clip.audio(codec)
            val target = writer ?: open(audio.sampleRate)?.also { writer = it }
            target != null && target.write(audio.resampled(target.sampleRate)) is Outcome.Success
        }
        val target = writer
        return when {
            target == null -> if (written) null else Outcome.Failure(Unit)

            !written -> {
                target.abort()
                Outcome.Failure(Unit)
            }

            else -> when (val finished = target.finish()) {
                is Outcome.Success -> Outcome.Success(finished.value)
                is Outcome.Failure -> Outcome.Failure(Unit)
            }
        }
    }

    private fun create(
        context: Context,
        folder: String,
        name: String,
        chapter: AudioChapter,
        sampleRate: Int,
    ): ChapterAudioWriter? {
        val file = MediaStoreChapterFiles(context.contentResolver, folder).create(name)
        val writer = (file as? Outcome.Success)?.value?.let { AacChapterWriter.create(chapter, it, sampleRate, config) }
        return when {
            file is Outcome.Failure -> null.also { AudoraLog.w(TAG, "Studio file not created: ${file.error.detail}") }
            writer is Outcome.Success -> writer.value
            writer is Outcome.Failure -> null.also { AudoraLog.w(TAG, "Studio file encoder: ${writer.error.detail}") }
            else -> null
        }
    }
}
