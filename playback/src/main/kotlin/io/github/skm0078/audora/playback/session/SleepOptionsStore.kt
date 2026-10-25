// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/session/SleepOptionsStore.kt
// Purpose: Keeps the sleep timer extras (plan item 37) in a one-line file the playback service owns.

package io.github.skm0078.audora.playback.session

import android.content.Context
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.SleepOptions
import io.github.skm0078.audora.playback.async.LatestWrites
import java.io.File
import java.io.IOException
import java.util.concurrent.Executor

/**
 * The service owns these, like the speech style, so bedtime auto-on works for reading resumed from a headset too.
 * [current] is read and set on the main thread. A missing or unreadable file means the defaults, never an error.
 */
class SleepOptionsStore(private val file: File, writer: Executor = LatestWrites.SETTINGS_WRITER) {
    private val saves = LatestWrites(writer, ::save)

    private var options: SleepOptions? = null

    val current: SleepOptions
        get() = options ?: load().also { options = it }

    /** Applies [requested] at once; the file is saved in the background, a failed save logged. */
    fun set(requested: SleepOptions) {
        options = requested
        saves.submit(requested)
    }

    private fun save(requested: SleepOptions) {
        val temp = File(file.path + ".tmp")
        try {
            file.parentFile?.mkdirs()
            temp.writeText(requested.encode(), Charsets.UTF_8)
            if (!temp.renameTo(file)) throw IOException("cannot replace ${file.name}")
            AudoraLog.i(TAG, "Sleep options set: ${requested.encode()}")
        } catch (e: IOException) {
            AudoraLog.e(TAG, "Could not save the sleep options; they apply until the service stops", e)
        }
    }

    private fun load(): SleepOptions = try {
        SleepOptions.decode(file.takeIf { it.isFile }?.readText(Charsets.UTF_8))
    } catch (e: IOException) {
        AudoraLog.w(TAG, "Sleep options unreadable, using the defaults: ${e.message}")
        SleepOptions()
    }

    companion object {
        private const val TAG = "Playback.SleepOptions"
        private const val FILE_NAME = "sleep-options.txt"

        fun forContext(context: Context): SleepOptionsStore =
            SleepOptionsStore(File(context.applicationContext.filesDir, FILE_NAME))
    }
}
