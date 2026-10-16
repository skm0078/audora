// File: build-logic/convention/src/main/kotlin/BundledVoiceAssetsTask.kt
// Purpose: Puts the bundled English voice into :speech's assets at build time: downloads the SHA-256-pinned
//          sherpa-onnx archive named by speech/bundled-voices.json, unpacks only what the voice needs and writes
//          voice.properties for the app to read (ADR-014). Model binaries never enter git.

import groovy.json.JsonSlurper
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
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.file.RelativePath
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class BundledVoiceAssetsTask : DefaultTask() {
    /** speech/bundled-voices.json: the candidate voices, their pins, and which one is bundled. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val catalog: RegularFileProperty

    /** Downloaded archives, kept across builds and keyed by SHA-256 (under the Gradle user home). */
    @get:Internal
    abstract val downloadDirectory: DirectoryProperty

    /** Asset root; the voice lands in `voices/bundled/` under it. */
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Inject
    abstract val archives: ArchiveOperations

    @get:Inject
    abstract val fileSystem: FileSystemOperations

    init {
        group = "build"
        description = "Downloads, verifies and unpacks the bundled voice into generated assets."
    }

    @TaskAction
    fun unpack() {
        val voice = selectedVoice()
        val archive = downloadVerified(voice)
        val target = outputDirectory.get().dir(ASSET_DIR).asFile
        fileSystem.delete { delete(outputDirectory) }

        val keepDicts = voice.espeakDictionaries.map { "${it}_dict" }.toSet()
        val wanted = listOfNotNull(voice.model, voice.tokens, voice.voices) + ATTRIBUTION_FILES
        fileSystem.copy {
            from(archives.tarTree(archives.bzip2(archive)))
            include(wanted.map { "${voice.archive}/$it" })
            if (voice.dataDir != null) include("${voice.archive}/${voice.dataDir}/**")
            // espeak-ng loads only the dictionary of the language it speaks; the others are ~16 MB of APK.
            exclude { it.name.endsWith(DICT_SUFFIX) && it.name !in keepDicts }
            eachFile { relativePath = RelativePath.parse(true, relativePath.segments.drop(1).joinToString("/")) }
            includeEmptyDirs = false
            into(target)
        }

        val missing = (listOfNotNull(voice.model, voice.tokens, voice.voices, voice.dataDir))
            .filterNot { File(target, it).exists() }
        if (missing.isNotEmpty()) throw GradleException("${voice.archive} lacks $missing; check bundled-voices.json")
        File(target, MANIFEST).writeText(voice.manifest())
        logger.lifecycle("Bundled voice ${voice.id} from ${voice.archive} (${target.walk().count()} files)")
    }

    private fun selectedVoice(): VoiceEntry {
        @Suppress("UNCHECKED_CAST")
        val root = JsonSlurper().parse(catalog.get().asFile) as Map<String, Any?>
        val selected = root.string("bundled")
        val dictionaries = (root["espeakDictionaries"] as? List<*>).orEmpty().map { it.toString() }
        val baseUrl = root.string("baseUrl")
        val entries = (root["voices"] as? List<*>).orEmpty().filterIsInstance<Map<String, Any?>>()
        val entry = entries.firstOrNull { it["id"] == selected }
            ?: throw GradleException("bundled-voices.json: bundled voice '$selected' is not in voices")
        return VoiceEntry(
            id = selected,
            label = entry.string("label"),
            lang = entry.string("lang"),
            family = entry.string("family"),
            archive = entry.string("archive"),
            url = "$baseUrl${entry.string("archive")}.tar.bz2",
            sha256 = entry.string("sha256").lowercase(),
            model = entry.string("model"),
            tokens = entry["tokens"]?.toString() ?: DEFAULT_TOKENS,
            voices = entry["voices"]?.toString(),
            dataDir = entry["dataDir"]?.toString(),
            speakerId = (entry["speakerId"] as? Number)?.toInt() ?: 0,
            licence = entry.string("licence"),
            credit = entry.string("credit"),
            licenceUrl = entry.string("licenceUrl"),
            sourceUrl = entry.string("sourceUrl"),
            espeakDictionaries = dictionaries,
        )
    }

    private fun downloadVerified(voice: VoiceEntry): File {
        val cached = downloadDirectory.get().file("${voice.sha256}.tar.bz2").asFile
        if (cached.isFile && sha256(cached) == voice.sha256) return cached
        cached.parentFile.mkdirs()
        val partial = File(cached.path + ".partial")
        var lastError: IOException? = null
        for (attempt in 1..DOWNLOAD_ATTEMPTS) {
            lastError = tryDownload(voice.url, partial, attempt)
            if (lastError == null) break
            Thread.sleep(RETRY_BASE_MS * attempt)
        }
        if (lastError != null) throw GradleException("Could not download ${voice.url}", lastError)
        val actual = sha256(partial)
        if (actual != voice.sha256 || !partial.renameTo(cached)) {
            partial.delete()
            throw GradleException("${voice.url} has SHA-256 $actual, expected ${voice.sha256} (or could not be cached)")
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

    private fun Map<String, Any?>.string(key: String): String = this[key]?.toString()?.takeIf { it.isNotBlank() }
        ?: throw GradleException("bundled-voices.json: '$key' missing in $this")

    private data class VoiceEntry(
        val id: String,
        val label: String,
        val lang: String,
        val family: String,
        val archive: String,
        val url: String,
        val sha256: String,
        val model: String,
        val tokens: String,
        val voices: String?,
        val dataDir: String?,
        val speakerId: Int,
        val licence: String,
        // Shown on the licences screen (P3-06, ADR-030).
        val credit: String,
        val licenceUrl: String,
        val sourceUrl: String,
        val espeakDictionaries: List<String>,
    ) {
        /** voice.properties, read by :speech's SherpaVoiceManifest. Written by hand: Properties.store adds a date. */
        fun manifest(): String {
            val values = sortedMapOf(
                "id" to id,
                "label" to label,
                "lang" to lang,
                "family" to family,
                "model" to model,
                "tokens" to tokens,
                "voices" to voices,
                "dataDir" to dataDir,
                "speakerId" to speakerId.toString(),
                "licence" to licence,
                "credit" to credit,
                "licenceUrl" to licenceUrl,
                "sourceUrl" to sourceUrl,
                "contentVersion" to "$sha256+dicts=${espeakDictionaries.sorted().joinToString(",")}",
            )
            return values.entries.filter { it.value != null }.joinToString("\n", postfix = "\n") { (key, value) ->
                "$key=" + requireNotNull(value).replace("\\", "\\\\").replace("\n", "\\n")
            }
        }
    }

    private companion object {
        const val ASSET_DIR = "voices/bundled"
        const val MANIFEST = "voice.properties"
        const val DEFAULT_TOKENS = "tokens.txt"
        const val DICT_SUFFIX = "_dict"
        const val DOWNLOAD_ATTEMPTS = 4
        const val RETRY_BASE_MS = 2_000L
        const val BUFFER_BYTES = 1 shl 16
        val ATTRIBUTION_FILES = listOf("MODEL_CARD", "LICENSE", "README.md")
    }
}
