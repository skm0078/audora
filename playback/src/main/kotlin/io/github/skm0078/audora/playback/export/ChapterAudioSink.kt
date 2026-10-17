// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/export/ChapterAudioSink.kt
// Purpose: Where an exported chapter's audio goes: the seam between the exporter and the encoder/storage.

package io.github.skm0078.audora.playback.export

import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.speech.audio.PcmAudio

/** One chapter's file being written. Not thread-safe: the exporter's thread owns it from open to finish. */
interface ChapterAudioWriter {
    /** The file's sample rate, fixed when it was opened; the exporter resamples sentences to it. */
    val sampleRate: Int

    /** Appends [audio], which is at [sampleRate]. */
    fun write(audio: PcmAudio): Outcome<Unit, ExportError.Storage>

    /** Completes the file and releases the encoder. */
    fun finish(): Outcome<ExportedChapter, ExportError.Storage>

    /** Releases the encoder and deletes the partial file. Safe to call after a failed [write]. */
    fun abort()
}

/** Opens a chapter's file on its first audio, so a chapter with nothing to read leaves no empty file. */
fun interface ChapterAudioSink {
    fun open(chapter: AudioChapter, sampleRate: Int): Outcome<ChapterAudioWriter, ExportError.Storage>
}
