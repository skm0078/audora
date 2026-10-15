// File: spikes/playback/src/main/kotlin/io/github/skm0078/audora/spike/playback/SpikeApplication.kt
// Purpose: Starts logging, then checks whether the previous run's process was killed mid-run.

package io.github.skm0078.audora.spike.playback

import android.app.Application
import android.os.Build
import io.github.skm0078.audora.core.logging.AsyncLogSink
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.logging.LogLevel
import io.github.skm0078.audora.core.logging.RollingFileLogSink
import io.github.skm0078.audora.spike.playback.device.ExitReasons
import io.github.skm0078.audora.spike.playback.service.RunStore
import java.io.File

class SpikeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AudoraLog.install(SpikeLogcatSink(LogLevel.DEBUG), AsyncLogSink(RollingFileLogSink(logDirectory(this))))
        AudoraLog.i(
            TAG,
            "Process started: ${BuildConfig.APPLICATION_ID} ${BuildConfig.VERSION_NAME} on " +
                "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
        )
        // A fresh process cannot own a live run, so a run still marked active was killed with its process.
        RunStore(this).markOrphanedRun(ExitReasons.lastExitSummary(this))
    }

    companion object {
        private const val TAG = "S3.App"
        private const val LOG_DIRECTORY = "logs"

        fun logDirectory(app: Application): File = File(app.filesDir, LOG_DIRECTORY)
    }
}
