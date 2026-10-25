// File: app/src/main/kotlin/io/github/skm0078/audora/ui/licences/LicencesRoute.kt
// Purpose: Settings → "Voices & licences" (P3-06) wired to its controller, loaded again on each visit.

package io.github.skm0078.audora.ui.licences

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Loaded on each visit: a voice downloaded since then shows its licence files. */
@Composable
fun LicencesRoute(source: LicenceSource, navigateBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val controller = remember { LicencesController(source, scope).also { it.start() } }
    val state by controller.state.collectAsStateWithLifecycle()
    LicencesScreen(
        state = state,
        onBack = navigateBack,
        onOpenTexts = controller::openTexts,
        onCloseTexts = controller::closeTexts,
    )
}
