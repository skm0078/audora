// File: app/src/main/kotlin/io/github/skm0078/audora/ui/settings/TourGroup.kt
// Purpose: You's first row, Show me around, which replays the 1-minute tour (ADR 2026-10-28-home-and-tour).

package io.github.skm0078.audora.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.skm0078.audora.R
import io.github.skm0078.audora.ui.theme.LinkOption
import io.github.skm0078.audora.ui.theme.OptionGroup

/** First on You, where a listener who skipped the tour looks for help finding their way. */
@Composable
internal fun TourGroup(onShowTour: () -> Unit) {
    OptionGroup(title = null) {
        SettingAnchor(SettingKey.TOUR) {
            LinkOption(
                stringResource(R.string.settings_tour_title),
                onClick = onShowTour,
                subtitle = stringResource(R.string.settings_tour_body),
            )
        }
    }
}
