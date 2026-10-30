// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/epub/StagedChapters.kt
// Purpose: Keeps a talking book's finished chapters (audio and timings) so a stopped run goes on later (item 76).

package io.github.skm0078.audora.playback.epub

import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.playback.queue.UnitKey
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** A finished chapter as staged: its audio and timings, and the sentences read into it. */
data class StagedChapter(val audio: ChapterAudio, val voiced: List<UnitKey>)

/**
 * The staging folder of one book at one rendering signature: chapter audio files written by the exporter, and beside
 * each a small text record of its timings, written once the chapter's file is finished (temp file, then rename), so a
 * record always describes a complete file. Android's 6 h limit on long foreground jobs, a full disk at pack time or a
 * crash then cost only the chapter in progress: [load] gives back the rest and the run renders what is missing.
 *
 * Records hold page and sentence numbers and sample offsets, never the book's text.
 */
class StagedChapters(private val folder: File) {
    /** Writes [chapter]'s record; false (logged) if it could not, which only means it is rendered again next time. */
    fun save(chapter: StagedChapter): Boolean {
        val record = File(folder, recordName(chapter.audio.chapter))
        val partial = File(folder, record.name + PARTIAL_SUFFIX)
        return try {
            partial.writeText(encode(chapter))
            if (!partial.renameTo(record)) throw IOException("rename failed")
            true
        } catch (e: IOException) {
            AudoraLog.w(TAG, "Chapter ${chapter.audio.chapter} not staged: ${e.javaClass.simpleName}")
            partial.delete()
            false
        }
    }

    /** Every staged chapter whose record reads back and whose audio file is still there, by chapter number. */
    fun load(): Map<Int, StagedChapter> {
        val records = folder.listFiles { file ->
            file.name.startsWith(RECORD_PREFIX) &&
                file.name.endsWith(RECORD_SUFFIX)
        }
        return records.orEmpty().mapNotNull { record ->
            val staged = try {
                decode(record.readLines())
            } catch (e: IOException) {
                AudoraLog.w(TAG, "Staged record unreadable: ${e.javaClass.simpleName}")
                null
            }
            staged?.takeIf { it.audio.file.isFile && it.audio.file.length() > 0 }
        }.associateBy { it.audio.chapter }
    }

    private fun encode(chapter: StagedChapter): String = buildString {
        val audio = chapter.audio
        append(VERSION).append('\n')
        append("chapter ").append(audio.chapter).append('\n')
        append("file ").append(audio.file.name).append('\n')
        append("rate ").append(audio.sampleRate).append('\n')
        append("voiced ").append(chapter.voiced.joinToString(",") { "${it.page}:${it.index}" }).append('\n')
        audio.clips.forEach { clip ->
            append("clip ").append(clip.sentenceId).append(' ').append(clip.start).append(' ').append(clip.end)
            append('\n')
        }
    }

    /** Null for a record of another version or a damaged one: that chapter is rendered again. */
    private fun decode(lines: List<String>): StagedChapter? {
        val fields = lines.drop(1).filter { it.isNotEmpty() }.map {
            it.substringBefore(' ') to
                it.substringAfter(' ', "")
        }
        val single = fields.filter { it.first != "clip" }.toMap()
        val clips = fields.filter { it.first == "clip" }.map { clipOf(it.second) }
        val voiced = single["voiced"].orEmpty().split(',').filter { it.isNotEmpty() }.map(::keyOf)
        val audio = audioOf(single, clips.filterNotNull())
        val whole = lines.firstOrNull() == VERSION && null !in clips && null !in voiced
        return if (whole && audio != null) StagedChapter(audio, voiced.filterNotNull()) else null
    }

    private fun audioOf(fields: Map<String, String>, clips: List<SentenceClip>): ChapterAudio? {
        val file = fields["file"]?.takeIf { it.isNotEmpty() && '/' !in it }?.let { File(folder, it) }
        val rate = fields["rate"]?.toIntOrNull()?.takeIf { it > 0 }
        return fields["chapter"]?.toIntOrNull()?.let { chapter ->
            if (file != null && rate != null) ChapterAudio(chapter, file, rate, clips) else null
        }
    }

    private fun clipOf(text: String): SentenceClip? {
        val parts = text.split(' ').takeIf { it.size == CLIP_PARTS } ?: return null
        val start = parts[1].toLongOrNull()
        val end = parts[2].toLongOrNull()
        return if (start != null && end != null && start in 0..end) SentenceClip(parts[0], start, end) else null
    }

    private fun keyOf(text: String): UnitKey? {
        val page = text.substringBefore(':').toIntOrNull()
        val index = text.substringAfter(':', "").toIntOrNull()
        return if (page == null || index == null) null else UnitKey(page, index)
    }

    companion object {
        private const val TAG = "Copy.Staging"
        private const val VERSION = "audora-staged-chapter 1"
        private const val RECORD_PREFIX = "chapter-"
        private const val RECORD_SUFFIX = ".staged"
        private const val PARTIAL_SUFFIX = ".partial"
        private const val CLIP_PARTS = 3
        private const val HASH_HEX_CHARS = 16

        private fun recordName(chapter: Int) = "$RECORD_PREFIX$chapter$RECORD_SUFFIX"

        /**
         * The staging folder for book [bookId] rendered at [signature] under [root]; every other book's or signature's
         * folder is deleted, so at most one talking book is staged at a time (its audio, about 30 MB an hour).
         */
        fun folderFor(root: File, bookId: String, signature: String): File {
            val name = "${bookPrefix(bookId)}${hash(signature)}"
            root.listFiles()?.filter { it.name != name }?.forEach { it.deleteRecursively() }
            return File(root, name).apply { mkdirs() }
        }

        /** How every staging folder of [bookId] begins, whatever its signature ([TalkingBookLeftovers]). */
        fun bookPrefix(bookId: String): String = "${hash(bookId)}-"

        private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(HASH_HEX_CHARS)
    }
}
