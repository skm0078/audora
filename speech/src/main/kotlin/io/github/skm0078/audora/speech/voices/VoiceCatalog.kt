// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/voices/VoiceCatalog.kt
// Purpose: The downloadable voices (speech/models.json, packed by the build as one properties file per voice).

package io.github.skm0078.audora.speech.voices

import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.flatMap
import io.github.skm0078.audora.speech.sherpa.SherpaVoiceManifest
import io.github.skm0078.audora.speech.sherpa.SherpaVoiceSpec
import java.util.Properties

/**
 * One voice the user can download: its sherpa-onnx files plus where the zip is and what it must hash to.
 * [spec]'s `contentVersion` is [sha256], so an installed copy from an older pin shows as an update.
 *
 * @property credits for the licences screen (P3-06); null only for an asset written by an older build.
 * @property recommended its language's suggested download (speech/models.json `recommended`, ADR-030).
 * @property useTerms terms the user agrees to before the download starts; null for a plain licence.
 */
data class CatalogVoice(
    val spec: SherpaVoiceSpec,
    override val url: String,
    override val sha256: String,
    override val sizeBytes: Long,
    val credits: VoiceCredits? = null,
    val recommended: Boolean = false,
    val useTerms: VoiceUseTerms? = null,
) : PinnedFile {
    override val id: String get() = spec.id
}

/** The catalog shipped in this APK. Pins travel with the signed APK, so nothing downloaded can change them. */
class VoiceCatalog(val voices: List<CatalogVoice>) {
    private val byId = voices.associateBy { it.id }

    init {
        require(byId.size == voices.size) { "Duplicate catalog ids in ${voices.map { it.id }}" }
    }

    fun find(id: String): CatalogVoice? = byId[id]

    companion object {
        /** Where the build writes the catalog inside the APK's assets (VoiceCatalogAssetsTask). */
        const val ASSET_DIR = "voices/catalog"
        const val FILE_SUFFIX = ".properties"
    }
}

/**
 * Parses one catalog entry: the `voice.properties` keys of [SherpaVoiceManifest] plus `url`, `sha256`,
 * `sizeBytes`, the credits ([VoiceCredits]) and `recommended`. The build already validated it; this guards against
 * a hand-edited asset or a build bug.
 */
object CatalogVoiceParser {
    private const val KEY_URL = "url"
    private const val KEY_SHA256 = "sha256"
    private const val KEY_SIZE = "sizeBytes"
    private const val KEY_RECOMMENDED = "recommended"
    private const val KEY_USE_TERMS = "useTerms"
    private const val HTTPS = "https://"
    private val SHA256_HEX = Regex("[0-9a-f]{64}")

    fun parse(properties: Properties): Outcome<CatalogVoice, String> =
        SherpaVoiceManifest.parse(properties).flatMap { spec ->
            val url = properties.getProperty(KEY_URL)?.trim().orEmpty()
            val sha256 = properties.getProperty(KEY_SHA256)?.trim()?.lowercase().orEmpty()
            val size = properties.getProperty(KEY_SIZE)?.trim()?.toLongOrNull()
            val terms = properties.getProperty(KEY_USE_TERMS)?.trim()
            val useTerms = terms?.let(VoiceUseTerms::fromKey)
            when {
                !url.startsWith(HTTPS) -> Outcome.Failure("catalog voice ${spec.id} needs an https url, has '$url'")

                !SHA256_HEX.matches(sha256) -> Outcome.Failure("catalog voice ${spec.id} has an invalid sha256")

                size == null || size <= 0 -> Outcome.Failure("catalog voice ${spec.id} has an invalid sizeBytes")

                // A voice whose terms this build cannot show is left out rather than offered without them.
                terms != null && useTerms == null ->
                    Outcome.Failure("catalog voice ${spec.id} has unknown useTerms '$terms'")

                else -> Outcome.Success(
                    CatalogVoice(
                        spec = spec.copy(contentVersion = sha256),
                        url = url,
                        sha256 = sha256,
                        sizeBytes = size,
                        credits = VoiceCredits.parse(properties),
                        recommended = properties.getProperty(KEY_RECOMMENDED)?.trim().toBoolean(),
                        useTerms = useTerms,
                    ),
                )
            }
        }
}
