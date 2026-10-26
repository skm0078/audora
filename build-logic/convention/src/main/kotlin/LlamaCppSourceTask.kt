// File: build-logic/convention/src/main/kotlin/LlamaCppSourceTask.kt
// Purpose: Puts llama.cpp's source, for the story brain's native library (ADR story-brain), where :speech's CMake build
//          finds it: downloads the SHA-256-pinned release tarball once (cached in the Gradle user home) and unpacks it.

import java.io.File
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RelativePath
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class LlamaCppSourceTask : DefaultTask() {
    /** The llama.cpp release tag, e.g. b11887; the tarball is GitHub's archive of that tag. */
    @get:Input
    abstract val tag: Property<String>

    /** SHA-256 of the tarball, so a moved tag or a changed archive fails the build instead of building other code. */
    @get:Input
    abstract val sha256: Property<String>

    /** Downloaded tarballs, kept across builds and keyed by SHA-256 (under the Gradle user home). */
    @get:Internal
    abstract val downloadDirectory: DirectoryProperty

    /** The unpacked source: CMakeLists.txt sits at its top. */
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Inject
    abstract val archives: ArchiveOperations

    @get:Inject
    abstract val fileSystem: FileSystemOperations

    init {
        group = "build"
        description = "Downloads, verifies and unpacks llama.cpp's source for the story brain."
    }

    @TaskAction
    fun unpack() {
        val tarball = downloadVerified()
        fileSystem.delete { delete(outputDirectory) }
        fileSystem.copy {
            from(archives.tarTree(archives.gzip(tarball)))
            into(outputDirectory)
            // Drop the archive's top folder (llama.cpp-<tag>/), so the output is the source root.
            eachFile {
                val inside = relativePath.segments.drop(1).joinToString("/")
                relativePath = RelativePath.parse(true, inside)
            }
            includeEmptyDirs = false
        }
        if (!outputDirectory.get().file("CMakeLists.txt").asFile.isFile) {
            throw GradleException("llama.cpp ${tag.get()} unpacked without a CMakeLists.txt")
        }
    }

    private fun downloadVerified(): File {
        val expected = sha256.get()
        val cached = downloadDirectory.get().file("llama.cpp-$expected.tar.gz").asFile
        if (cached.isFile && sha256(cached) == expected) return cached
        cached.parentFile.mkdirs()
        val url = "https://github.com/ggml-org/llama.cpp/archive/refs/tags/${tag.get()}.tar.gz"
        val partial = File(cached.path + ".partial")
        var lastError: IOException? = null
        for (attempt in 1..DOWNLOAD_ATTEMPTS) {
            lastError = tryDownload(url, partial, attempt)
            if (lastError == null) break
            Thread.sleep(RETRY_BASE_MS * attempt)
        }
        if (lastError != null) throw GradleException("Could not download $url", lastError)
        val actual = sha256(partial)
        if (actual != expected || !partial.renameTo(cached)) {
            partial.delete()
            throw GradleException("$url has SHA-256 $actual, expected $expected (or could not be cached)")
        }
        return cached
    }

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

    private companion object {
        const val DOWNLOAD_ATTEMPTS = 3
        const val RETRY_BASE_MS = 2_000L
        const val BUFFER_BYTES = 1 shl 16
    }
}
