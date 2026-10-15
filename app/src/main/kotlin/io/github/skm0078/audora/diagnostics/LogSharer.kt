// File: app/src/main/kotlin/io/github/skm0078/audora/diagnostics/LogSharer.kt
// Purpose: Settings → Share logs: zips the summary and log files into the cache and builds the share-sheet intent.

package io.github.skm0078.audora.diagnostics

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.skm0078.audora.BuildConfig
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.config.DiagnosticsConfig
import io.github.skm0078.audora.core.diagnostics.BuildAndDevice
import io.github.skm0078.audora.core.diagnostics.DiagnosticsSummary
import io.github.skm0078.audora.core.diagnostics.LogBundleError
import io.github.skm0078.audora.core.diagnostics.LogBundleWriter
import io.github.skm0078.audora.core.diagnostics.ProcessExitSource
import io.github.skm0078.audora.core.diagnostics.ReadingSessionTracker
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.map
import io.github.skm0078.audora.logging.AppLogging
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The zip lives in `cacheDir/shared-logs/`, exposed only through this app's FileProvider with a
 * one-off read grant to the app the user picks. The previous zip is deleted when a new one is made,
 * not right after sharing, because the receiving app may read it later.
 */
@Singleton
class LogSharer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appLogging: AppLogging,
    private val exitSource: ProcessExitSource,
    private val tracker: ReadingSessionTracker,
    private val config: DiagnosticsConfig,
) {
    private val writer = LogBundleWriter()

    /** Blocking file I/O: call from a background thread. */
    fun prepareShareIntent(): Outcome<Intent, LogBundleError> {
        val now = System.currentTimeMillis()
        AudoraLog.i(TAG, "Preparing logs to share")
        if (!appLogging.flush()) AudoraLog.w(TAG, "Log queue did not drain in time; the newest lines may be missing")

        val dir = File(context.cacheDir, SHARE_DIRECTORY)
        dir.listFiles()?.forEach { old ->
            if (!old.delete()) AudoraLog.w(TAG, "Could not delete old log bundle ${old.name}")
        }
        val stamp = SimpleDateFormat(FILE_STAMP_PATTERN, Locale.US).format(Date(now))
        val target = File(dir, "${config.bundleBaseName}-$stamp.zip")
        val summary = DiagnosticsSummary.render(
            createdAtMillis = now,
            build = buildAndDevice(),
            exits = exitSource.recentExits().take(config.maxExitsInSummary),
            unfinished = tracker.unfinishedSession(),
        )
        return writer.write(target, summary, appLogging.fileSink.logFiles()).map { zip ->
            AudoraLog.i(TAG, "Log bundle ready: ${zip.name}, ${zip.length()} bytes")
            shareIntent(zip)
        }
    }

    private fun shareIntent(zip: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", zip)
        val subject = context.getString(R.string.share_logs_subject, BuildConfig.VERSION_NAME, Build.MODEL)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = ZIP_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_logs_body))
            // ClipData carries the grant through the chooser to the app the user picks.
            clipData = ClipData.newRawUri(zip.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, context.getString(R.string.share_logs_chooser_title))
    }

    private fun buildAndDevice() = BuildAndDevice(
        applicationId = BuildConfig.APPLICATION_ID,
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE.toLong(),
        manufacturer = Build.MANUFACTURER,
        model = Build.MODEL,
        androidRelease = Build.VERSION.RELEASE,
        sdkInt = Build.VERSION.SDK_INT,
    )

    private companion object {
        const val TAG = "LogShare"

        /** Must match res/xml/log_share_paths.xml and the provider authority in the manifest. */
        const val SHARE_DIRECTORY = "shared-logs"
        const val AUTHORITY_SUFFIX = ".logs"
        const val ZIP_MIME_TYPE = "application/zip"
        const val FILE_STAMP_PATTERN = "yyyyMMdd-HHmmss"
    }
}
