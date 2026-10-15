// File: app/src/main/kotlin/io/github/skm0078/audora/logging/AppLogging.kt
// Purpose: Wires the :core logging facade to logcat and the rolling log file, and records crashes.

package io.github.skm0078.audora.logging

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.skm0078.audora.BuildConfig
import io.github.skm0078.audora.core.logging.AsyncLogSink
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.logging.LogLevel
import io.github.skm0078.audora.core.logging.RollingFileLogSink
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLogging @Inject constructor(@ApplicationContext private val context: Context) {
    /** The on-disk log; P1-07 "Share logs" reads [RollingFileLogSink.logFiles] from here. */
    val fileSink: RollingFileLogSink by lazy { RollingFileLogSink(File(context.filesDir, LOG_DIRECTORY)) }

    private val asyncFileSink by lazy { AsyncLogSink(fileSink) }

    /** Writes every queued line to disk, so Share logs (P1-07) includes the newest ones. Blocks; false on timeout. */
    fun flush(): Boolean = asyncFileSink.flush(SHARE_FLUSH_TIMEOUT_MS)

    fun start() {
        val logcatMinLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.INFO
        AudoraLog.install(LogcatLogSink(logcatMinLevel), asyncFileSink)
        installCrashRecorder()
        AudoraLog.i(
            TAG,
            "Started ${BuildConfig.APPLICATION_ID} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) " +
                "on ${Build.MANUFACTURER} ${Build.MODEL}, " +
                "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
        )
    }

    private fun installCrashRecorder() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            AudoraLog.e(TAG, "Uncaught exception on thread '${thread.name}'", throwable)
            asyncFileSink.flush(CRASH_FLUSH_TIMEOUT_MS)
            // Hand over to the platform handler so Android still shows its crash dialog and kills the process.
            previous?.uncaughtException(thread, throwable)
        }
    }

    private companion object {
        const val TAG = "AppLogging"
        const val LOG_DIRECTORY = "logs"

        /** Upper bound on how long a crash waits for its stack trace to reach disk. */
        const val CRASH_FLUSH_TIMEOUT_MS = 1_000L

        /** Upper bound on how long Share logs waits for queued lines before zipping the files. */
        const val SHARE_FLUSH_TIMEOUT_MS = 2_000L
    }
}
