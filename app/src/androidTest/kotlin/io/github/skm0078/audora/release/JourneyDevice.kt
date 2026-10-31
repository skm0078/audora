// File: app/src/androidTest/kotlin/io/github/skm0078/audora/release/JourneyDevice.kt
// Purpose: What the release journey needs from the device itself: test books placed in Downloads the way a Files app
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
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.ComposeTestRule
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

    /** The last [count] kept log lines, for a step's failure message. */
    fun logTail(count: Int): List<String> = lines.takeLast(count)

    /**
     * Copies a PDF from the test's assets into Download/ListenLockTest through MediaStore, as a browser or Files app
     * saves it, and returns its content URI: what "Open with" hands the app.
     */
    fun downloadedPdf(asset: String): Uri = downloaded("pdfs", asset, PDF)

    /**
     * Copies an e-book from the test's assets/ebooks the same way. Saved as plain bytes: MediaStore would rename a
     * file whose type it does not know, and the app tells a book's format from its bytes, never from its type.
     */
    fun downloadedBook(asset: String): Uri = downloaded("ebooks", asset, ANY_FILE)

    private fun downloaded(folder: String, asset: String, mimeType: String): Uri {
        val resolver = app.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, asset)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/ListenLockTest")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)) {
            "MediaStore refused a new download for $asset"
        }
        instrumentation.context.assets.open("$folder/$asset").use { input ->
            requireNotNull(resolver.openOutputStream(uri)) { "No output stream for $uri" }.use { input.copyTo(it) }
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        return uri
    }

    /** The intent Files sends for "Open with ListenLock". */
    fun openWith(uri: Uri, mimeType: String = PDF): Intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, mimeType)
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

    /** A switch or checkbox row by its title: the title is merged into the toggleable row. */
    fun toggleRow(title: String): SemanticsMatcher = hasText(title) and isToggleable()

    /**
     * Whether a node matching [matcher] is on screen. False, not a failure, while the screen cannot be read (see
     * [countNow]); a real miss still fails when the caller's wait times out.
     */
    fun ComposeTestRule.shows(matcher: SemanticsMatcher): Boolean = (countNow(matcher) ?: 0) > 0

    /**
     * Whether no node matching [matcher] is on screen: what a wait for something to go away polls. False while the
     * screen cannot be read, so a passing moment is not taken for the node being gone.
     */
    fun ComposeTestRule.hides(matcher: SemanticsMatcher): Boolean = countNow(matcher) == 0

    /**
     * How many nodes match [matcher] now, or null for a moment the tree cannot be read: one activity handing over to
     * the next with no composition yet (IllegalStateException), or the read landing mid layout as a card leaves the
     * reader ("performMeasureAndLayout called during measure layout"). Both pass on the next poll; the app's own
     * frames are not affected. The mid-layout one reaches here wrapped by Espresso's idle sync
     * (RuntimeException > ExecutionException > IllegalArgumentException), so it is told by its cause chain.
     */
    private fun ComposeTestRule.countNow(matcher: SemanticsMatcher): Int? = try {
        onAllNodes(matcher).fetchSemanticsNodes().size
    } catch (_: IllegalStateException) {
        null
    } catch (failure: RuntimeException) {
        if (!failure.isMidLayoutRead()) throw failure
        null
    }

    private fun Throwable.isMidLayoutRead(): Boolean {
        val chain = generateSequence(this) { it.cause }
        return chain.any { it is IllegalArgumentException && it.message?.contains(MID_LAYOUT) == true }
    }

    private const val MID_LAYOUT = "performMeasureAndLayout called during measure layout"
    private const val PDF = "application/pdf"
    private const val ANY_FILE = "application/octet-stream"
    private const val POLL_MS = 200L
    const val DEFAULT_TIMEOUT_MS = 30_000L
}
