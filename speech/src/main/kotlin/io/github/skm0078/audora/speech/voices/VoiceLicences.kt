// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/voices/VoiceLicences.kt
// Purpose: Credits and licence texts of every voice Audora ships or offers, for the "Voices & licences" screen.

package io.github.skm0078.audora.speech.voices

import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.speech.sherpa.SherpaVoiceSpec
import java.io.File
import java.io.IOException
import java.util.Properties

/**
 * Who made a voice, the licence's own page and where the model came from (P3-06, ADR-030). CC BY-SA, BY-NC-SA and
 * BY-NC voices must show these (Build Spec §3); every catalog and bundled voice carries them.
 */
data class VoiceCredits(val credit: String, val licenceUrl: String, val sourceUrl: String) {
    companion object {
        private const val KEY_CREDIT = "credit"
        private const val KEY_LICENCE_URL = "licenceUrl"
        private const val KEY_SOURCE_URL = "sourceUrl"
        private const val HTTPS = "https://"

        /** Null when a key is missing or a link is not https (an older build's asset, or an imported voice). */
        fun parse(properties: Properties): VoiceCredits? {
            fun text(key: String): String? = properties.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }
            val credit = text(KEY_CREDIT)
            val licenceUrl = text(KEY_LICENCE_URL)?.takeIf { it.startsWith(HTTPS) }
            val sourceUrl = text(KEY_SOURCE_URL)?.takeIf { it.startsWith(HTTPS) }
            return if (credit != null && licenceUrl != null && sourceUrl != null) {
                VoiceCredits(credit, licenceUrl, sourceUrl)
            } else {
                null
            }
        }
    }
}

/** Where a listed voice comes from, in the order the screen shows them per language. */
enum class LicenceKind { BUILT_IN, DOWNLOAD, IMPORTED }

/** @property installed on this phone now, so its licence files ([LicenceTexts]) can be shown. */
data class VoiceLicence(
    val id: VoiceId,
    val label: String,
    val lang: Lang,
    val licence: String,
    val credits: VoiceCredits?,
    val kind: LicenceKind,
    val installed: Boolean,
)

object VoiceLicences {
    /**
     * Every voice this APK can read with: the bundled one, every catalog voice (downloaded or not, since the screen
     * is also where a user checks a licence before downloading) and imported ones. Credits come from the catalog in
     * the APK, never from a downloaded file.
     */
    fun build(
        bundled: SherpaVoiceSpec?,
        bundledCredits: VoiceCredits?,
        catalog: VoiceCatalog,
        installed: List<InstalledVoice>,
    ): List<VoiceLicence> {
        val installedIds = installed.map { it.spec.id }.toSet()
        val builtIn = listOfNotNull(bundled).map {
            VoiceLicence(it.voiceId, it.label, it.lang, it.licence, bundledCredits, LicenceKind.BUILT_IN, true)
        }
        val downloads = catalog.voices.map {
            val spec = it.spec
            VoiceLicence(
                spec.voiceId,
                spec.label,
                spec.lang,
                spec.licence,
                it.credits,
                LicenceKind.DOWNLOAD,
                spec.id in installedIds,
            )
        }
        val imported = installed.filter { it.origin == VoiceOrigin.CUSTOM }.map {
            VoiceLicence(
                it.spec.voiceId,
                it.spec.label,
                it.spec.lang,
                it.spec.licence,
                null,
                LicenceKind.IMPORTED,
                true,
            )
        }
        return (builtIn + downloads + imported).sortedWith(
            compareBy<VoiceLicence> { it.lang.ordinal }.thenBy { it.kind.ordinal }.thenBy { it.label },
        )
    }
}

/** One licence or model-card file shipped inside a voice. */
data class LicenceText(val name: String, val text: String)

/** Reads the attribution files a voice's folder carries (the mirror and the importer keep exactly these). */
object LicenceTexts {
    /** MODEL_LICENSE: a model's own licence added by the voice mirror when its archive has only its code's. */
    val FILE_NAMES = listOf("MODEL_LICENSE", "LICENSE", "MODEL_CARD", "README.md")

    /** A licence or model card is a few kilobytes; anything far larger is not one and is cut. */
    const val MAX_CHARS = 64 * 1024

    fun read(dir: File): List<LicenceText> = FILE_NAMES.mapNotNull { name ->
        val file = File(dir, name)
        if (!file.isFile) return@mapNotNull null
        try {
            LicenceText(name, readCapped(file))
        } catch (e: IOException) {
            AudoraLog.e(TAG, "Licence file $name of voice=${dir.name} unreadable", e)
            null
        }
    }

    private fun readCapped(file: File): String = file.reader(Charsets.UTF_8).use { reader ->
        val text = StringBuilder()
        val buffer = CharArray(BUFFER_CHARS)
        while (text.length < MAX_CHARS) {
            val read = reader.read(buffer, 0, minOf(buffer.size, MAX_CHARS - text.length))
            if (read < 0) break
            text.appendRange(buffer, 0, read)
        }
        text.toString()
    }

    private const val BUFFER_CHARS = 8 * 1024
    private const val TAG = "Speech.Licences"
}
