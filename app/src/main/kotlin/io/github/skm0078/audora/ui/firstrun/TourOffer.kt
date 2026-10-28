// File: app/src/main/kotlin/io/github/skm0078/audora/ui/firstrun/TourOffer.kt
// Purpose: The tour over the places: the sheet that offers it once, and the step dots it shares with each stop
//          (ADR 2026-10-28-home-and-tour).

package io.github.skm0078.audora.ui.firstrun

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.ui.theme.AppSheet
import io.github.skm0078.audora.ui.theme.Dimens

/**
 * The tour as it stands over the places: its offer, a stop, or nothing. [firstBook] when the library holds just the
 * one book, so the offer can say it is in.
 */
@Composable
fun GuidedTourOverlay(tour: GuidedTour, targets: TourTargets, firstBook: Boolean) {
    when {
        tour.offering -> TourOffer(firstBook, onStart = tour::start, onLater = tour::close)
        else -> tour.stop?.let { stop -> TourSpotlight(stop, holeOf(stop, targets), tour) }
    }
}

/** The Listen stop points at the Continue card's Play; with no book read yet there is none, so at the first book. */
private fun holeOf(stop: TourStop, targets: TourTargets) =
    targets.boundsOf(stop.target) ?: targets.boundsOf(TourTarget.BOOK).takeIf { stop.target == TourTarget.PLAY }

/** Swiping the sheet away, or Back, answers Maybe later. */
@Composable
private fun TourOffer(firstBook: Boolean, onStart: () -> Unit, onLater: () -> Unit) {
    AppSheet(onDismissRequest = onLater, skipPartiallyExpanded = true) {
        Column(
            Modifier.navigationBarsPadding().padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceS),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
        ) {
            Box(
                Modifier
                    .size(OFFER_ICON_BOX)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(Dimens.SpaceL)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_library),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            val title = stringResource(R.string.tour_offer_title)
            Text(
                if (firstBook) stringResource(R.string.tour_offer_first_book) + "\n" + title else title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.tour_offer_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StepDots(current = -1)
            OfferButtons(onStart, onLater)
            Text(
                stringResource(R.string.tour_replay_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Two buttons of equal width, the tour's own on the end side where the thumb rests. */
@Composable
private fun OfferButtons(onStart: () -> Unit, onLater: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM)) {
        OutlinedButton(onClick = onLater, modifier = Modifier.weight(1f).heightIn(min = BUTTON_HEIGHT)) {
            Text(stringResource(R.string.tour_later), textAlign = TextAlign.Center)
        }
        Button(onClick = onStart, modifier = Modifier.weight(1f).heightIn(min = BUTTON_HEIGHT)) {
            Text(stringResource(R.string.tour_start), textAlign = TextAlign.Center)
        }
    }
}

/**
 * One dot per stop, the [current] one long; -1 for none. Silent for TalkBack: the stop's own "Step 2 of 5" says it.
 */
@Composable
internal fun StepDots(current: Int, modifier: Modifier = Modifier) {
    Row(modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(DOT)) {
        TourStop.entries.indices.forEach { index ->
            val on = index == current
            Box(
                Modifier
                    .size(width = if (on) DOT_LONG else DOT, height = DOT)
                    .background(
                        if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                        CircleShape,
                    ),
            )
        }
    }
}

private val OFFER_ICON_BOX = 64.dp
private val BUTTON_HEIGHT = 52.dp
private val DOT = 6.dp
private val DOT_LONG = 18.dp
