// File: app/src/main/kotlin/io/github/skm0078/audora/ui/voices/VoicesActions.kt
// Purpose: The user actions of the Voices screen, forwarded by MainActivity to VoicesController.

package io.github.skm0078.audora.ui.voices

import android.net.Uri
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.speech.voices.VoiceRow

/** What the Voices screen can ask for; [voicesActions] forwards each to [VoicesController]. */
@Suppress("TooManyFunctions") // One callback per user action on the screen.
interface VoicesActions {
    fun onBack()

    fun onDownload(row: VoiceRow)

    fun onCancel(row: VoiceRow)

    fun onUse(row: VoiceRow)

    fun onUseDefault(lang: Lang)

    fun onRequestDelete(row: VoiceRow)

    fun onConfirmDelete()

    fun onCancelDelete()

    fun onImport(treeUri: Uri, lang: Lang, label: String)

    fun onMessageShown()

    /** Plays a sample of [row]'s voice, or stops it if it is playing; screens without samples ignore it. */
    fun onSample(row: VoiceRow) = Unit
}
