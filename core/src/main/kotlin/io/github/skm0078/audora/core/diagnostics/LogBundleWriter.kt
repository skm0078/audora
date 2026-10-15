// File: core/src/main/kotlin/io/github/skm0078/audora/core/diagnostics/LogBundleWriter.kt
// Purpose: Packs the summary and the rolling log files into one zip a friend can send in a chat or email.

package io.github.skm0078.audora.core.diagnostics

import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * One zip instead of several files: every chat and mail app accepts a single attachment, and text logs
 * compress about tenfold, so the 6 MB worst case travels as well under 1 MB.
 */
class LogBundleWriter {
    /**
     * Writes [summary] as `summary.txt` followed by [logFiles] (oldest first, kept under their own names)
     * into [target], via a temp file so a failed write never leaves a half zip under the final name.
     * A log file that vanished (rotated away meanwhile) is skipped and noted in the log.
     */
    fun write(target: File, summary: String, logFiles: List<File>): Outcome<File, LogBundleError> {
        val dir = target.parentFile
        if (dir != null && !dir.isDirectory && !dir.mkdirs()) {
            return failure(target, IOException("Cannot create $dir"))
        }
        val temp = File(dir, "${target.name}$TEMP_SUFFIX")
        return try {
            ZipOutputStream(FileOutputStream(temp)).use { zip ->
                zip.putNextEntry(ZipEntry(SUMMARY_NAME))
                zip.write(summary.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                logFiles.forEach { file -> addFile(zip, file) }
            }
            if (!temp.renameTo(target)) throw IOException("Cannot rename $temp to $target")
            Outcome.Success(target)
        } catch (e: IOException) {
            temp.delete()
            failure(target, e)
        }
    }

    private fun addFile(zip: ZipOutputStream, file: File) {
        if (!file.isFile) {
            AudoraLog.w(TAG, "Log file ${file.name} disappeared before sharing; skipped")
            return
        }
        zip.putNextEntry(ZipEntry(file.name))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun failure(target: File, e: IOException): Outcome<File, LogBundleError> {
        AudoraLog.e(TAG, "Could not build log bundle ${target.path}", e)
        return Outcome.Failure(LogBundleError(e.toString()))
    }

    companion object {
        const val SUMMARY_NAME = "summary.txt"
        private const val TEMP_SUFFIX = ".tmp"
        private const val TAG = "LogBundle"
    }
}

/** The bundle could not be written; [message] says why (already logged with the path). */
data class LogBundleError(val message: String)
