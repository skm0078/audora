// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/voices/LanguagePacks.kt
// Purpose: One obvious download per language (its recommended voice), and the voices a simple build leaves out.

package io.github.skm0078.audora.speech.voices

import io.github.skm0078.audora.core.model.Lang

/**
 * A language's one-tap download: [voice] is the catalog's recommended voice for [lang]. [ready] once any neural
 * voice of [lang] can speak, whichever it is, so a listener who picked another voice is never asked again.
 */
data class LanguagePack(val lang: Lang, val voice: VoiceRow, val ready: Boolean) {
    val downloading: Boolean get() = voice.status is VoiceStatus.Downloading || voice.status == VoiceStatus.Queued
}

object LanguagePacks {
    /** Every language with a recommended download, in [Lang] order; a language with a built-in voice has none. */
    fun of(rows: List<VoiceRow>): List<LanguagePack> = Lang.entries.mapNotNull { pack(rows, it) }

    /** [lang]'s pack, or null when it has no recommended download (English: its voice is built in). */
    fun pack(rows: List<VoiceRow>, lang: Lang): LanguagePack? {
        val own = rows.filter { it.lang == lang }
        val voice = own.firstOrNull { it.recommended && it.kind == VoiceKind.CATALOG }
        val builtIn = own.any { it.kind == VoiceKind.BUILT_IN }
        return voice?.takeIf { !builtIn }?.let { LanguagePack(lang, it, ready = own.any { row -> row.speaks }) }
    }

    private val VoiceRow.speaks: Boolean
        get() = status == VoiceStatus.Installed || status == VoiceStatus.UpdateAvailable
}

/**
 * Which voices Settings lists. A [simple] build (the Play release) leaves out [hiddenIds]: voices still being
 * judged, or whose licence is unsettled. A hidden voice already on the phone, or reading its language, stays listed,
 * so nobody loses a voice they chose. [canImport]: the "Import a voice" row, for people who bring their own.
 */
data class VoiceListConfig(val simple: Boolean = false, val hiddenIds: Set<String> = SIMPLE_HIDDEN) {
    val canImport: Boolean get() = !simple

    fun visible(rows: List<VoiceRow>): List<VoiceRow> = if (!simple) {
        rows
    } else {
        rows.filter { it.id.name !in hiddenIds || it.readsLanguage || it.status != VoiceStatus.NotDownloaded }
    }

    companion object {
        /**
         * Supertonic Hindi (its model licence file is missing upstream), MMS Bengali (non-commercial, and SYSPIN
         * reads Bengali better) and Piper's Google Bengali (the weakest of the three Bengali voices).
         */
        val SIMPLE_HIDDEN = setOf("supertonic-hi", "mms-bn", "piper-bn-google")
    }
}
