// File: app/src/main/kotlin/io/github/skm0078/audora/ui/voices/VoicesRoute.kt
// Purpose: Wires the Voices screen to its controller: state, actions, stopping a sample on leave; the build's list.

package io.github.skm0078.audora.ui.voices

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.skm0078.audora.BuildConfig
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.speech.voices.VoiceListConfig
import io.github.skm0078.audora.speech.voices.VoiceRow
import io.github.skm0078.audora.ui.reader.VoicePackActions

/** The Play build's shorter voice list (ADR-056); the dev build lists every voice. */
internal val BUILD_VOICE_LIST = VoiceListConfig(simple = BuildConfig.SIMPLE_VOICES)

/** The reader's language-pack card downloads the same way this screen does. */
internal fun voicePackActions(manager: VoiceManager) = VoicePackActions(
    onDownload = { manager.download(it.id.name) },
    onCancel = { manager.cancel(it.id.name) },
)

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
