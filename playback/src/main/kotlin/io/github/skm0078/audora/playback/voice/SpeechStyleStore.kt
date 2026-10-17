// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/voice/SpeechStyleStore.kt
// Purpose: The listener's speaking rate, pace and clause pauses (ADR-033), kept by the playback service in a file.

package io.github.skm0078.audora.playback.voice

import android.content.Context
import io.github.skm0078.audora.core.config.SpeechStyleConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.Pace
import io.github.skm0078.audora.core.model.ReadingModes
import io.github.skm0078.audora.core.model.SpeechStyle
import java.io.File
import java.io.IOException
import java.util.Properties

/**
 * The service owns the style, so reading resumed from the lock screen or a headset after Audora was closed speaks
 * at the listener's rate too, not only reading started from the app. [current] is read on the render thread and
 * set on the main thread, hence volatile. A missing or unreadable file means the default style, never an error.
 */
class SpeechStyleStore(private val file: File, val config: SpeechStyleConfig = SpeechStyleConfig()) {
    @Volatile
    private var style: SpeechStyle? = null

    /** Loaded from the file on first use: one small read. */
    val current: SpeechStyle
        get() = style ?: load().also { style = it }

    /** Applies [requested] (rate clamped to the range) at once; returns what was applied. A failed save is logged. */
    fun set(requested: SpeechStyle): SpeechStyle {
        val applied = requested.copy(rate = config.clampRate(requested.rate))
        style = applied
        save(applied)
        return applied
    }

    private fun load(): SpeechStyle {
        val properties = readProperties() ?: return SpeechStyle()
        val rate = properties.getProperty(KEY_RATE)?.toFloatOrNull() ?: SpeechStyle.NORMAL_RATE
        val pace = Pace.entries.firstOrNull { it.name == properties.getProperty(KEY_PACE) } ?: Pace.NORMAL
        val clausePauses = properties.getProperty(KEY_CLAUSE_PAUSES)?.toBooleanStrictOrNull() ?: true
        val modes = ReadingModes.decode(properties.getProperty(KEY_READING_MODES))
        return SpeechStyle(config.clampRate(rate), pace, clausePauses, modes)
    }

    /** Null when there is no file or it cannot be read; logged. */
    private fun readProperties(): Properties? = try {
        file.takeIf { it.isFile }?.reader(Charsets.UTF_8)?.use { reader -> Properties().apply { load(reader) } }
    } catch (e: IOException) {
        AudoraLog.w(TAG, "Speech style unreadable, using the default: ${e.message}")
        null
    } catch (e: IllegalArgumentException) {
        // Properties.load throws this on a malformed \uXXXX escape.
        AudoraLog.w(TAG, "Speech style malformed, using the default: ${e.message}")
        null
    }

    /** Atomically (temp file + rename). */
    private fun save(applied: SpeechStyle) {
        val properties = Properties().apply {
            setProperty(KEY_RATE, applied.rate.toString())
            setProperty(KEY_PACE, applied.pace.name)
            setProperty(KEY_CLAUSE_PAUSES, applied.clausePauses.toString())
            setProperty(KEY_READING_MODES, applied.modes.encode())
        }
        val temp = File(file.path + ".tmp")
        try {
            file.parentFile?.mkdirs()
            temp.writer(Charsets.UTF_8).use { properties.store(it, null) }
            if (!temp.renameTo(file)) throw IOException("cannot replace ${file.name}")
            AudoraLog.i(
                TAG,
                "Speech style set: ${applied.rate} ${applied.pace} clauses=${applied.clausePauses} ${applied.modes}",
            )
        } catch (e: IOException) {
            AudoraLog.e(TAG, "Could not save the speech style; it applies until the service stops", e)
        }
    }

    companion object {
        private const val TAG = "Playback.SpeechStyle"
        private const val FILE_NAME = "speech-style.properties"
        private const val KEY_RATE = "rate"
        private const val KEY_PACE = "pace"
        private const val KEY_CLAUSE_PAUSES = "clausePauses"
        private const val KEY_READING_MODES = "readingModes"

        fun forContext(context: Context): SpeechStyleStore =
            SpeechStyleStore(File(context.applicationContext.filesDir, FILE_NAME))
    }
}
