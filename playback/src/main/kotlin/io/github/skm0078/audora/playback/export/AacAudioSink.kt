// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/export/AacAudioSink.kt
// Purpose: Writes each exported chapter as "NN Pages a-b.m4a", one folder per book, as audiobook players expect.

package io.github.skm0078.audora.playback.export

import io.github.skm0078.audora.core.config.AudiobookConfig
import io.github.skm0078.audora.core.model.SpeechStyle
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.map

/**
 * Voice, Smart AudioBook Player and the like treat a folder of numbered files as a book and its files as
 * chapters. [chapterCount] sizes the number prefix so the files sort in reading order. [pitch] is the listener's
 * (ADR-067); the files are shifted to it, since they play outside ListenLock's player. [book], when given, tags
 * each file with the book's title, author and cover, so players show the book instead of a list of page ranges.
 */
class AacAudioSink(
    private val files: ChapterFiles,
    private val chapterCount: Int,
    private val config: AudiobookConfig = AudiobookConfig(),
    private val pitch: Float = SpeechStyle.NORMAL_PITCH,
    private val book: BookTags? = null,
) : ChapterAudioSink {
    override fun open(chapter: AudioChapter, sampleRate: Int): Outcome<ChapterAudioWriter, ExportError.Storage> =
        when (val file = files.create("${chapter.fileStem(chapterCount)}.$EXTENSION")) {
            is Outcome.Success -> {
                val tags = book?.forChapter(chapter, chapterCount)
                AacChapterWriter.create(chapter, file.value, sampleRate, config, tags).map { it.atPitch(pitch) }
            }

            is Outcome.Failure -> file
        }

    private companion object {
        const val EXTENSION = "m4a"
    }
}

/** What every chapter file of one book shares; [forChapter] adds the chapter's own title and number. */
class BookTags(val title: String, val author: String?, val coverJpeg: ByteArray?) {
    fun forChapter(chapter: AudioChapter, chapterCount: Int) = AudioTags(
        title = chapter.fileStem(chapterCount),
        album = title,
        artist = author,
        track = chapter.number,
        trackTotal = chapterCount,
        genre = GENRE,
        coverJpeg = coverJpeg,
    )

    private companion object {
        /** The genre audiobook players and file managers file these under; a fixed tag value, never translated. */
        const val GENRE = "Audiobook"
    }
}
