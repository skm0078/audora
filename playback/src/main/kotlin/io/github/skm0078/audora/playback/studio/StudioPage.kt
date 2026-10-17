// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/studio/StudioPage.kt
// Purpose: One page of studio audio on disk: each sentence's rendered audio, losslessly packed, with a checksum.

package io.github.skm0078.audora.playback.studio

import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.speech.audio.PcmAudio
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.util.zip.CRC32

/**
 * One rendered sentence. [textKey] is [StudioKeys.unitKey] of the unit it was rendered from: a sentence whose text
 * or kind changed since (a new pipeline, a fixed page) no longer matches and is rendered live.
 */
class StudioClip(val index: Int, val textKey: Long, val sampleRate: Int, val sampleCount: Int, val data: ByteArray) {
    val durationMs: Long get() = sampleCount * MS_PER_SECOND / sampleRate

    fun matches(unit: ReadingUnit): Boolean = unit.index == index && StudioKeys.unitKey(unit) == textKey

    fun audio(codec: LosslessPcm): PcmAudio = PcmAudio(codec.decode(data, sampleCount), sampleRate)

    companion object {
        private const val MS_PER_SECOND = 1_000L

        fun of(unit: ReadingUnit, audio: PcmAudio, codec: LosslessPcm): StudioClip = StudioClip(
            unit.index,
            StudioKeys.unitKey(unit),
            audio.sampleRate,
            audio.samples.size,
            codec.encode(audio.samples),
        )
    }
}

/** A page's clips in reading order. The file layout is versioned; an older or foreign file reads as corrupt. */
class StudioPage(val page: Int, val clips: List<StudioClip>) {
    val durationMs: Long get() = clips.sumOf { it.durationMs }

    fun clipFor(unit: ReadingUnit): StudioClip? = clips.firstOrNull {
        it.index == unit.index
    }?.takeIf { it.matches(unit) }

    /** True when every unit of [plays] has its clip: the page needs no rendering. */
    fun covers(plays: List<ReadingUnit>): Boolean = plays.all { clipFor(it) != null }

    fun toBytes(): ByteArray {
        val body = ByteArrayOutputStream()
        DataOutputStream(body).use { out ->
            out.writeInt(MAGIC)
            out.writeByte(VERSION)
            out.writeInt(page)
            out.writeInt(clips.size)
            for (clip in clips) {
                out.writeInt(clip.index)
                out.writeLong(clip.textKey)
                out.writeInt(clip.sampleRate)
                out.writeInt(clip.sampleCount)
                out.writeInt(clip.data.size)
                out.write(clip.data)
            }
        }
        val bytes = body.toByteArray()
        val checksum = CRC32().apply { update(bytes) }.value
        val tail = ByteArrayOutputStream(CHECKSUM_BYTES)
        DataOutputStream(tail).use { it.writeLong(checksum) }
        return bytes + tail.toByteArray()
    }

    companion object {
        private const val MAGIC = 0x41535455 // "ASTU"
        private const val VERSION = 1
        private const val CHECKSUM_BYTES = Long.SIZE_BYTES

        /** Throws [CorruptStudioData] on a truncated, damaged or foreign file. */
        fun fromBytes(bytes: ByteArray): StudioPage {
            corruptUnless(bytes.size >= CHECKSUM_BYTES) { "file too short" }
            val bodySize = bytes.size - CHECKSUM_BYTES
            val stored = DataInputStream(ByteArrayInputStream(bytes, bodySize, CHECKSUM_BYTES)).readLong()
            val actual = CRC32().apply { update(bytes, 0, bodySize) }.value
            corruptUnless(stored == actual) { "checksum mismatch" }
            return try {
                DataInputStream(ByteArrayInputStream(bytes, 0, bodySize)).use(::readBody)
            } catch (e: EOFException) {
                throw CorruptStudioData("file ends early", e)
            }
        }

        private fun readBody(input: DataInputStream): StudioPage {
            corruptUnless(input.readInt() == MAGIC) { "not a studio page" }
            corruptUnless(input.readByte().toInt() == VERSION) { "unknown version" }
            val page = input.readInt()
            val count = input.readInt()
            corruptUnless(count >= 0) { "negative clip count" }
            val clips = List(count) {
                val index = input.readInt()
                val textKey = input.readLong()
                val sampleRate = input.readInt()
                val sampleCount = input.readInt()
                val size = input.readInt()
                corruptUnless(sampleRate > 0 && sampleCount >= 0) { "bad clip format" }
                corruptUnless(size in 0..input.available()) { "bad clip size" }
                StudioClip(index, textKey, sampleRate, sampleCount, ByteArray(size).also(input::readFully))
            }
            return StudioPage(page, clips)
        }
    }
}
