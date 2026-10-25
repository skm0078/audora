// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/focus/FocusSoundStore.kt
// Purpose: Keeps the focus sound choice (plan item 72) in a one-line file the playback service owns.

package io.github.skm0078.audora.playback.focus

import android.content.Context
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.FocusSoundOptions
import io.github.skm0078.audora.playback.async.LatestWrites
import java.io.File
import java.io.IOException
import java.util.concurrent.Executor

/**
 * The service owns it, like the sleep options, so reading resumed from a headset has the sound too. [current] is
 * set on the main thread and read on the playback thread, hence volatile. A missing or unreadable file means the
 * sound is off, never an error.
 */
class FocusSoundStore(private val file: File, writer: Executor = LatestWrites.SETTINGS_WRITER) {
    private val saves = LatestWrites(writer, ::save)

    @Volatile
    private var options: FocusSoundOptions? = null

    val current: FocusSoundOptions
        get() = options ?: load().also { options = it }

    /** Applies [requested] at once; the file is saved in the background, a failed save logged. */
    fun set(requested: FocusSoundOptions) {
        options = requested
        saves.submit(requested)
    }

    private fun save(requested: FocusSoundOptions) {
        val temp = File(file.path + ".tmp")
        try {
            file.parentFile?.mkdirs()
            temp.writeText(requested.encode(), Charsets.UTF_8)
            if (!temp.renameTo(file)) throw IOException("cannot replace ${file.name}")
            AudoraLog.i(TAG, "Focus sound set: ${requested.encode()}")
        } catch (e: IOException) {
            AudoraLog.e(TAG, "Could not save the focus sound; it applies until the service stops", e)
        }
    }

    private fun load(): FocusSoundOptions = try {
        FocusSoundOptions.decode(file.takeIf { it.isFile }?.readText(Charsets.UTF_8))
    } catch (e: IOException) {
        AudoraLog.w(TAG, "Focus sound unreadable, using the default: ${e.message}")
        FocusSoundOptions()
    }

    companion object {
        private const val TAG = "Playback.FocusSound"
        private const val FILE_NAME = "focus-sound.txt"

        fun forContext(context: Context): FocusSoundStore =
            FocusSoundStore(File(context.applicationContext.filesDir, FILE_NAME))
    }
}
