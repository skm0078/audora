// File: app/src/main/kotlin/io/github/skm0078/audora/ui/settings/OcrModelsAction.kt
// Purpose: What a Settings model-pack row (OCR, punctuation) can ask its model manager to do.

package io.github.skm0078.audora.ui.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

enum class OcrModelsAction { DOWNLOAD, CANCEL, DELETE }

/** Runs [action] on a model manager: download and cancel at once, delete (disk work) on this scope. */
internal fun CoroutineScope.runModelAction(
    action: OcrModelsAction,
    download: () -> Unit,
    cancel: () -> Unit,
    delete: suspend () -> Unit,
) {
    when (action) {
        OcrModelsAction.DOWNLOAD -> download()
        OcrModelsAction.CANCEL -> cancel()
        OcrModelsAction.DELETE -> launch { delete() }
    }
}
