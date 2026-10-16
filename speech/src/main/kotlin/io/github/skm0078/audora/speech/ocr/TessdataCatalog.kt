// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/ocr/TessdataCatalog.kt
// Purpose: The OCR models this APK can download (assets/ocr/tessdata.properties), pinned by SHA-256 and size.

package io.github.skm0078.audora.speech.ocr

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.flatMap
import io.github.skm0078.audora.core.result.map
import io.github.skm0078.audora.speech.voices.PinnedFile
import java.util.Properties

/** One Tesseract model: `tessdata/<fileName>` once installed. */
data class OcrModel(
    val lang: Lang,
    val fileName: String,
    override val url: String,
    override val sha256: String,
    override val sizeBytes: Long,
) : PinnedFile {
    override val id: String get() = "ocr-${lang.bcp47Tag}"
}

/** The pinned models; [revision] names the pin set (it keys the OCR text cache). */
class TessdataCatalog(val revision: String, val models: List<OcrModel>) {
    val totalBytes: Long get() = models.sumOf { it.sizeBytes }

    fun find(lang: Lang): OcrModel? = models.firstOrNull { it.lang == lang }

    companion object {
        /** Where the catalog sits in the APK's assets. */
        const val ASSET_PATH = "ocr/tessdata.properties"
    }
}

/**
 * Reads the catalog. A broken entry fails the whole catalog: it is a file in the APK, so a mistake is a build bug,
 * and a half-read catalog would offer languages that can never install.
 */
object TessdataCatalogParser {
    private const val HTTPS = "https://"
    private val SHA256_HEX = Regex("[0-9a-f]{64}")
    private val FILE_NAME = Regex("[a-z_]+\\.traineddata")

    fun parse(properties: Properties): Outcome<TessdataCatalog, String> {
        val revision = properties.value("revision")
        val baseUrl = properties.value("baseUrl").orEmpty()
        val tags = properties.value("langs")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        return when {
            revision == null -> Outcome.Failure("tessdata catalog has no revision")

            !baseUrl.startsWith(HTTPS) || !baseUrl.endsWith("/") ->
                Outcome.Failure("tessdata catalog needs an https baseUrl ending in '/', has '$baseUrl'")

            tags.isEmpty() -> Outcome.Failure("tessdata catalog lists no langs")

            tags.distinct().size != tags.size -> Outcome.Failure("tessdata catalog repeats a lang")

            else -> models(properties, tags, baseUrl).map { TessdataCatalog(revision, it) }
        }
    }

    /** Every model, or the first broken entry. */
    private fun models(properties: Properties, tags: List<String>, baseUrl: String): Outcome<List<OcrModel>, String> =
        tags.fold<String, Outcome<List<OcrModel>, String>>(Outcome.Success(emptyList())) { parsed, tag ->
            parsed.flatMap { models -> model(properties, tag, baseUrl).map { models + it } }
        }

    private fun model(properties: Properties, tag: String, baseUrl: String): Outcome<OcrModel, String> {
        val lang = Lang.entries.firstOrNull { it.bcp47Tag == tag }
        val file = properties.value("$tag.file")
        val sha256 = properties.value("$tag.sha256")?.lowercase()
        val size = properties.value("$tag.sizeBytes")?.toLongOrNull()
        return when {
            lang == null -> Outcome.Failure("tessdata catalog: unknown lang '$tag'")
            file == null || !FILE_NAME.matches(file) -> Outcome.Failure("tessdata catalog: bad file for $tag: '$file'")
            sha256 == null || !SHA256_HEX.matches(sha256) -> Outcome.Failure("tessdata catalog: bad sha256 for $tag")
            size == null || size <= 0 -> Outcome.Failure("tessdata catalog: bad sizeBytes for $tag")
            else -> Outcome.Success(OcrModel(lang, file, baseUrl + file, sha256, size))
        }
    }

    private fun Properties.value(key: String): String? = getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }
}
