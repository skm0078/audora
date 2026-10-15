// File: core/src/main/kotlin/io/github/skm0078/audora/core/logging/AudoraLog.kt
// Purpose: Logging facade used by every module. Sinks are installed once by the app at startup.

package io.github.skm0078.audora.core.logging

/**
 * Process-wide logging facade.
 *
 * A global object (like Timber) instead of an injected logger, so plain-Kotlin pipeline code in
 * :pdf and :speech can log without threading a logger through every pure function.
 * Before [install] is called, entries are dropped; the app installs sinks in Application.onCreate.
 *
 * Log with context (book, page, engine, voice), never the text of the user's PDF.
 */
object AudoraLog {
    @Volatile
    private var sinks: List<LogSink> = emptyList()

    @Volatile
    private var clock: () -> Long = System::currentTimeMillis

    /** Replaces all sinks. Call once at startup; tests may call it again. */
    fun install(vararg newSinks: LogSink, clock: () -> Long = System::currentTimeMillis) {
        this.clock = clock
        sinks = newSinks.toList()
    }

    fun d(tag: String, message: String) = log(LogLevel.DEBUG, tag, message, null)

    fun i(tag: String, message: String) = log(LogLevel.INFO, tag, message, null)

    fun w(tag: String, message: String, throwable: Throwable? = null) = log(LogLevel.WARN, tag, message, throwable)

    fun e(tag: String, message: String, throwable: Throwable? = null) = log(LogLevel.ERROR, tag, message, throwable)

    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        val current = sinks
        if (current.isEmpty()) return
        val entry =
            LogEntry(
                timestampMillis = clock(),
                level = level,
                tag = tag,
                message = message,
                threadName = Thread.currentThread().name,
                throwable = throwable,
            )
        current.forEach { sink -> writeSafely(sink, entry) }
    }

    @Suppress("TooGenericExceptionCaught") // A broken sink must never crash the caller or hide other sinks.
    private fun writeSafely(sink: LogSink, entry: LogEntry) {
        try {
            sink.write(entry)
        } catch (e: Exception) {
            System.err.println("AudoraLog: sink ${sink::class.java.name} failed: $e")
        }
    }
}
