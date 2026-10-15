// File: spikes/voices/src/main/kotlin/io/github/skm0078/audora/spike/voices/download/ModelStore.kt
// Purpose: Downloads, verifies (SHA-256) and unpacks the S1 voice archives from the s1-voices release into
//          app storage. An archive is only marked installed after its hash matched and it unpacked completely.

package io.github.skm0078.audora.spike.voices.download

import io.github.skm0078.audora.core.logging.AudoraLog
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI

data class DownloadConfig(
    val attempts: Int = DEFAULT_ATTEMPTS,
    val connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
    val readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS,
    val bufferBytes: Int = DEFAULT_BUFFER_BYTES,
) {
    companion object {
        const val DEFAULT_ATTEMPTS = 3
        const val DEFAULT_CONNECT_TIMEOUT_MS = 15_000
        const val DEFAULT_READ_TIMEOUT_MS = 60_000
        const val DEFAULT_BUFFER_BYTES = 64 * 1024
    }
}

/** Network access, behind an interface so the install logic is testable without a server. */
interface HttpFetcher {
    fun fetchText(url: String): String

    /** Streams [url] into [target], reporting (bytesSoFar, totalOrMinusOne). */
    fun download(url: String, target: File, onBytes: (Long, Long) -> Unit)
}

class UrlHttpFetcher(private val config: DownloadConfig = DownloadConfig()) : HttpFetcher {
    override fun fetchText(url: String): String = open(url).let { conn ->
        try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    override fun download(url: String, target: File, onBytes: (Long, Long) -> Unit) {
        val conn = open(url)
        try {
            val total = conn.contentLengthLong
            conn.inputStream.use { input -> target.outputStream().use { copy(input, it, total, onBytes) } }
        } finally {
            conn.disconnect()
        }
    }

    private fun copy(input: InputStream, output: OutputStream, total: Long, onBytes: (Long, Long) -> Unit) {
        val buffer = ByteArray(config.bufferBytes)
        var soFar = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            soFar += read
            onBytes(soFar, total)
        }
    }

    private fun open(url: String): HttpURLConnection {
        // GitHub release downloads redirect https → https to its asset host; HttpURLConnection follows that.
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        conn.connectTimeout = config.connectTimeoutMs
        conn.readTimeout = config.readTimeoutMs
        conn.instanceFollowRedirects = true
        val code = conn.responseCode
        if (code != HttpURLConnection.HTTP_OK) {
            conn.disconnect()
            throw IOException("HTTP $code for $url")
        }
        return conn
    }
}

sealed interface InstallOutcome {
    val archive: String

    data class Installed(override val archive: String, val bytes: Long) : InstallOutcome

    data class AlreadyInstalled(override val archive: String) : InstallOutcome

    data class Failed(override val archive: String, val reason: String) : InstallOutcome
}

data class InstallProgress(val archive: String, val index: Int, val count: Int, val bytes: Long, val total: Long)

class ModelStore(
    private val modelsRoot: File,
    private val baseUrl: String,
    private val http: HttpFetcher,
    private val config: DownloadConfig = DownloadConfig(),
) {
    fun modelDir(archive: String): File = File(modelsRoot, archive)

    fun isInstalled(archive: String): Boolean = File(modelDir(archive), INSTALLED_MARKER).isFile

    fun installedBytes(): Long = modelsRoot.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun deleteAll(): Boolean = modelsRoot.deleteRecursively().also { modelsRoot.mkdirs() }

    /** Installs every archive not yet installed. Never throws for one bad archive; each gets its own outcome. */
    fun install(
        archives: List<String>,
        isCancelled: () -> Boolean,
        onProgress: (InstallProgress) -> Unit,
    ): List<InstallOutcome> {
        modelsRoot.mkdirs()
        val (done, pending) = archives.partition(::isInstalled)
        val already = done.map { InstallOutcome.AlreadyInstalled(it) }
        val sums = if (pending.isEmpty()) emptyMap() else fetchSums()
        val outcomes = pending.mapIndexed { index, archive ->
            when {
                sums == null -> InstallOutcome.Failed(archive, "checksum list $SUMS_FILE unavailable")

                isCancelled() -> InstallOutcome.Failed(archive, "cancelled")

                sums["$archive.zip"] == null -> InstallOutcome.Failed(archive, "not listed in $SUMS_FILE")

                else -> installWithRetries(archive, sums.getValue("$archive.zip")) { bytes, total ->
                    onProgress(InstallProgress(archive, index + 1, pending.size, bytes, total))
                }
            }
        }
        return already + outcomes
    }

    private fun fetchSums(): Map<String, String>? = try {
        ModelArchives.parseSha256Sums(http.fetchText(baseUrl + SUMS_FILE))
    } catch (e: IOException) {
        AudoraLog.e(TAG, "Could not fetch $SUMS_FILE from $baseUrl", e)
        null
    }

    private fun installWithRetries(
        archive: String,
        expectedSha: String,
        onBytes: (Long, Long) -> Unit,
    ): InstallOutcome {
        var lastError = "no attempt made"
        for (attempt in 1..config.attempts) {
            try {
                val bytes = downloadVerifyUnpack(archive, expectedSha, onBytes)
                return InstallOutcome.Installed(archive, bytes)
            } catch (e: IOException) {
                lastError = e.message ?: e.javaClass.simpleName
                AudoraLog.w(TAG, "Install of $archive failed (attempt $attempt/${config.attempts})", e)
            }
        }
        return InstallOutcome.Failed(archive, lastError)
    }

    /** One attempt. Leaves no partial zip or staging directory behind, whatever happens. */
    private fun downloadVerifyUnpack(archive: String, expectedSha: String, onBytes: (Long, Long) -> Unit): Long {
        val zip = File(modelsRoot, "$archive.zip.part")
        val staging = File(modelsRoot, ".staging-$archive")
        try {
            http.download(baseUrl + "$archive.zip", zip, onBytes)
            val actual = ModelArchives.sha256(zip)
            ensure(actual == expectedSha) { "SHA-256 mismatch: expected $expectedSha, got $actual" }
            staging.deleteRecursively()
            val files = ModelArchives.unzip(zip, staging)
            val unpacked = File(staging, archive)
            ensure(unpacked.isDirectory) { "Archive has no top-level '$archive/' directory" }
            val target = modelDir(archive)
            target.deleteRecursively()
            ensure(unpacked.renameTo(target)) { "Could not move $unpacked to $target" }
            File(target, INSTALLED_MARKER).writeText(expectedSha)
            AudoraLog.i(TAG, "Installed $archive: $files files, ${zip.length() / BYTES_PER_MB} MB zip")
            return zip.length()
        } finally {
            zip.delete()
            staging.deleteRecursively()
        }
    }

    private inline fun ensure(condition: Boolean, message: () -> String) {
        if (!condition) throw IOException(message())
    }

    companion object {
        const val SUMS_FILE = "SHA256SUMS.txt"
        const val INSTALLED_MARKER = ".installed"
        private const val BYTES_PER_MB = 1_000_000
        private const val TAG = "S1.Models"
    }
}
