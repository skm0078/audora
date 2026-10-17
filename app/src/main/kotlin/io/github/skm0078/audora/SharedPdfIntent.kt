// File: app/src/main/kotlin/io/github/skm0078/audora/SharedPdfIntent.kt
// Purpose: Reads the PDF out of an "Open with" (VIEW) or "Share" (SEND) intent from another app.

package io.github.skm0078.audora

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

object SharedPdfIntent {
    /** The PDF's URI, or null when [intent] is not a VIEW or SEND of a PDF (the launcher's MAIN, for one). */
    fun uriOf(intent: Intent?): String? {
        val uri: Uri? = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        }
        return uri?.takeIf { it.scheme == SCHEME_CONTENT }?.toString()
    }

    /** Only content URIs: a file URI from another app would need storage permission Audora never asks for. */
    private const val SCHEME_CONTENT = "content"
}
