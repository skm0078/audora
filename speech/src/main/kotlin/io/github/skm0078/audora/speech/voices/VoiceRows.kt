// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/voices/VoiceRows.kt
// Purpose: Builds the Voices screen's list from the catalog, what is installed, running downloads and the choices.

package io.github.skm0078.audora.speech.voices

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.speech.sherpa.SherpaVoiceSpec

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
) {
    /** Unique in the list even when an installed voice shares the built-in voice's id after a bundled-voice swap. */
    val key: String get() = "${kind.name}:${id.name}"
}

object VoiceRows {
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
        val builtIn = listOfNotNull(bundled).map { spec ->
            val reads = preferred[spec.lang]?.let { it == spec.voiceId } ?: (spec.lang == DEFAULT_NEURAL_LANG)
            row(spec, VoiceKind.BUILT_IN, VoiceStatus.BuiltIn, 0, reads)
        }
        val fromCatalog = catalog.voices.map { voice ->
            val copy = installedById[voice.id]?.takeIf { it.origin == VoiceOrigin.CATALOG }
            val status = catalogStatus(voice, copy, downloads[voice.id])
            val spec = copy?.spec ?: voice.spec
            val reads = copy != null && preferred[spec.lang] == spec.voiceId
            row(spec, VoiceKind.CATALOG, status, copy?.sizeBytes ?: voice.sizeBytes, reads)
        }
        val catalogIds = catalog.voices.map { it.id }.toSet()
        val others = installed.filter { it.origin == VoiceOrigin.CUSTOM || it.spec.id !in catalogIds }.map {
            val kind = if (it.origin == VoiceOrigin.CUSTOM) VoiceKind.CUSTOM else VoiceKind.CATALOG
            row(it.spec, kind, VoiceStatus.Installed, it.sizeBytes, preferred[it.spec.lang] == it.spec.voiceId)
        }
        return (builtIn + fromCatalog + others).sortedWith(
            compareBy<VoiceRow> { it.lang.ordinal }.thenBy { it.kind.ordinal }.thenBy { it.label },
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

    private fun row(spec: SherpaVoiceSpec, kind: VoiceKind, status: VoiceStatus, size: Long, reads: Boolean) =
        VoiceRow(spec.voiceId, spec.label, spec.lang, spec.licence, size, kind, status, reads)

    /** The language the built-in neural voice reads by default (Build Spec §3). */
    private val DEFAULT_NEURAL_LANG = Lang.EN
}
