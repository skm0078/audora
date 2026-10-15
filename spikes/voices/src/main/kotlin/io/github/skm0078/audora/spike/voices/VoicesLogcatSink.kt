// File: spikes/voices/src/main/kotlin/io/github/skm0078/audora/spike/voices/VoicesLogcatSink.kt
// Purpose: Sends spike log entries to logcat. A copy of :app's LogcatLogSink, since a spike must not depend on :app.

package io.github.skm0078.audora.spike.voices

import android.util.Log
import io.github.skm0078.audora.core.logging.LogEntry
import io.github.skm0078.audora.core.logging.LogLevel
import io.github.skm0078.audora.core.logging.LogSink

class VoicesLogcatSink(private val minLevel: LogLevel) : LogSink {
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
        const val TAG_PREFIX = "Audora."
    }
}
