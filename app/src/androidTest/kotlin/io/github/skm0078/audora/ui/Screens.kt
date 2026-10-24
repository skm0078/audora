// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/Screens.kt
// Purpose: Saves a device test's screenshot where CI collects it for review (the "player-screens" artifact).

package io.github.skm0078.audora.ui

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry

object Screens {
    private const val FOLDER = "Download/ListenLock-screens"
    private const val PNG_QUALITY = 100

    /** Into Downloads/ListenLock-screens, which outlives the test app's uninstall; CI pulls it into an artifact. */
    fun save(name: String, bitmap: Bitmap) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "$name.png")
            put(MediaStore.Downloads.MIME_TYPE, "image/png")
            put(MediaStore.Downloads.RELATIVE_PATH, FOLDER)
        }
        val resolver = context.contentResolver
        val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)) { "No $name" }
        requireNotNull(resolver.openOutputStream(uri)) { "Cannot write $name" }.use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)) { "PNG of $name failed" }
        }
    }
}
