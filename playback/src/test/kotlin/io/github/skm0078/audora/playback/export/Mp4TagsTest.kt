// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/export/Mp4TagsTest.kt
// Purpose: Tags land in moov/udta/meta/ilst for every box order MediaMuxer writes, and the audio never moves.

package io.github.skm0078.audora.playback.export

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import kotlin.io.path.createTempDirectory
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Mp4TagsTest {
    private val folder = createTempDirectory("mp4tags").toFile()

    @After
    fun cleanUp() {
        folder.deleteRecursively()
    }

    @Test
    fun aMoovAtTheEndIsRewrittenInPlace() {
        val file = mp4(ftyp(), mdat(), moov())

        val audioAt = mdatOffset(file)

        tag(file)

        assertEquals(listOf("ftyp", "mdat", "moov"), topLevel(file.readBytes()).map { it.first })
        assertTags(file)
        assertAudioUnmoved(file, audioAt)
    }

    @Test
    fun aMoovBeforeRoomyPaddingKeepsTheRestAsFree() {
        val file = mp4(ftyp(), moov(), box("free", ByteArray(ROOMY)), mdat())
        val sizeBefore = file.length()
        val audioAt = mdatOffset(file)

        tag(file)

        assertEquals(listOf("ftyp", "moov", "free", "mdat"), topLevel(file.readBytes()).map { it.first })
        assertEquals(sizeBefore, file.length())
        assertTags(file)
        assertAudioUnmoved(file, audioAt)
    }

    @Test
    fun aMoovThatOutgrowsItsPaddingMovesToTheEnd() {
        val file = mp4(ftyp(), moov(), box("free", ByteArray(TIGHT)), mdat())
        val audioAt = mdatOffset(file)

        tag(file)

        assertEquals(listOf("ftyp", "free", "free", "mdat", "moov"), topLevel(file.readBytes()).map { it.first })
        assertTags(file)
        assertAudioUnmoved(file, audioAt)
    }

    @Test
    fun anExistingUdtaKeepsItsOtherChildrenAndLosesItsOldMeta() {
        val location = box("©xyz", "+00.0000+000.0000/".toByteArray())
        val oldMeta = box("meta", ByteArray(OLD_META))
        val file = mp4(ftyp(), mdat(), moov(box("udta", location + oldMeta)))

        tag(file)

        val udta = child(child(file.readBytes(), "moov", skip = 0), "udta")
        assertEquals(listOf("©xyz", "meta"), children(udta, HEADER).map { it.first })
        assertTags(file)
    }

    @Test
    fun noArtistAndNoCoverLeaveThoseItemsOut() {
        val file = mp4(ftyp(), mdat(), moov())

        tag(file, tags(artist = null, cover = null))

        val items = ilst(file.readBytes())
        assertNull(items["©ART"])
        assertNull(items["covr"])
        assertEquals("01 Pages 1-10", String(items.getValue("©nam"), Charsets.UTF_8))
    }

    @Test(expected = IOException::class)
    fun aFileWithoutMoovIsRefused() {
        tag(mp4(ftyp(), mdat()))
    }

    private fun tag(file: File, tags: AudioTags = TAGS) {
        RandomAccessFile(file, "rw").use { Mp4Tags.write(RandomAccessSeekable(it), tags) }
    }

    private fun assertTags(file: File) {
        val items = ilst(file.readBytes())
        assertEquals("01 Pages 1-10", String(items.getValue("©nam"), Charsets.UTF_8))
        assertEquals("বইয়ের নাম", String(items.getValue("©alb"), Charsets.UTF_8))
        assertEquals("লেখক", String(items.getValue("©ART"), Charsets.UTF_8))
        assertEquals("Audiobook", String(items.getValue("©gen"), Charsets.UTF_8))
        val track = ByteBuffer.wrap(items.getValue("trkn"))
        assertEquals(listOf<Short>(0, 1, 12, 0), List(4) { track.short })
        assertArrayEquals(COVER, items.getValue("covr"))
        // A data box's type says how to read its value: UTF-8 text is 1, a JPEG cover 13.
        assertEquals(DATA_JPEG, dataType(file.readBytes(), "covr"))
    }

    /** The audio's bytes and position, which the sample offsets in stco point at, are as before tagging. */
    private fun assertAudioUnmoved(file: File, offsetBefore: Int) {
        val bytes = file.readBytes()
        val mdat = mdatOffset(file)
        assertEquals(offsetBefore, mdat)
        assertArrayEquals(AUDIO, bytes.copyOfRange(mdat + HEADER, mdat + HEADER + AUDIO.size))
    }

    private fun mdatOffset(file: File) = topLevel(file.readBytes()).single { it.first == "mdat" }.second

    private fun mp4(vararg boxes: ByteArray): File = File(folder, "chapter.m4a").apply {
        writeBytes(boxes.fold(ByteArray(0)) { all, box -> all + box })
    }

    private fun ftyp() = box("ftyp", "M4A ".toByteArray() + ByteArray(4) + "isomM4A ".toByteArray())

    private fun mdat() = box("mdat", AUDIO)

    private fun moov(vararg extra: ByteArray) = box("moov", extra.fold(box("mvhd", ByteArray(MVHD))) { a, b -> a + b })

    private fun box(type: String, payload: ByteArray): ByteArray = ByteBuffer.allocate(HEADER + payload.size)
        .putInt(HEADER + payload.size).put(type.toByteArray(Charsets.ISO_8859_1)).put(payload).array()

    private fun topLevel(bytes: ByteArray) = children(bytes, 0).map { it.first to it.second }

    /** (type, offset, size) of each box from [from] to the end of [bytes]. */
    private fun children(bytes: ByteArray, from: Int): List<Triple<String, Int, Int>> {
        val out = mutableListOf<Triple<String, Int, Int>>()
        var offset = from
        while (offset + HEADER <= bytes.size) {
            val size = ByteBuffer.wrap(bytes, offset, 4).int
            out += Triple(String(bytes, offset + 4, 4, Charsets.ISO_8859_1), offset, size)
            offset += size
        }
        return out
    }

    /** The whole box of [type] directly inside [parent] (a whole box, header included). */
    private fun child(parent: ByteArray, type: String, skip: Int = HEADER): ByteArray {
        val (_, offset, size) = children(parent, skip).first { it.first == type }
        return parent.copyOfRange(offset, offset + size)
    }

    private fun ilstBox(bytes: ByteArray): ByteArray {
        val udta = child(child(bytes, "moov", skip = 0), "udta")
        val meta = child(udta, "meta")
        return child(meta, "ilst", skip = HEADER + 4)
    }

    /** Each ilst item's value: its `data` box after the type and locale. */
    private fun ilst(bytes: ByteArray): Map<String, ByteArray> {
        val ilst = ilstBox(bytes)
        return children(ilst, HEADER).associate { (type, offset, size) ->
            val data = child(ilst.copyOfRange(offset, offset + size), "data")
            type to data.copyOfRange(HEADER + DATA_PREFIX, data.size)
        }
    }

    private fun dataType(bytes: ByteArray, item: String): Int {
        val ilst = ilstBox(bytes)
        val (_, offset, size) = children(ilst, HEADER).first { it.first == item }
        return ByteBuffer.wrap(child(ilst.copyOfRange(offset, offset + size), "data"), HEADER, 4).int
    }

    private class RandomAccessSeekable(private val file: RandomAccessFile) : SeekableFile {
        override fun size() = file.length()

        override fun read(position: Long, length: Int) = ByteArray(length).also {
            file.seek(position)
            file.readFully(it)
        }

        override fun write(position: Long, bytes: ByteArray) {
            file.seek(position)
            file.write(bytes)
        }

        override fun truncate(size: Long) = file.setLength(size)
    }

    private companion object {
        const val HEADER = 8
        const val MVHD = 100
        const val ROOMY = 4_000
        const val TIGHT = 16
        const val OLD_META = 24
        const val DATA_JPEG = 13
        const val DATA_PREFIX = 8
        val AUDIO = ByteArray(2_000) { (it % 251).toByte() }
        val COVER = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3, 0xFF.toByte(), 0xD9.toByte())
        val TAGS = tags()

        fun tags(artist: String? = "লেখক", cover: ByteArray? = COVER) = AudioTags(
            title = "01 Pages 1-10",
            album = "বইয়ের নাম",
            artist = artist,
            track = 1,
            trackTotal = 12,
            genre = "Audiobook",
            coverJpeg = cover,
        )
    }
}
