// File: app/src/main/kotlin/io/github/skm0078/audora/ui/made/MadeRoutes.kt
// Purpose: The Made place, the Jobs screen and the in-app player as routes for the activity (ADR-067, ADR-068), so
//          each new screen does not add to MainActivity.

package io.github.skm0078.audora.ui.made

import androidx.compose.runtime.Composable
import io.github.skm0078.audora.ui.AppDestination
import io.github.skm0078.audora.ui.jobs.JobsRoute

/** The Made place itself: its list, with Jobs and each made set's player one tap away. */
@Composable
fun MadePlaceRoute(navigate: (AppDestination) -> Unit) {
    MadeRoute(
        onPlay = { navigate(AppDestination.MadePlayer(it, fromMade = true)) },
        onOpenJobs = { navigate(AppDestination.Jobs) },
    )
}

/** Jobs and a made set's player over the places; back goes to Made, or to the library for a book's own set. */
@Composable
fun JobsAndMadeRoute(destination: AppDestination, navigate: (AppDestination) -> Unit) {
    when (destination) {
        is AppDestination.MadePlayer -> MadePlayerRoute(destination.setKey) {
            navigate(destination.parent ?: AppDestination.Library)
        }

        else -> JobsRoute { navigate(AppDestination.Made) }
    }
}
