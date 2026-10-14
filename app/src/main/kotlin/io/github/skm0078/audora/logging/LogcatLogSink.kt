// File: app/src/main/kotlin/io/github/skm0078/audora/logging/LogcatLogSink.kt
// Purpose: Sends log entries to Android logcat, for adb debugging.

package io.github.skm0078.audora.logging

import android.util.Log
import io.github.skm0078.audora.core.logging.LogEntry
import io.github.skm0078.audora.core.logging.LogLevel
import io.github.skm0078.audora.core.logging.LogSink

class LogcatLogSink(private val minLevel: LogLevel) : LogSink {
    override fun write(entry: LogEntry) {
        if (entry.level < minLevel) return
        val tag = "$TAG_PREFIX${entry.tag}"
        when (entry.level) {
            LogLevel.DEBUG -> Log.d(tag, entry.message, entry.throwable)
            LogLevel.INFO -> Log.i(tag, entry.message, entry.throwable)
            LogLevel.WARN -> Log.w(tag, entry.message, entry.throwable)
            LogLevel.ERROR -> Log.e(tag, entry.message, entry.throwable)
        }
    }

    private companion object {
        /** Lets `adb logcat -s` style filters catch every Audora line by prefix. */
        const val TAG_PREFIX = "Audora."
    }
}
