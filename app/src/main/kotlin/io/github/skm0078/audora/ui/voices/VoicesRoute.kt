// File: app/src/main/kotlin/io/github/skm0078/audora/ui/voices/VoicesRoute.kt
// Purpose: Wires the Voices screen to its controller: state collection, actions, and stopping a sample on leave.

package io.github.skm0078.audora.ui.voices

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.speech.voices.VoiceRow

@Composable
internal fun VoicesRoute(controller: VoicesController, navigateBack: () -> Unit) {
    val state by controller.state.collectAsStateWithLifecycle()
    val actions = remember(controller) { voicesActions(controller, navigateBack) }
    // A sample never plays on after the screen is left.
    DisposableEffect(controller) { onDispose { controller.stopSample() } }
    VoicesScreen(state = state, actions = actions)
}

internal fun voicesActions(controller: VoicesController, navigateBack: () -> Unit): VoicesActions =
    object : VoicesActions {
        override fun onBack() = navigateBack()

        override fun onDownload(row: VoiceRow) = controller.download(row)

        override fun onCancel(row: VoiceRow) = controller.cancel(row)

        override fun onUse(row: VoiceRow) = controller.use(row)

        override fun onUseDefault(lang: Lang) = controller.useDefault(lang)

        override fun onRequestDelete(row: VoiceRow) = controller.proposeDelete(row)

        override fun onConfirmDelete() = controller.confirmDelete()

        override fun onCancelDelete() = controller.proposeDelete(null)

        override fun onImport(treeUri: Uri, lang: Lang, label: String) =
            controller.import(treeUri.toString(), lang, label)

        override fun onMessageShown() = controller.messageShown()

        override fun onSample(row: VoiceRow) = controller.toggleSample(row)
    }
