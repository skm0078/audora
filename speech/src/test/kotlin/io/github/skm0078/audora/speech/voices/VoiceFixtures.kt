// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/voices/VoiceFixtures.kt
// Purpose: Test doubles for the voice manager: a scripted Range-aware HTTP server, voice zips, catalog voices.

package io.github.skm0078.audora.speech.voices

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.speech.sherpa.SherpaFamily
import io.github.skm0078.audora.speech.sherpa.SherpaVoiceSpec
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Serves [content] like a CDN: 206 with `Content-Range` for a Range request, 200 otherwise. Knobs simulate a dropped
 * connection, a server that ignores Range, an error status, or a file that changed on the server.
 */
class FakeHttpSource(var content: ByteArray) : HttpSource {
    val offsets = mutableListOf<Long>()

    /** The connection drops after this many body bytes of the next response (then resets to null). */
    var dropAfter: Int? = null
    var ignoreRange = false
    var status: Int? = null
    var connectFailure: IOException? = null

    override fun open(url: String, offset: Long): HttpResponse {
        offsets += offset
        connectFailure?.let { throw it }
        val code = status
        return when {
            code != null -> HttpResponse(code, null, ByteArrayInputStream(ByteArray(0)))

            offset > 0 && offset >= content.size ->
                HttpResponse(HttpResponse.RANGE_NOT_SATISFIABLE, null, ByteArrayInputStream(ByteArray(0)))

            else -> serve(if (offset > 0 && !ignoreRange) offset.toInt() else 0)
        }
    }

    private fun serve(start: Int): HttpResponse {
        val body = content.copyOfRange(start, content.size)
        val stream: InputStream = dropAfter?.let { limit ->
            dropAfter = null
            DroppingStream(body, limit)
        } ?: ByteArrayInputStream(body)
        return if (start > 0) {
            HttpResponse(HttpResponse.PARTIAL_CONTENT, "bytes $start-${content.size - 1}/${content.size}", stream)
        } else {
            HttpResponse(HttpResponse.OK, null, stream)
        }
    }

    private class DroppingStream(bytes: ByteArray, private val limit: Int) : InputStream() {
        private val source = ByteArrayInputStream(bytes)
        private var served = 0

        override fun read(): Int {
            if (served >= limit) throw IOException("connection reset")
            served++
            return source.read()
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (served >= limit) throw IOException("connection reset")
            val read = source.read(b, off, minOf(len, limit - served))
            if (read > 0) served += read
            return read
        }
    }
}

fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

fun voiceSpec(
    id: String = "piper-en-cori",
    lang: Lang = Lang.EN,
    family: SherpaFamily = SherpaFamily.VITS,
    contentVersion: String = "v1",
) = SherpaVoiceSpec(
    id = id,
    label = "Voice $id",
    lang = lang,
    family = family,
    model = "model.onnx",
    tokens = "tokens.txt",
    voices = "voices.bin".takeIf { family == SherpaFamily.KITTEN },
    dataDir = "espeak-ng-data",
    speakerId = 0,
    licence = "test licence",
    contentVersion = contentVersion,
)

/** A catalog voice whose pins match [bytes]. */
fun catalogVoice(bytes: ByteArray, id: String = "piper-en-cori", lang: Lang = Lang.EN): CatalogVoice {
    val sha = sha256Hex(bytes)
    return CatalogVoice(
        voiceSpec(id, lang, contentVersion = sha),
        "https://example.test/$id.zip",
        sha,
        bytes.size.toLong(),
    )
}

/** A zip holding [entries] (name to content), like the mirror's: flat, stored or deflated. */
fun zipOf(entries: Map<String, ByteArray>): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        entries.forEach { (name, data) ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(data)
            zip.closeEntry()
        }
    }
    return out.toByteArray()
}

/** The files a VITS voice from [voiceSpec] needs. */
fun voiceZipEntries(model: ByteArray = ByteArray(4096) { it.toByte() }): Map<String, ByteArray> = mapOf(
    "model.onnx" to model,
    "tokens.txt" to "a 1\n".toByteArray(),
    "espeak-ng-data/phontab" to ByteArray(16),
    "espeak-ng-data/en_dict" to ByteArray(8),
)

/** An ImportTree over a real folder, like the picker's tree. */
class FolderTree(private val root: File) : ImportTree {
    override val name: String get() = root.name

    override fun files(maxDepth: Int, limit: Int): List<ImportFile> = root.walkTopDown()
        .maxDepth(maxDepth + 1)
        .filter { it.isFile }
        .map { ImportFile(it.relativeTo(root).invariantSeparatorsPath, it.length()) }
        .take(limit)
        .toList()

    override fun open(path: String): InputStream = File(root, path).inputStream()
}

fun File.write(path: String, bytes: ByteArray = ByteArray(8)): File = File(this, path).apply {
    parentFile.mkdirs()
    writeBytes(bytes)
}
