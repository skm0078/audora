// File: app/src/main/kotlin/io/github/skm0078/audora/ui/made/MadeRoutes.kt
// Purpose: The Jobs screen, the Made list and the in-app player as one route for the activity (ADR-067, ADR-068),
//          so each new screen does not add to MainActivity.

package io.github.skm0078.audora.ui.made

import androidx.compose.runtime.Composable
import io.github.skm0078.audora.ui.AppDestination
import io.github.skm0078.audora.ui.jobs.JobsRoute

/** Jobs → Made → player, and back the same way; a player opened from a book's details goes back to the library. */
@Composable
fun JobsAndMadeRoute(destination: AppDestination, navigate: (AppDestination) -> Unit) {
    when (destination) {
        AppDestination.Made -> MadeRoute(
            onPlay = { navigate(AppDestination.MadePlayer(it, fromMade = true)) },
            onBack = { navigate(AppDestination.Jobs) },
        )

        is AppDestination.MadePlayer -> MadePlayerRoute(destination.setKey) {
            navigate(destination.parent ?: AppDestination.Library)
        }

        else -> JobsRoute(onOpenMade = { navigate(AppDestination.Made) }) { navigate(AppDestination.Library) }
    }
}
