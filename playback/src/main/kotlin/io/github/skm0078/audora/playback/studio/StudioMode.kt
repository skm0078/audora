// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/studio/StudioMode.kt
// Purpose: The calls the UI makes for studio mode, and where studio audio lives on the phone (plan item 6, ADR-039).

package io.github.skm0078.audora.playback.studio

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.SpeechStyle
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.pdf.PIPELINE_VERSION
import io.github.skm0078.audora.speech.punct.PunctuationModels
import java.io.File

object StudioMode {
    internal const val ACTION_START = "io.github.skm0078.audora.studio.START"
    internal const val ACTION_STOP = "io.github.skm0078.audora.studio.STOP"
    internal const val ACTION_ON_BATTERY = "io.github.skm0078.audora.studio.ON_BATTERY"
    internal const val EXTRA_BOOK = "book"
    private const val FOLDER = "studio"
    private const val TAG = "Studio.Start"

    /**
     * Starts rendering [id] ahead of the listener's place. Call it from a user action while Audora is on screen
     * (Android allows a foreground service only then, and Audora never starts work on its own, C7). It renders while
     * the phone charges; its notification offers to start on battery and to stop.
     */
    fun start(context: Context, id: BookId) {
        AudoraLog.i(TAG, "Studio mode requested, book=${id.logLabel}")
        val intent = Intent(context, StudioService::class.java).setAction(ACTION_START).putExtra(EXTRA_BOOK, id.value)
        context.startForegroundService(intent)
    }

    /** Where studio audio is kept: app-private, never backed up (it is re-creatable). */
    fun store(context: Context): StudioStore = StudioStore(File(context.applicationContext.noBackupFilesDir, FOLDER))

    /** The audio key for a book read with [style], [voices] and pronunciation [fixes] (ADR-039). */
    fun signature(context: Context, style: SpeechStyle, voices: (Lang) -> VoiceId?, fixes: String): StudioSignature =
        StudioSignature(
            pipelineVersion = PIPELINE_VERSION,
            appStamp = appStamp(context),
            style = style,
            voices = Lang.entries.associateWith(voices),
            fixes = fixes,
            punctuation = PunctuationModels.get(context).isInstalled(),
        )

    /**
     * The app's last install or update time: every new build may render differently, so its audio is not reused.
     * Read once per process (an update restarts the process); playback asks for every sentence.
     */
    private fun appStamp(context: Context): Long = stamp ?: try {
        context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
    } catch (e: PackageManager.NameNotFoundException) {
        AudoraLog.w(TAG, "Own package not found; studio audio keyed without the build", e)
        0L
    }.also { stamp = it }

    @Volatile
    private var stamp: Long? = null
}
