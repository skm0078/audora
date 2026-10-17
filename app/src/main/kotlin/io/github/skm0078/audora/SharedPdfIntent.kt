// File: app/src/main/kotlin/io/github/skm0078/audora/SharedPdfIntent.kt
// Purpose: Reads the book file, or shared text, out of an "Open with" (VIEW) or "Share" (SEND) intent.

package io.github.skm0078.audora

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

/** [title] from the sharing app's subject line, when it gives one. */
data class SharedText(val title: String?, val text: String) {
    /** A browser's "Share" of a page sends only its link; Audora never fetches pages, so there is nothing to read. */
    val isOnlyLink: Boolean get() = LINK.matches(text.trim())

    private companion object {
        val LINK = Regex("""https?://\S+""")
    }
}

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

    /**
     * Text shared from a browser, chat or notes app (plan item 42): a SEND of text with no file attached. Null for
     * anything else, including a shared .txt file (that comes through [uriOf]).
     */
    fun textOf(intent: Intent?): SharedText? {
        val textShare = intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true &&
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) == null
        val text = intent?.takeIf { textShare }?.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        return text?.takeIf { it.isNotBlank() }?.let {
            SharedText(intent.getStringExtra(Intent.EXTRA_SUBJECT)?.takeIf(String::isNotBlank), it)
        }
    }

    /** Only content URIs: a file URI from another app would need storage permission Audora never asks for. */
    private const val SCHEME_CONTENT = "content"
}
