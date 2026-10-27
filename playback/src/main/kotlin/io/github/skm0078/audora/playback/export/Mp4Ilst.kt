// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/export/Mp4Ilst.kt
// Purpose: Builds the iTunes-style `meta` box (hdlr + ilst) that audiobook players read an .m4a's tags from.

package io.github.skm0078.audora.playback.export

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

internal object Mp4Ilst {
    /** `meta` (a full box: version and flags first) holding `hdlr` and one `ilst` item per tag that has a value. */
    fun meta(tags: AudioTags): ByteArray {
        val items = buildList {
            add(text(NAME, tags.title))
            add(text(ALBUM, tags.album))
            tags.artist?.takeIf { it.isNotBlank() }?.let { artist ->
                add(text(ARTIST, artist))
                add(text(ALBUM_ARTIST, artist))
            }
            add(text(GENRE, tags.genre))
            add(item(TRACK, DATA_IMPLICIT, trackNumber(tags.track, tags.trackTotal)))
            tags.coverJpeg?.let { add(item(COVER, DATA_JPEG, it)) }
        }
        val versionAndFlags = ByteArray(Int.SIZE_BYTES)
        return mp4Box(META, listOf(versionAndFlags, handler(), mp4Box(ILST, items)))
    }

    private fun text(type: String, value: String) = item(type, DATA_UTF8, value.toByteArray(Charsets.UTF_8))

    /** One tag: a box named after it holding a `data` box (type, locale, value). */
    private fun item(type: String, dataType: Int, payload: ByteArray): ByteArray {
        val data = ByteBuffer.allocate(DATA_PREFIX + payload.size).putInt(dataType).putInt(0).put(payload).array()
        return mp4Box(type, listOf(mp4Box(DATA, listOf(data))))
    }

    /** `trkn` value: 2 reserved bytes, the track, the total, 2 reserved bytes. */
    private fun trackNumber(track: Int, total: Int): ByteArray = ByteBuffer.allocate(TRACK_BYTES)
        .putShort(ZERO_SHORT).putShort(track.toShort()).putShort(total.toShort()).putShort(ZERO_SHORT).array()

    /** `hdlr` for iTunes metadata: handler `mdir`, manufacturer `appl`, empty name. Players skip `ilst` without it. */
    private fun handler(): ByteArray = ByteBuffer.allocate(HANDLER_SIZE)
        .putInt(HANDLER_SIZE).put(ascii("hdlr"))
        .putInt(0).putInt(0).put(ascii("mdir")).put(ascii("appl"))
        .putInt(0).putInt(0).put(ZERO_BYTE)
        .array()

    private fun ascii(type: String) = type.toByteArray(Charsets.US_ASCII)

    private const val HANDLER_SIZE = 33
    private const val DATA_PREFIX = 8
    private const val TRACK_BYTES = 8
    private const val ZERO_SHORT: Short = 0
    private const val ZERO_BYTE: Byte = 0
    private const val DATA_IMPLICIT = 0
    private const val DATA_UTF8 = 1
    private const val DATA_JPEG = 13

    private const val META = "meta"
    private const val ILST = "ilst"
    private const val DATA = "data"
    private const val NAME = "©nam"
    private const val ALBUM = "©alb"
    private const val ARTIST = "©ART"
    private const val ALBUM_ARTIST = "aART"
    private const val GENRE = "©gen"
    private const val TRACK = "trkn"
    private const val COVER = "covr"
}

/** A box: 32-bit size, four-letter type (ISO-8859-1, so `©` is the single byte 0xA9), then [parts] in order. */
internal fun mp4Box(type: String, parts: List<ByteArray>): ByteArray {
    val size = MP4_HEADER + parts.sumOf { it.size }
    val out = ByteArrayOutputStream(size)
    out.write(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(size).array())
    out.write(type.toByteArray(Charsets.ISO_8859_1))
    parts.forEach(out::write)
    return out.toByteArray()
}

private const val MP4_HEADER = 8
