// File: app/src/main/kotlin/io/github/skm0078/audora/SharedPdfIntent.kt
// Purpose: Reads the book file, pictures or text out of an "Open with" (VIEW), "Share" (SEND, SEND_MULTIPLE) or
//          text-selection "Read aloud" (PROCESS_TEXT) intent.

package io.github.skm0078.audora

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import io.github.skm0078.audora.core.logging.AudoraLog

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
     * Pictures opened or shared to be read as one book (ADR-066): a VIEW, SEND or SEND_MULTIPLE whose content is an
     * image by [typeOf] (the provider's MIME type) or, failing that, the intent's own type. Empty for anything else,
     * which then goes through [uriOf] and [textOf].
     */
    fun imagesOf(intent: Intent?, typeOf: (Uri) -> String?): List<Uri> {
        val uris: List<Uri> = when (intent?.action) {
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)

            Intent.ACTION_SEND -> listOfNotNull(stream(intent))

            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()

            else -> emptyList()
        }
        return uris.filter { uri ->
            uri.scheme == SCHEME_CONTENT && isImage(safeType(typeOf, uri) ?: intent?.type)
        }
    }

    /**
     * Text to read now (plan item 42, ADR-066): a SEND of text with no file attached, from a browser, chat or notes
     * app, or text selected in any app and sent with "Read aloud" (PROCESS_TEXT). Null for anything else, including
     * a shared .txt file (that comes through [uriOf]).
     */
    fun textOf(intent: Intent?): SharedText? {
        if (intent?.action == Intent.ACTION_PROCESS_TEXT) {
            val selected = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            return selected?.takeIf { it.isNotBlank() }?.let { SharedText(title = null, text = it) }
        }
        val textShare = intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true &&
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) == null
        val text = intent?.takeIf { textShare }?.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        return text?.takeIf { it.isNotBlank() }?.let {
            SharedText(intent.getStringExtra(Intent.EXTRA_SUBJECT)?.takeIf(String::isNotBlank), it)
        }
    }

    /** A picture ListenLock reads with OCR: the photo picker's, the camera's and the web's usual formats. */
    fun isImage(mimeType: String?): Boolean = mimeType?.lowercase() in IMAGE_MIME_TYPES

    private fun stream(intent: Intent): Uri? =
        IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)

    /** A provider that has gone away throws instead of answering; the intent's own type is used then. */
    private fun safeType(typeOf: (Uri) -> String?, uri: Uri): String? = try {
        typeOf(uri)
    } catch (e: SecurityException) {
        AudoraLog.w(TAG, "No type for a shared picture: ${e.javaClass.simpleName}")
        null
    }

    /** The picture formats every Android 12 phone decodes (HEIC and HEIF since Android 9). */
    val IMAGE_MIME_TYPES = setOf("image/png", "image/jpeg", "image/jpg", "image/webp", "image/heic", "image/heif")

    private const val TAG = "Audora.Share"

    /** Only content URIs: a file URI from another app would need storage permission Audora never asks for. */
    private const val SCHEME_CONTENT = "content"
}
