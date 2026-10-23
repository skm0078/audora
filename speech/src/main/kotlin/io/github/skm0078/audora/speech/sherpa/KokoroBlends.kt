// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/sherpa/KokoroBlends.kt
// Purpose: Mixes two Kokoro speakers' styles into a new narrator voice (plan item 75), written as a voices file.

package io.github.skm0078.audora.speech.sherpa

import io.github.skm0078.audora.core.result.Outcome
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** The voice's own style with [amount] (0–1, both ends excluded) of speaker [withSpeaker]'s mixed in. */
data class StyleBlend(val withSpeaker: Int, val amount: Float) {
    init {
        require(withSpeaker >= 0) { "withSpeaker must be >= 0, was $withSpeaker" }
        require(amount > 0f && amount < 1f) { "amount must be in (0, 1), was $amount" }
    }
}

/**
 * A Kokoro voice is a style tensor per speaker in the model's voices file: little-endian float32, one row of
 * [STYLE_ROWS] × [STYLE_DIM] per speaker, speakers back to back (Kokoro v1.0 as sherpa-onnx packages it). A blend is
 * a weighted mean of two speakers' rows, the way Kokoro's own tools mix voices. It is written over the voice's own
 * row in a copy of the file, so the file keeps its size and speaker count (sherpa-onnx checks both against the
 * model) and every other speaker, the character voices among them (plan item 63), stays as it was.
 *
 * The copy is as big as the original (about 28 MB for Kokoro v1.0); [BlendCache] keeps one per voice, rebuilt when
 * missing (it lives in a cache), so a blend costs that disk and one copy when it is chosen, and no extra memory.
 */
object KokoroBlends {
    const val STYLE_ROWS = 510
    const val STYLE_DIM = 256
    private const val FLOAT_BYTES = 4
    const val SPEAKER_BYTES = STYLE_ROWS * STYLE_DIM * FLOAT_BYTES
    private const val TEMP_SUFFIX = ".tmp"

    /** [target]: [source] with speaker [own]'s row blended; reused when it is already there. */
    fun write(source: File, target: File, own: Int, blend: StyleBlend): Outcome<File, String> {
        val size = source.length()
        val speakers = size / SPEAKER_BYTES
        return when {
            size <= 0L || size % SPEAKER_BYTES != 0L -> Outcome.Failure("${source.name} is not a Kokoro voices file")

            own >= speakers || blend.withSpeaker >= speakers ->
                Outcome.Failure("speaker $own or ${blend.withSpeaker} is past the model's $speakers")

            target.isFile && target.length() == size -> Outcome.Success(target)

            else -> copyBlended(source, target, own, blend)
        }
    }

    private fun copyBlended(source: File, target: File, speaker: Int, blend: StyleBlend): Outcome<File, String> {
        var temp: File? = null
        return try {
            val folder = target.absoluteFile.parentFile ?: throw IOException("no folder for ${target.name}")
            folder.mkdirs()
            // A temp file of this build's own: another build of the same copy never shares or renames it.
            val own = File.createTempFile(target.name, TEMP_SUFFIX, folder).also { temp = it }
            source.copyTo(own, overwrite = true)
            RandomAccessFile(own, "rw").use { file ->
                val mine = read(file, speaker)
                val other = read(file, blend.withSpeaker)
                val mixed = ByteBuffer.allocate(SPEAKER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
                while (mine.hasRemaining()) {
                    mixed.putFloat(mine.getFloat() * (1f - blend.amount) + other.getFloat() * blend.amount)
                }
                mixed.flip()
                val at = speaker.toLong() * SPEAKER_BYTES
                // A short write is possible on a nearly full disk: write until the whole row is down.
                while (mixed.hasRemaining()) file.channel.write(mixed, at + mixed.position())
                file.fd.sync()
            }
            if (!own.renameTo(target)) throw IOException("cannot replace ${target.name}")
            Outcome.Success(target)
        } catch (e: IOException) {
            temp?.delete()
            Outcome.Failure("blend not written: ${e.message}")
        }
    }

    private fun read(file: RandomAccessFile, speaker: Int): ByteBuffer {
        val row = ByteBuffer.allocate(SPEAKER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        val start = speaker.toLong() * SPEAKER_BYTES
        while (row.hasRemaining()) {
            if (file.channel.read(row, start + row.position()) < 0) throw IOException("voices file ends early")
        }
        row.flip()
        return row
    }
}
