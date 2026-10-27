// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/export/Mp4Tags.kt
// Purpose: Writes iTunes-style tags (title, book, author, track, cover) into a finished .m4a, which MediaMuxer cannot.

package io.github.skm0078.audora.playback.export

import java.io.IOException
import java.nio.ByteBuffer

/** What an audiobook player shows for one chapter file: [title] is the chapter, [book] the album it belongs to. */
class AudioTags(val title: String, val track: Int, val trackTotal: Int, val book: BookTags)

/** What every chapter file of one book shares; [forChapter] adds the chapter's own title and number. */
class BookTags(val title: String, val author: String?, val coverJpeg: ByteArray?) {
    fun forChapter(chapter: AudioChapter, chapterCount: Int) = AudioTags(
        title = chapter.fileStem(chapterCount),
        track = chapter.number,
        trackTotal = chapterCount,
        book = this,
    )
}

/** A file that can be read and written anywhere: an open "rw" descriptor on a phone, a RandomAccessFile in tests. */
interface SeekableFile {
    fun size(): Long

    fun read(position: Long, length: Int): ByteArray

    fun write(position: Long, bytes: ByteArray)

    fun truncate(size: Long)
}

/**
 * Adds `moov/udta/meta/ilst` to an MP4 file. Only the `moov` box changes; the audio (`mdat`) never moves, so no
 * sample offset (`stco`) needs fixing whatever order MediaMuxer wrote the boxes in:
 * - `moov` last in the file: rewritten in place, the file grows.
 * - `moov` followed by `free` padding with room: rewritten in place, the rest stays `free`.
 * - otherwise: the old `moov` becomes `free` and the new one goes at the end of the file.
 *
 * Any existing `udta` keeps its other children; its `meta` is replaced. A file with a 64-bit `moov` (never
 * written by MediaMuxer) is refused rather than guessed at.
 */
object Mp4Tags {
    fun write(file: SeekableFile, tags: AudioTags) {
        val boxes = topLevelBoxes(file)
        val index = boxes.indexOfFirst { it.type == MOOV }
        if (index < 0) broken("No moov box")
        val moov = boxes[index]
        if (moov.headerSize != HEADER) broken("64-bit moov box")
        val oldMoov = file.read(moov.offset, moov.size.toInt())
        val newMoov = withTags(oldMoov, tags)
        val next = boxes.getOrNull(index + 1)
        // A size-0 box runs to the end of the file: anything appended after it would become part of it.
        val canAppend = !boxes.last().runsToEnd
        when {
            next == null -> {
                file.write(moov.offset, newMoov)
                file.truncate(moov.offset + newMoov.size)
            }

            next.type == FREE && fitsIn(newMoov.size.toLong(), moov.size + next.size) -> {
                val room = moov.size + next.size
                file.write(moov.offset, newMoov)
                if (room > newMoov.size) file.write(moov.offset + newMoov.size, freeHeader(room - newMoov.size))
            }

            !canAppend -> broken("The last box runs to the end of the file")

            else -> {
                val end = file.size()
                file.write(end, newMoov)
                file.write(moov.offset + TYPE_OFFSET, FREE.toByteArray(Charsets.US_ASCII))
            }
        }
    }

    /** The space left after the new box is either nothing or a whole `free` box, never a gap too small for one. */
    private fun fitsIn(size: Long, room: Long) = size == room || size + HEADER <= room

    private fun topLevelBoxes(file: SeekableFile): List<Box> {
        val total = file.size()
        val boxes = mutableListOf<Box>()
        var offset = 0L
        while (offset + HEADER <= total) {
            val header = ByteBuffer.wrap(file.read(offset, HEADER))
            val size32 = header.int.toLong() and UINT_MASK
            val type = typeOf(header)
            val (size, headerSize) = when (size32) {
                LARGE_SIZE -> ByteBuffer.wrap(file.read(offset + HEADER, Long.SIZE_BYTES)).long to LARGE_HEADER
                TO_END -> (total - offset) to HEADER
                else -> size32 to HEADER
            }
            if (size < headerSize || offset + size > total) broken("Broken $type box at $offset")
            boxes += Box(type, offset, size, headerSize, runsToEnd = size32 == TO_END)
            offset += size
        }
        return boxes
    }

    /** [moov] with its `udta/meta` replaced by one carrying [tags]. */
    private fun withTags(moov: ByteArray, tags: AudioTags): ByteArray {
        val children = children(moov, from = HEADER)
        val oldUdta = children.firstOrNull { it.type == UDTA }
        val udtaChildren = oldUdta?.let { udta ->
            children(moov, from = udta.offset.toInt() + HEADER, until = (udta.offset + udta.size).toInt())
                .filter { it.type != META }
                .map { moov.copyOfRange(it.offset.toInt(), (it.offset + it.size).toInt()) }
        }.orEmpty()
        val udta = mp4Box(UDTA, udtaChildren + Mp4Ilst.meta(tags))
        val kept = children.filter { it.type != UDTA }
            .map { moov.copyOfRange(it.offset.toInt(), (it.offset + it.size).toInt()) }
        return mp4Box(MOOV, kept + udta)
    }

    private fun children(bytes: ByteArray, from: Int, until: Int = bytes.size): List<Box> {
        val boxes = mutableListOf<Box>()
        var offset = from
        while (offset + HEADER <= until) {
            val header = ByteBuffer.wrap(bytes, offset, HEADER)
            val size = header.int.toLong() and UINT_MASK
            val type = typeOf(header)
            if (size < HEADER || offset + size > until) broken("Broken $type box inside moov")
            boxes += Box(type, offset.toLong(), size, HEADER)
            offset += size.toInt()
        }
        return boxes
    }

    private fun broken(reason: String): Nothing = throw IOException(reason)

    private fun freeHeader(size: Long): ByteArray =
        ByteBuffer.allocate(HEADER).putInt(size.toInt()).put(FREE.toByteArray(Charsets.US_ASCII)).array()

    private fun typeOf(header: ByteBuffer): String {
        val type = ByteArray(Int.SIZE_BYTES)
        header.get(type)
        return String(type, Charsets.ISO_8859_1)
    }

    private data class Box(
        val type: String,
        val offset: Long,
        val size: Long,
        val headerSize: Int,
        val runsToEnd: Boolean = false,
    )

    private const val HEADER = 8
    private const val LARGE_HEADER = 16
    private const val TYPE_OFFSET = 4
    private const val UINT_MASK = 0xFFFF_FFFFL
    private const val LARGE_SIZE = 1L
    private const val TO_END = 0L
    private const val MOOV = "moov"
    private const val FREE = "free"
    private const val UDTA = "udta"
    private const val META = "meta"
}
