// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/epub/MediaOverlay.kt
// Purpose: The talking book's timing: where each sentence is in its chapter's audio, written as EPUB 3 SMIL.

package io.github.skm0078.audora.playback.epub

import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.playback.export.AudioChapter
import io.github.skm0078.audora.playback.export.ExportListener
import io.github.skm0078.audora.playback.export.ExportedChapter
import io.github.skm0078.audora.playback.queue.UnitKey
import io.github.skm0078.audora.speech.audio.PcmAudio
import java.io.File

/** Sentence [sentenceId] plays from sample [start] (inclusive) to [end] (exclusive) of its chapter's file. */
data class SentenceClip(val sentenceId: String, val start: Long, val end: Long) {
    init {
        require(start in 0..end) { "start ($start) must be in 0..end ($end)" }
    }
}

/** A chapter's audio file and where each sentence is in it. */
data class ChapterAudio(val chapter: Int, val file: File, val sampleRate: Int, val clips: List<SentenceClip>) {
    val samples: Long get() = clips.lastOrNull()?.end ?: 0L
}

/**
 * Listens to the audiobook exporter and records each sentence's place in its chapter's file, in samples at the
 * file's rate (the exporter resamples every sentence to it), so timings never drift over a long chapter. Feed it on
 * the exporter's thread only. [onChapter] gets each finished chapter (to stage it, [StagedChapters]); [restore] puts
 * back chapters staged by an earlier run, which this run does not render again.
 */
class OverlayRecorder(
    private val folder: File,
    private val onPage: (AudioChapter, Int) -> Unit = { _, _ -> },
    private val onChapter: (StagedChapter) -> Unit = {},
) : ExportListener {
    private val open = ArrayList<SentenceClip>()
    private val openKeys = ArrayList<UnitKey>()
    private var openRate = 0
    private var position = 0L
    private val done = LinkedHashMap<Int, ChapterAudio>()
    private val read = HashSet<UnitKey>()

    /** Every finished chapter's audio, by chapter number. */
    val chapters: Map<Int, ChapterAudio> get() = done

    /** Every sentence in a finished chapter's file: the copy keeps their text even when it is scored as junk. */
    val voiced: Set<UnitKey> get() = read

    override fun onPageDone(chapter: AudioChapter, page: Int) = onPage(chapter, page)

    fun restore(staged: Collection<StagedChapter>) {
        staged.sortedBy { it.audio.chapter }.forEach { chapter ->
            done[chapter.audio.chapter] = chapter.audio
            read.addAll(chapter.voiced)
        }
    }

    override fun onSentenceWritten(chapter: AudioChapter, unit: ReadingUnit, audio: PcmAudio) {
        val end = position + audio.samples.size
        open.add(SentenceClip(DocSentence(unit).id, position, end))
        openKeys.add(UnitKey.of(unit))
        openRate = audio.sampleRate
        position = end
    }

    override fun onChapterDone(chapter: ExportedChapter) {
        val number = chapter.chapter.number
        val audio = ChapterAudio(number, File(folder, chapter.name), openRate, open.toList())
        done[number] = audio
        read.addAll(openKeys)
        onChapter(StagedChapter(audio, openKeys.toList()))
        open.clear()
        openKeys.clear()
        position = 0L
    }
}

/** SMIL 3.0 media overlays, one document per chapter (EPUB Media Overlays 3.3). */
internal object Smil {
    private const val MS_PER_SECOND = 1_000L
    private const val SECONDS_PER_MINUTE = 60L
    private const val SECONDS_PER_HOUR = 3_600L

    fun write(chapter: DocChapter, audio: ChapterAudio): String = buildString {
        val text = EpubPaths.chapter(chapter.number)
        val known = chapter.sentences.mapTo(HashSet()) { it.id }
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<smil xmlns=\"http://www.w3.org/ns/SMIL\" xmlns:epub=\"http://www.idpf.org/2007/ops\" ")
        append("version=\"3.0\">\n<body>\n<seq id=\"seq-").append(chapter.number).append("\" epub:textref=\"")
        append(text).append("\" epub:type=\"chapter\">\n")
        audio.clips.filter { it.sentenceId in known && it.end > it.start }.forEachIndexed { i, clip ->
            append("<par id=\"par-").append(i + 1).append("\"><text src=\"").append(text).append('#')
            append(clip.sentenceId).append("\"/><audio src=\"").append(EpubPaths.audio(chapter.number))
            append("\" clipBegin=\"").append(clock(clip.start, audio.sampleRate))
            append("\" clipEnd=\"").append(clock(clip.end, audio.sampleRate)).append("\"/></par>\n")
        }
        append("</seq>\n</body>\n</smil>\n")
    }

    /** "h:mm:ss.fff", the SMIL clock value EPUB reading systems accept everywhere. */
    fun clock(samples: Long, sampleRate: Int): String {
        val ms = samples * MS_PER_SECOND / sampleRate
        val seconds = ms / MS_PER_SECOND
        return "%d:%02d:%02d.%03d".format(
            java.util.Locale.ROOT,
            seconds / SECONDS_PER_HOUR,
            seconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE,
            seconds % SECONDS_PER_MINUTE,
            ms % MS_PER_SECOND,
        )
    }
}
