// File: core/src/main/kotlin/io/github/skm0078/audora/core/model/VoiceMaps.kt
// Purpose: The stored form of a voice per language, shared by a book's own voices and the cast voices.

package io.github.skm0078.audora.core.model

/** `EN=SHERPA/name;HI=...`; [decode] reads it back, dropping bad entries, so it never fails. */
object VoiceMaps {
    fun encode(voices: Map<Lang, VoiceId>): String = voices.entries.sortedBy {
        it.key
    }.joinToString(";") { (lang, voice) -> "$lang=${voice.engine}/${voice.name}" }

    fun decode(line: String?): Map<Lang, VoiceId> = line.orEmpty().split(';').mapNotNull { entry ->
        val (lang, voice) = entry.split('=', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
        val (engine, name) = voice.split('/', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
        val parsedLang = Lang.entries.firstOrNull { it.name == lang } ?: return@mapNotNull null
        val parsedEngine = EngineId.entries.firstOrNull { it.name == engine } ?: return@mapNotNull null
        name.takeIf { it.isNotBlank() }?.let { parsedLang to VoiceId(parsedEngine, it) }
    }.toMap()
}
