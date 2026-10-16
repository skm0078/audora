// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/voices/VoicePreferences.kt
// Purpose: Which voice reads each language when the user picked one in Settings → Voices (a small properties file).

package io.github.skm0078.audora.speech.voices

import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.VoiceId
import java.io.File
import java.io.IOException
import java.util.Properties

/**
 * Lives next to the voices (not in the app's DataStore) because the playback service reads it, and :speech is the
 * module that owns voices. A missing or unreadable file means "use the defaults", never an error.
 */
class VoicePreferences(private val file: File) {
    fun read(): Map<Lang, VoiceId> {
        if (!file.isFile) return emptyMap()
        val properties = try {
            file.reader(Charsets.UTF_8).use { reader -> Properties().apply { load(reader) } }
        } catch (e: IOException) {
            AudoraLog.w(TAG, "Voice choices unreadable, using defaults: ${e.message}")
            Properties()
        }
        return Lang.entries
            .mapNotNull { lang -> properties.getProperty(lang.bcp47Tag)?.let(::parse)?.let { lang to it } }
            .toMap()
    }

    /** Saves [choices] atomically (temp file + rename). Returns false, after logging, if it could not. */
    fun write(choices: Map<Lang, VoiceId>): Boolean {
        val properties = Properties()
        choices.forEach { (lang, voice) -> properties.setProperty(lang.bcp47Tag, "${voice.engine.name}:${voice.name}") }
        val temp = File(file.path + ".tmp")
        return try {
            file.parentFile?.mkdirs()
            temp.writer(Charsets.UTF_8).use { properties.store(it, null) }
            if (!temp.renameTo(file)) throw IOException("cannot replace ${file.name}")
            true
        } catch (e: IOException) {
            AudoraLog.e(TAG, "Could not save voice choices", e)
            false
        }
    }

    private fun parse(value: String): VoiceId? {
        val engine = EngineId.entries.firstOrNull { it.name == value.substringBefore(':') }
        val name = value.substringAfter(':', missingDelimiterValue = "")
        return if (engine == null || name.isBlank()) null else VoiceId(engine, name)
    }

    private companion object {
        const val TAG = "Speech.VoicePrefs"
    }
}
