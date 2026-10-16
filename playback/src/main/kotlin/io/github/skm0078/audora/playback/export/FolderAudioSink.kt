// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/export/FolderAudioSink.kt
// Purpose: Writes each exported chapter as "NN Pages a-b.m4a" in one folder: the layout audiobook players expect.

package io.github.skm0078.audora.playback.export

import io.github.skm0078.audora.core.config.AudiobookConfig
import io.github.skm0078.audora.core.result.Outcome
import java.io.File

/**
 * One folder per book, one file per chapter: Voice, Smart AudioBook Player and the like treat such a folder as
 * a book and its files as chapters. [folder] must exist and be writable; [chapterCount] sizes the number prefix.
 */
class FolderAudioSink(
    private val folder: File,
    private val chapterCount: Int,
    private val config: AudiobookConfig = AudiobookConfig(),
) : ChapterAudioSink {
    override fun open(chapter: AudioChapter, sampleRate: Int): Outcome<ChapterAudioWriter, ExportError.Storage> {
        if (!folder.isDirectory) return Outcome.Failure(ExportError.Storage("the folder ${folder.name} is missing"))
        val file = File(folder, "${chapter.fileStem(chapterCount)}.$EXTENSION")
        return AacChapterWriter.create(chapter, file, sampleRate, config)
    }

    private companion object {
        const val EXTENSION = "m4a"
    }
}
