// File: app/src/main/kotlin/io/github/skm0078/audora/ui/pronunciation/FixDraft.kt
// Purpose: What PronunciationFixDialog hands its caller on Add; the caller adds the language and where it applies.

package io.github.skm0078.audora.ui.pronunciation

/** A fix as typed: [written] as the book prints it, [spoken] as it should sound; both untrimmed. */
internal data class FixDraft(val written: String, val spoken: String, val wholeWord: Boolean)
