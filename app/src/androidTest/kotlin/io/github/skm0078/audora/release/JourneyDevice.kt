// File: app/src/androidTest/kotlin/io/github/skm0078/audora/release/JourneyDevice.kt
// Purpose: What the release journey needs from the device itself: test PDFs placed in Downloads the way a Files app
//          holds them, "Open with" intents, the shell (Back key, permissions), polling, and the app's log lines.

package io.github.skm0078.audora.release

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import io.github.skm0078.audora.MainActivity
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.logging.LogEntry
import io.github.skm0078.audora.core.logging.LogLevel
import io.github.skm0078.audora.core.logging.LogSink
import io.github.skm0078.audora.logging.LogcatLogSink
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.fail

object JourneyDevice {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    /** The app under test (its resources, files and services). */
    val app: Context get() = instrumentation.targetContext

    private val lines = CopyOnWriteArrayList<String>()

    /** Keeps every app log line in memory as well as in logcat, so a step can wait for the service to say it acted. */
    fun captureLogs() {
        AudoraLog.install(LogcatLogSink(LogLevel.DEBUG), LogSink { entry: LogEntry -> lines += entry.message })
    }

    fun logCount(fragment: String): Int = lines.count { fragment in it }

    /**
     * Copies a PDF from the test's assets into Download/ListenLockTest through MediaStore, as a browser or Files app
     * saves it, and returns its content URI: what "Open with" hands the app.
     */
    fun downloadedPdf(asset: String): Uri {
        val resolver = app.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, asset)
            put(MediaStore.MediaColumns.MIME_TYPE, PDF)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/ListenLockTest")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)) {
            "MediaStore refused a new download for $asset"
        }
        instrumentation.context.assets.open("pdfs/$asset").use { input ->
            requireNotNull(resolver.openOutputStream(uri)) { "No output stream for $uri" }.use { input.copyTo(it) }
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        return uri
    }

    /** The intent Files sends for "Open with ListenLock". */
    fun openWith(uri: Uri): Intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, PDF)
        .setClass(app, MainActivity::class.java)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    /** Runs [command] as the shell user and waits for it to finish. */
    fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command),
    ).use { String(it.readBytes()) }

    fun pressBackKey() {
        shell("input keyevent 4")
    }

    fun hasPermission(permission: String): Boolean = app.checkSelfPermission(permission) == 0

    /** The app's activity on screen now, or null while another app's window (a permission dialog) covers it. */
    fun resumedActivity(): Activity? {
        var resumed: Activity? = null
        instrumentation.runOnMainSync {
            resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull()
        }
        return resumed
    }

    fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    /** Polls [condition] off the main thread; fails with [what] and [state] when it never holds. */
    fun awaitTrue(
        what: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        state: () -> String = { "" },
        condition: () -> Boolean,
    ) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (condition()) return
            Thread.sleep(POLL_MS)
        }
        fail("Timed out after $timeoutMs ms waiting for: $what. ${state()}")
    }

    private const val PDF = "application/pdf"
    private const val POLL_MS = 200L
    const val DEFAULT_TIMEOUT_MS = 30_000L
}
