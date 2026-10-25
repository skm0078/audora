// File: app/src/main/kotlin/io/github/skm0078/audora/SampleBook.kt
// Purpose: "Try a sample": a short page about ListenLock, in the app's language, to hear it work in one tap.

package io.github.skm0078.audora

import android.content.Context

/**
 * The sample, as shared text, so it is saved and opened like text shared to ListenLock (ADR-066). [context] must be
 * the activity, whose resources are in the language picked in Settings. Written for the app, so it can be shipped.
 */
internal fun sampleBook(context: Context) =
    SharedText(title = context.getString(R.string.sample_title), text = context.getString(R.string.sample_text))
