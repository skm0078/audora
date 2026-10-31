// File: build-logic/convention/src/main/kotlin/VoiceCatalogAssetsTask.kt
// Purpose: Validates speech/models.json and packs it into :speech's assets as one voice.properties-style file per
//          downloadable voice (P4-01, ADR-022), so the app parses it without a JSON library. The bundled voice is
//          left out: it is already in the APK. Each entry carries its credits for the licences screen and whether it
//          is its language's recommended voice (P3-06, ADR-030).

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
        val recommended = recommendedIds(root, packed)
        val beta = betaLangs(root)
        packed.forEach { entry ->
            val id = entry.text("id")
            val flags = Flags(recommended = id in recommended, beta = entry.text("lang") in beta)
            File(target, "$id.properties").writeText(manifest(entry, baseUrl, flags))
        }
        logger.lifecycle("Voice catalog: ${packed.map { it.text("id") }} (bundled $bundledId left out)")
    }

    /** `recommended` maps a language to its default download (one line to change after the listening test). */
    private fun recommendedIds(root: Map<String, Any?>, packed: List<Map<String, Any?>>): Set<String> {
        val byId = packed.associateBy { it.text("id") }
        val recommended = (root["recommended"] as? Map<*, *>).orEmpty()
        recommended.forEach { (lang, id) ->
            val entry = byId[id.toString()] ?: fail("recommended voice '$id' for $lang is not a downloadable voice")
            if (entry.text("lang") != lang.toString()) fail("recommended voice '$id' does not speak $lang")
        }
        return recommended.values.map { it.toString() }.toSet()
    }

    /**
     * `beta` lists the languages whose downloadable voices are still early (2026-10-31: Bengali). Every voice of such a
     * language is marked, so the Voices screen says so; graduating a language is deleting it from the list.
     */
    private fun betaLangs(root: Map<String, Any?>): Set<String> {
        val langs = (root["beta"] as? List<*>).orEmpty().map { it.toString() }
        langs.forEach { if (it !in LANGS) fail("beta language '$it' must be one of $LANGS") }
        return langs.toSet()
    }

    /** What the catalog's top-level lists say about one voice. */
    private data class Flags(val recommended: Boolean, val beta: Boolean)

    private fun manifest(entry: Map<String, Any?>, baseUrl: String, flags: Flags): String {
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
        }
        validateEngine(id, entry)
        CREDIT_LINKS.forEach { key -> if (!entry.text(key).startsWith("https://")) fail("$id: $key must be https") }
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
            "lexicon" to entry["lexicon"]?.toString(),
            "engineLang" to entry["engineLang"]?.toString(),
            "numSteps" to (entry["numSteps"] as? Number)?.toInt()?.toString(),
            "threads" to (entry["threads"] as? Number)?.toInt()?.toString(),
            "styles" to styles(id, entry),
            "licence" to entry.text("licence"),
            "credit" to entry.text("credit"),
            "licenceUrl" to entry.text("licenceUrl"),
            "sourceUrl" to entry.text("sourceUrl"),
            "useTerms" to useTerms(id, entry),
            "recommended" to flags.recommended.toString(),
            // Written only when true, so the other voices' assets stay byte-for-byte as they were.
            "beta" to flags.beta.takeIf { it }?.toString(),
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

    /** The sherpa family and the keys it needs (P4-02: Kokoro and Supertonic need a voices file and a language). */
    private fun validateEngine(id: String, entry: Map<String, Any?>) {
        val family = entry.text("family")
        when {
            family !in FAMILIES -> fail("$id: family must be one of $FAMILIES")

            family in NEEDS_VOICES && entry["voices"] == null -> fail("$id: a $family voice needs voices")

            family in NEEDS_ENGINE_LANG && entry["engineLang"] == null -> fail("$id: a $family voice needs engineLang")

            entry["numSteps"] != null && ((entry["numSteps"] as? Number)?.toInt() ?: 0) <= 0 ->
                fail("$id: numSteps must be a number > 0")

            entry["threads"] != null && ((entry["threads"] as? Number)?.toInt() ?: 0) <= 0 ->
                fail("$id: threads must be a number > 0")
        }
    }

    /** Use terms the user agrees to before downloading (a model licence with use restrictions, e.g. OpenRAIL-M). */
    private fun useTerms(id: String, entry: Map<String, Any?>): String? {
        val terms = entry["useTerms"]?.toString() ?: return null
        if (terms !in USE_TERMS) fail("$id: useTerms must be one of $USE_TERMS")
        return terms
    }

    /**
     * A styled VITS model's style per feeling (ADR-057), `{"NEUTRAL": 3, "SAD": 12}` written as `NEUTRAL:3,SAD:12`.
     * The app knows the feeling names (core's Feeling) and drops a voice whose styles it cannot read.
     */
    private fun styles(id: String, entry: Map<String, Any?>): String? {
        val styles = entry["styles"] ?: return null
        val map = styles as? Map<*, *> ?: fail("$id: styles must map feelings to style ids")
        val pairs = map.entries.map { (feeling, style) ->
            val number = (style as? Int)?.takeIf { it >= 0 } // JsonSlurper reads a whole number as an Int
            when {
                !FEELING.matches(feeling.toString()) -> fail("$id: style key '$feeling' must be a feeling name")
                number == null -> fail("$id: style for $feeling must be a whole number >= 0")
                else -> "$feeling:$number"
            }
        }
        if (entry["family"] != "vits" || "NEUTRAL" !in map.keys) fail("$id: styles need a vits voice and NEUTRAL")
        return pairs.joinToString(",")
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
        val FAMILIES = setOf("vits", "kitten", "kokoro", "supertonic")
        val NEEDS_VOICES = setOf("kitten", "kokoro", "supertonic")
        val NEEDS_ENGINE_LANG = setOf("kokoro", "supertonic")
        val CREDIT_LINKS = listOf("licenceUrl", "sourceUrl")
        val FEELING = Regex("[A-Z]+")
        val USE_TERMS = setOf("openrail-m")
    }
}
