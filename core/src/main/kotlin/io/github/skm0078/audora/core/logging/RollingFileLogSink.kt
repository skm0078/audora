// File: core/src/main/kotlin/io/github/skm0078/audora/core/logging/RollingFileLogSink.kt
// Purpose: Appends log lines to a size-capped set of files so friends can share logs without telemetry.

package io.github.skm0078.audora.core.logging

import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * Writes synchronously and flushes every entry, so the last lines before a crash reach disk.
 * Wrap it in [AsyncLogSink] to keep file I/O off the caller's thread.
 *
 * Disk problems are reported to stderr and the entry is dropped; logging never crashes the app.
 */
class RollingFileLogSink(private val directory: File, private val config: LogFileConfig = LogFileConfig()) :
    LogSink,
    Closeable {
    private val lock = Any()
    private var output: OutputStream? = null
    private var currentSize: Long = 0

    /** Existing log files, oldest first; what "Share logs" will attach. */
    fun logFiles(): List<File> = (config.maxFiles - 1 downTo 0)
        .map { File(directory, config.fileName(it)) }
        .filter { it.exists() }

    override fun write(entry: LogEntry) {
        val bytes = LogFormatter.format(entry).toByteArray(Charsets.UTF_8)
        synchronized(lock) {
            try {
                var stream = output ?: open()
                if (currentSize > 0 && currentSize + bytes.size > config.maxFileBytes) {
                    rotate()
                    stream = open()
                }
                stream.write(bytes)
                stream.flush()
                currentSize += bytes.size
            } catch (e: IOException) {
                System.err.println("RollingFileLogSink: write failed in $directory: $e")
                closeQuietly()
            }
        }
    }

    override fun close() = synchronized(lock) { closeQuietly() }

    private fun open(): OutputStream {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create log directory $directory")
        val file = File(directory, config.fileName(0))
        currentSize = file.length()
        return FileOutputStream(file, true).also { output = it }
    }

    private fun rotate() {
        closeQuietly()
        File(directory, config.fileName(config.maxFiles - 1)).delete()
        for (index in config.maxFiles - 2 downTo 0) {
            val from = File(directory, config.fileName(index))
            if (from.exists() && !from.renameTo(File(directory, config.fileName(index + 1)))) {
                throw IOException("Cannot rotate log file $from")
            }
        }
        currentSize = 0
    }

    private fun closeQuietly() {
        try {
            output?.close()
        } catch (e: IOException) {
            System.err.println("RollingFileLogSink: close failed: $e")
        }
        output = null
    }
}
