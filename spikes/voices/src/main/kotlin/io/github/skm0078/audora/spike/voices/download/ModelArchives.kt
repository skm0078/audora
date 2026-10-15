// File: spikes/voices/src/main/kotlin/io/github/skm0078/audora/spike/voices/download/ModelArchives.kt
// Purpose: Pure helpers for model downloads: SHA256SUMS parsing, hashing, and zip extraction that cannot escape
//          its target directory. Plain JVM code, unit-tested; P4-01's model manager can start from it.

package io.github.skm0078.audora.spike.voices.download

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object ModelArchives {
    private const val SHA256_HEX_LENGTH = 64
    private const val BUFFER_BYTES = 64 * 1024

    /** Parses `sha256sum` output ("<hex>  <name>" or "<hex> *<name>") into name → lowercase hex. */
    fun parseSha256Sums(text: String): Map<String, String> = text.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .associate { line ->
            val parts = line.split(Regex("\\s+"), limit = 2)
            require(parts.size == 2 && parts[0].length == SHA256_HEX_LENGTH) { "Malformed SHA256SUMS line: $line" }
            parts[1].removePrefix("*") to parts[0].lowercase()
        }

    fun sha256(file: File): String = file.inputStream().use(::sha256)

    fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_BYTES)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Extracts [zip] into [targetDir]. Rejects any entry that would land outside it ("zip slip"), so a bad
     * archive cannot overwrite app files. Returns the number of files written.
     */
    fun unzip(zip: File, targetDir: File): Int {
        val root = targetDir.canonicalFile
        var files = 0
        ZipInputStream(zip.inputStream().buffered()).use { stream ->
            generateSequence { stream.nextEntry }.forEach { entry ->
                if (extractEntry(stream, entry, root)) files++
            }
        }
        return files
    }

    /** Writes one entry; returns true if it was a file. */
    private fun extractEntry(stream: ZipInputStream, entry: ZipEntry, root: File): Boolean {
        val out = resolveInside(root, entry.name)
        if (entry.isDirectory) {
            out.mkdirs()
            return false
        }
        out.parentFile?.mkdirs()
        out.outputStream().use { stream.copyTo(it, BUFFER_BYTES) }
        return true
    }

    /** The file [entryName] maps to under [root]; throws if it resolves outside [root]. */
    fun resolveInside(root: File, entryName: String): File {
        val canonicalRoot = root.canonicalFile
        val candidate = File(canonicalRoot, entryName).canonicalFile
        val inside = candidate.path == canonicalRoot.path ||
            candidate.path.startsWith(canonicalRoot.path + File.separator)
        if (!inside) throw IOException("Zip entry escapes the target directory: $entryName")
        return candidate
    }
}
