// File: build-logic/convention/src/main/kotlin/VoiceCatalogAssetsTask.kt
// Purpose: Validates speech/models.json and packs it into :speech's assets as one voice.properties-style file per
//          downloadable voice (P4-01, ADR-022), so the app parses it without a JSON library. The bundled voice is
//          left out: it is already in the APK.

import groovy.json.JsonSlurper
import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class VoiceCatalogAssetsTask : DefaultTask() {
    /** speech/models.json: the downloadable voices and their pins. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val catalog: RegularFileProperty

    /** speech/bundled-voices.json, read only for the id of the bundled voice. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val bundledCatalog: RegularFileProperty

    /** Asset root; entries land in `voices/catalog/` under it. */
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Inject
    abstract val fileSystem: FileSystemOperations

    init {
        group = "build"
        description = "Validates speech/models.json and writes the downloadable-voice catalog into generated assets."
    }

    @TaskAction
    fun write() {
        val bundledId = (JsonSlurper().parse(bundledCatalog.get().asFile) as Map<*, *>)["bundled"]?.toString()

        @Suppress("UNCHECKED_CAST")
        val root = JsonSlurper().parse(catalog.get().asFile) as Map<String, Any?>
        val baseUrl = root.text("baseUrl")
        if (!baseUrl.startsWith("https://") || !baseUrl.endsWith("/")) fail("baseUrl must be https and end with /")
        val entries = (root["voices"] as? List<*>).orEmpty().filterIsInstance<Map<String, Any?>>()
        val ids = entries.map { it.text("id") }
        if (ids.toSet().size != ids.size) fail("duplicate voice ids in $ids")

        fileSystem.delete { delete(outputDirectory) }
        val target = outputDirectory.get().dir(ASSET_DIR).asFile.apply { mkdirs() }
        val packed = entries.filter { it.text("id") != bundledId }
        packed.forEach { entry -> File(target, "${entry.text("id")}.properties").writeText(manifest(entry, baseUrl)) }
        logger.lifecycle("Voice catalog: ${packed.map { it.text("id") }} (bundled $bundledId left out)")
    }

    private fun manifest(entry: Map<String, Any?>, baseUrl: String): String {
        val id = entry.text("id")
        val sha256 = entry.text("sha256").lowercase()
        val size = (entry["sizeBytes"] as? Number)?.toLong() ?: 0L
        val file = entry.text("file")
        when {
            !ID.matches(id) -> fail("id '$id' must be lower-case letters, digits and dashes")
            id.startsWith(CUSTOM_PREFIX) -> fail("id '$id' must not start with $CUSTOM_PREFIX (imported voices)")
            !SHA256.matches(sha256) -> fail("$id: sha256 must be 64 hex characters")
            size <= 0 -> fail("$id: sizeBytes must be > 0")
            file != "$id-${sha256.take(PIN_PREFIX)}.zip" -> fail("$id: file must be $id-${sha256.take(PIN_PREFIX)}.zip")
            entry.text("lang") !in LANGS -> fail("$id: lang must be one of $LANGS")
            entry.text("family") !in FAMILIES -> fail("$id: family must be one of $FAMILIES")
            entry.text("family") == "kitten" && entry["voices"] == null -> fail("$id: a kitten voice needs voices")
        }
        val values = sortedMapOf(
            "id" to id,
            "label" to entry.text("label"),
            "lang" to entry.text("lang"),
            "family" to entry.text("family"),
            "model" to entry.text("model"),
            "tokens" to (entry["tokens"]?.toString() ?: DEFAULT_TOKENS),
            "voices" to entry["voices"]?.toString(),
            "dataDir" to entry["dataDir"]?.toString(),
            "speakerId" to ((entry["speakerId"] as? Number)?.toInt() ?: 0).toString(),
            "licence" to entry.text("licence"),
            "contentVersion" to sha256,
            "url" to "$baseUrl$file",
            "sha256" to sha256,
            "sizeBytes" to size.toString(),
        )
        // Written by hand: Properties.store adds a date, which would make the APK differ build to build.
        return values.entries.filter { it.value != null }.joinToString("\n", postfix = "\n") { (key, value) ->
            "$key=" + requireNotNull(value).replace("\\", "\\\\").replace("\n", "\\n")
        }
    }

    private fun Map<String, Any?>.text(key: String): String = this[key]?.toString()?.takeIf { it.isNotBlank() }
        ?: fail("'$key' missing in ${this["id"] ?: this}")

    private fun fail(message: String): Nothing = throw GradleException("speech/models.json: $message")

    private companion object {
        const val ASSET_DIR = "voices/catalog"
        const val DEFAULT_TOKENS = "tokens.txt"
        const val CUSTOM_PREFIX = "custom-"
        const val PIN_PREFIX = 12
        val ID = Regex("[a-z0-9]+(-[a-z0-9]+)*")
        val SHA256 = Regex("[0-9a-f]{64}")
        val LANGS = setOf("en", "hi", "bn")
        val FAMILIES = setOf("vits", "kitten")
    }
}
