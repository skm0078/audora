// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/voices/VoiceRows.kt
// Purpose: Builds the Voices screen's list from the catalog, what is installed, running downloads and the choices.

package io.github.skm0078.audora.speech.voices

import io.github.skm0078.audora.core.config.VoiceSpeedConfig
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.speech.sherpa.CastSpeaker
import io.github.skm0078.audora.speech.sherpa.CastSpeakers
import io.github.skm0078.audora.speech.sherpa.SherpaVoiceSpec
import io.github.skm0078.audora.speech.sherpa.SpeakerGender

enum class VoiceKind { BUILT_IN, CATALOG, CUSTOM }

/** What a background download of one voice is doing, as WorkManager reports it. */
sealed interface DownloadState {
    data object Queued : DownloadState

    data class Running(val bytes: Long, val total: Long) : DownloadState

    data class Failed(val message: String) : DownloadState
}

sealed interface VoiceStatus {
    /** Packed in the APK: always there, cannot be deleted. */
    data object BuiltIn : VoiceStatus

    data object NotDownloaded : VoiceStatus

    data object Queued : VoiceStatus

    data class Downloading(val bytes: Long, val total: Long) : VoiceStatus

    data object Installed : VoiceStatus

    /** Installed from an older pin of the catalog; downloading again replaces it. */
    data object UpdateAvailable : VoiceStatus

    data class Failed(val message: String) : VoiceStatus
}

/**
 * @property readsLanguage this voice reads [lang] now (the user's choice, or the default when there is none).
 * @property sizeBytes download size for a catalog voice not yet installed, else the size on disk (0 when built in).
 * @property speed how fast it rendered on this phone; null until it has read something (P4-02).
 * @property tooSlow measured too slow to keep up (Build Spec §3: offered only below the limit); it cannot be newly
 *   picked, but stays if it is already the language's voice, since the user chose it.
 * @property recommended the catalog's suggested download for its language (P3-06); listed first among downloads.
 * @property beta a catalog voice of a language still in beta ([CatalogVoice.beta]): shown with a Beta tag.
 */
data class VoiceRow(
    val id: VoiceId,
    val label: String,
    val lang: Lang,
    val licence: String,
    val sizeBytes: Long,
    val kind: VoiceKind,
    val status: VoiceStatus,
    val readsLanguage: Boolean,
    val speed: VoiceSpeed? = null,
    val tooSlow: Boolean = false,
    val recommended: Boolean = false,
    val useTerms: VoiceUseTerms? = null,
    val beta: Boolean = false,
) {
    /** Unique in the list even when an installed voice shares the built-in voice's id after a bundled-voice swap. */
    val key: String get() = "${kind.name}:${id.name}"
}

object VoiceRows {
    /** Measured speeds and the rule that judges them. */
    class Speeds(val byVoice: Map<VoiceId, VoiceSpeed>, val config: VoiceSpeedConfig)

    /**
     * @param bundled the APK's voice, the English default; null if the APK lacks it (logged where it is read).
     * @param preferred the user's choice per language; a language without one reads with its default (built-in
     *   voice for English, the phone's voice otherwise).
     */
    fun build(
        bundled: SherpaVoiceSpec?,
        catalog: VoiceCatalog,
        installed: List<InstalledVoice>,
        downloads: Map<String, DownloadState>,
        preferred: Map<Lang, VoiceId>,
    ): List<VoiceRow> {
        val installedById = installed.associateBy { it.spec.id }
        val builtIn = listOfNotNull(bundled).flatMap { spec ->
            val reads = preferred[spec.lang]?.let { it == spec.voiceId } ?: (spec.lang == DEFAULT_NEURAL_LANG)
            listOf(row(spec, VoiceKind.BUILT_IN, VoiceStatus.BuiltIn, 0, reads).copy(recommended = true)) +
                builtInSpeakers(spec, preferred)
        }
        val fromCatalog = catalog.voices.map { voice ->
            val copy = installedById[voice.id]?.takeIf { it.origin == VoiceOrigin.CATALOG }
            val status = catalogStatus(voice, copy, downloads[voice.id])
            val spec = copy?.spec ?: voice.spec
            val reads = copy != null && preferred[spec.lang] == spec.voiceId
            row(spec, VoiceKind.CATALOG, status, copy?.sizeBytes ?: voice.sizeBytes, reads)
                .copy(recommended = voice.recommended, useTerms = voice.useTerms, beta = voice.beta)
        }
        val catalogIds = catalog.voices.map { it.id }.toSet()
        val others = installed.filter { it.origin == VoiceOrigin.CUSTOM || it.spec.id !in catalogIds }.map {
            val kind = if (it.origin == VoiceOrigin.CUSTOM) VoiceKind.CUSTOM else VoiceKind.CATALOG
            row(it.spec, kind, VoiceStatus.Installed, it.sizeBytes, preferred[it.spec.lang] == it.spec.voiceId)
        }
        return (builtIn + fromCatalog + others).sortedWith(
            compareBy<VoiceRow> { it.lang.ordinal }.thenBy { it.kind.ordinal }.thenBy { !it.recommended }
                .thenBy { it.label },
        )
    }

    private fun catalogStatus(voice: CatalogVoice, copy: InstalledVoice?, download: DownloadState?): VoiceStatus =
        when (download) {
            DownloadState.Queued -> VoiceStatus.Queued

            is DownloadState.Running -> VoiceStatus.Downloading(download.bytes, download.total)

            is DownloadState.Failed ->
                if (copy == null) VoiceStatus.Failed(download.message) else installed(voice, copy)

            null -> if (copy == null) VoiceStatus.NotDownloaded else installed(voice, copy)
        }

    private fun installed(voice: CatalogVoice, copy: InstalledVoice): VoiceStatus =
        if (copy.spec.contentVersion == voice.sha256) VoiceStatus.Installed else VoiceStatus.UpdateAvailable

    /** Adds each voice's measured speed and the too-slow verdict (P4-02). */
    fun withSpeeds(rows: List<VoiceRow>, speeds: Speeds): List<VoiceRow> = rows.map { row ->
        speeds.byVoice[row.id]?.let { row.copy(speed = it, tooSlow = it.tooSlow(speeds.config)) } ?: row
    }

    /**
     * The built-in voice's other speakers (ADR-052), each a voice of its own to pick: same model, so no download and
     * no extra memory. Named like the built-in voice's label, "English, Bruno (male)".
     */
    private fun builtInSpeakers(spec: SherpaVoiceSpec, preferred: Map<Lang, VoiceId>): List<VoiceRow> =
        CastSpeakers.of(spec).filter { it.speakerId != spec.speakerId }.map { speaker ->
            val id = CastSpeakers.variant(spec.voiceId, speaker.speakerId)
            VoiceRow(
                id = id,
                label = speakerLabel(spec, speaker),
                lang = spec.lang,
                licence = spec.licence,
                sizeBytes = 0,
                kind = VoiceKind.BUILT_IN,
                status = VoiceStatus.BuiltIn,
                readsLanguage = preferred[spec.lang] == id,
            )
        }

    private fun speakerLabel(spec: SherpaVoiceSpec, speaker: CastSpeaker): String {
        val language = spec.label.substringBefore(LABEL_LANGUAGE_END)
        return when (speaker.gender) {
            SpeakerGender.MALE -> "$language, ${speaker.label} (male)"
            SpeakerGender.FEMALE -> "$language, ${speaker.label} (female)"
            SpeakerGender.UNKNOWN -> "$language, ${speaker.label}"
        }
    }

    private fun row(spec: SherpaVoiceSpec, kind: VoiceKind, status: VoiceStatus, size: Long, reads: Boolean) =
        VoiceRow(spec.voiceId, spec.label, spec.lang, spec.licence, size, kind, status, reads)

    /** A catalog label starts with its language: "English, Bruno (male)". */
    private const val LABEL_LANGUAGE_END = ','

    /** The language the built-in neural voice reads by default (Build Spec §3). */
    private val DEFAULT_NEURAL_LANG = Lang.EN
}
