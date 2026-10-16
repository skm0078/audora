// File: build-logic/convention/src/main/kotlin/TessdataAssetsTask.kt
// Purpose: Puts the pinned Tesseract models into a test APK's assets at build time, so the device tests run the
//          real OCR without a model in git (P3-05, ADR-025). The app itself downloads them at run time.

import java.io.File
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import java.util.Properties
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class TessdataAssetsTask : DefaultTask() {
    /** speech/src/main/assets/ocr/tessdata.properties: the app's own pins, so tests read exactly what users get. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val catalog: RegularFileProperty

    /** Downloaded models, kept across builds and keyed by SHA-256 (under the Gradle user home). */
    @get:Internal
    abstract val downloadDirectory: DirectoryProperty

    /** Asset root; the models land in `tessdata/<code>.traineddata` under it. */
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    init {
        group = "build"
        description = "Downloads and verifies the pinned tessdata_best models into generated test assets."
    }

    @TaskAction
    fun copyModels() {
        val properties = Properties().apply { catalog.get().asFile.reader(Charsets.UTF_8).use { load(it) } }
        val baseUrl = properties.required("baseUrl")
        val target = outputDirectory.get().dir(ASSET_DIR).asFile
        target.deleteRecursively()
        target.mkdirs()
        for (lang in properties.required("langs").split(',').map { it.trim() }) {
            val file = properties.required("$lang.file")
            val sha256 = properties.required("$lang.sha256").lowercase()
            downloadVerified("$baseUrl$file", sha256).copyTo(File(target, file), overwrite = true)
        }
        logger.lifecycle("Tesseract models for tests: ${target.list()?.sorted()}")
    }

    private fun downloadVerified(url: String, sha256: String): File {
        val cached = downloadDirectory.get().file("$sha256.traineddata").asFile
        if (cached.isFile && sha256(cached) == sha256) return cached
        cached.parentFile.mkdirs()
        val partial = File(cached.path + ".partial")
        var lastError: IOException? = null
        for (attempt in 1..DOWNLOAD_ATTEMPTS) {
            lastError = tryDownload(url, partial, attempt)
            if (lastError == null) break
            Thread.sleep(RETRY_BASE_MS * attempt)
        }
        if (lastError != null) throw GradleException("Could not download $url", lastError)
        val actual = sha256(partial)
        if (actual != sha256 || !partial.renameTo(cached)) {
            partial.delete()
            throw GradleException("$url has SHA-256 $actual, expected $sha256 (or could not be cached)")
        }
        return cached
    }

    /** One download attempt; returns the error, or null on success. */
    private fun tryDownload(url: String, into: File, attempt: Int): IOException? = try {
        logger.lifecycle("Downloading $url (attempt $attempt)")
        URI(url).toURL().openStream().use { input -> into.outputStream().use { input.copyTo(it) } }
        null
    } catch (e: IOException) {
        into.delete()
        e
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun Properties.required(key: String): String = getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw GradleException("tessdata.properties: '$key' is missing")

    private companion object {
        const val ASSET_DIR = "tessdata"
        const val DOWNLOAD_ATTEMPTS = 3
        const val RETRY_BASE_MS = 2_000L
        const val BUFFER_BYTES = 1 shl 16
    }
}
