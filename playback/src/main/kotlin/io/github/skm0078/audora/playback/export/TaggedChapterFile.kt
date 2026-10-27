// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/export/TaggedChapterFile.kt
// Purpose: A chapter file that writes its MP4 tags (title, book, author, cover) just before it is published.

package io.github.skm0078.audora.playback.export

import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import java.io.IOException

/**
 * [file] with [tags] added once the encoder is done with it: MediaMuxer has stopped by the time a writer publishes,
 * and the file is still pending, so the media scanner first sees it complete with its tags. Tags are a nicety: a
 * file that cannot take them is still a good chapter, so a failure is logged and the file is published as it is.
 */
class TaggedChapterFile(private val file: ChapterFile, private val tags: AudioTags) : ChapterFile by file {
    override fun publish(): Outcome<Unit, ExportError.Storage> {
        try {
            Mp4Tags.write(DescriptorFile(file.descriptor), tags)
        } catch (e: IOException) {
            AudoraLog.w(TAG, "Tags not written to $name, cover=${tags.book.coverJpeg != null}", e)
        }
        return file.publish()
    }

    private companion object {
        const val TAG = "Export.Tags"
    }
}
