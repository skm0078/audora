// File: spikes/voices/src/main/kotlin/io/github/skm0078/audora/spike/voices/VoicesApplication.kt
// Purpose: Starts logging and owns the one VoicesController, so a benchmark outlives activity recreation.

package io.github.skm0078.audora.spike.voices

import android.app.Application
import android.os.Build
import io.github.skm0078.audora.core.logging.AsyncLogSink
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.logging.LogLevel
import io.github.skm0078.audora.core.logging.RollingFileLogSink
import java.io.File

class VoicesApplication : Application() {
    lateinit var controller: VoicesController
        private set

    override fun onCreate() {
        super.onCreate()
        AudoraLog.install(VoicesLogcatSink(LogLevel.DEBUG), AsyncLogSink(RollingFileLogSink(logDirectory(this))))
        AudoraLog.i(
            TAG,
            "Process started: ${BuildConfig.APPLICATION_ID} ${BuildConfig.VERSION_NAME} on " +
                "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
        )
        controller = VoicesController(this)
    }

    companion object {
        private const val TAG = "S1.App"
        private const val LOG_DIRECTORY = "logs"

        fun logDirectory(app: Application): File = File(app.filesDir, LOG_DIRECTORY)
    }
}
