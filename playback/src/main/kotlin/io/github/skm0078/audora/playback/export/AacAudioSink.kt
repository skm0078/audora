// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/export/AacAudioSink.kt
// Purpose: Writes each exported chapter as "NN Pages a-b.m4a", one folder per book, as audiobook players expect.

package io.github.skm0078.audora.playback.export

import io.github.skm0078.audora.core.config.AudiobookConfig
import io.github.skm0078.audora.core.result.Outcome

/**
 * Voice, Smart AudioBook Player and the like treat a folder of numbered files as a book and its files as
 * chapters. [chapterCount] sizes the number prefix so the files sort in reading order.
 */
class AacAudioSink(
    private val files: ChapterFiles,
    private val chapterCount: Int,
    private val config: AudiobookConfig = AudiobookConfig(),
) : ChapterAudioSink {
    override fun open(chapter: AudioChapter, sampleRate: Int): Outcome<ChapterAudioWriter, ExportError.Storage> =
        when (val file = files.create("${chapter.fileStem(chapterCount)}.$EXTENSION")) {
            is Outcome.Success -> AacChapterWriter.create(chapter, file.value, sampleRate, config)
            is Outcome.Failure -> file
        }

    private companion object {
        const val EXTENSION = "m4a"
    }
}
