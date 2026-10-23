// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/ParagraphSkipNotice.kt
// Purpose: Says briefly why a paragraph skip did nothing, instead of leaving the long-press silent.

package io.github.skm0078.audora.ui.reader

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import io.github.skm0078.audora.R

/**
 * A short toast for [refusal], then [onShown]. A toast, not a snackbar, so it needs no place in the reader's layout
 * and also shows over the full player, where the long-press lives.
 */
@Composable
fun ParagraphSkipNotice(refusal: ParagraphSkipRefusal?, onShown: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(refusal) {
        if (refusal == null) return@LaunchedEffect
        Toast.makeText(context, refusal.message, Toast.LENGTH_SHORT).show()
        onShown()
    }
}

@get:StringRes
private val ParagraphSkipRefusal.message: Int
    get() = when (this) {
        ParagraphSkipRefusal.NO_NEXT -> R.string.paragraph_skip_no_next
        ParagraphSkipRefusal.NO_PREVIOUS -> R.string.paragraph_skip_no_previous
    }
