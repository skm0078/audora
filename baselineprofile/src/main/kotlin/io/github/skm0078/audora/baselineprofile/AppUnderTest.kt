// File: baselineprofile/src/main/kotlin/io/github/skm0078/audora/baselineprofile/AppUnderTest.kt
// Purpose: The installed app the generator drives: its package, its own words for the buttons it taps, and a PDF
//          handed to it the way a Files app does ("Open with").

package io.github.skm0078.audora.baselineprofile

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry

internal class AppUnderTest private constructor(val packageName: String, private val context: Context) {
    private val resources: Resources = context.packageManager.getResourcesForApplication(packageName)

    /**
     * The app's own text for string resource [name], in the emulator's language. Looked up by name because this
     * module cannot see the app's R class; a renamed string fails here, by name, instead of a tap finding nothing.
     */
    @SuppressLint("DiscouragedApi")
    fun text(name: String): String {
        // The resource table carries the installed application id (.bench), not the code's namespace.
        val id = resources.getIdentifier(name, "string", packageName)
        check(id != 0) { "The app under test ($packageName) has no string named \"$name\"" }
        return resources.getString(id)
    }

    /**
     * Copies [asset] from this module's assets into Download/ListenLockProfile through MediaStore, as a browser saves
     * a PDF, and returns its content URI. Called once, before recording: every round opens the same book.
     */
    fun downloadedPdf(asset: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, asset)
            put(MediaStore.MediaColumns.MIME_TYPE, PDF)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/ListenLockProfile")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = checkNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)) {
            "MediaStore refused a new download for $asset"
        }
        context.assets.open(asset).use { input ->
            checkNotNull(resolver.openOutputStream(uri)) { "No output stream for $uri" }.use { input.copyTo(it) }
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        return uri
    }

    /** "Open with ListenLock" for [book]. Sent from this process, which owns the file and so may grant reading it. */
    fun openWith(book: Uri) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(book, PDF)
            .setComponent(ComponentName(packageName, MAIN_ACTIVITY))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(intent)
    }

    companion object {
        // The code's namespace: MainActivity's class name, whatever the build's application id (.bench, .debug).
        private const val APP_NAMESPACE = "io.github.skm0078.audora"
        private const val PDF = "application/pdf"
        private const val MAIN_ACTIVITY = "$APP_NAMESPACE.MainActivity"

        /** The app named by the "targetAppId" runner argument (baselineprofile/build.gradle.kts). */
        fun fromArguments(): AppUnderTest {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val packageName = checkNotNull(InstrumentationRegistry.getArguments().getString("targetAppId")) {
                "Runner argument targetAppId is missing"
            }
            return AppUnderTest(packageName, instrumentation.context)
        }
    }
}
