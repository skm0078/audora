// File: app/src/main/kotlin/io/github/skm0078/audora/ui/stats/StatsCardChoices.kt
// Purpose: The stats share card's choices: what it shows, its colours (four deep gradients on the brand's greens and
//          blues, and paper) and its shape.

@file:Suppress("MagicNumber") // A picture's palette is its literal values.

package io.github.skm0078.audora.ui.stats

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.stats.ListeningPeriod
import io.github.skm0078.audora.ui.share.CardShape

/** The card's choices; saved across rotation by the route. */
internal data class StatsCardChoices(
    val kind: StatsCardKind = StatsCardKind.MONTH,
    val look: StatsCardLook = StatsCardLook.FOREST,
    val shape: CardShape = CardShape.STORY,
)

/** What the card shows: one of the three periods, or the streak. */
internal enum class StatsCardKind(val period: ListeningPeriod) {
    MONTH(ListeningPeriod.MONTH),
    WEEK(ListeningPeriod.WEEK),
    YEAR(ListeningPeriod.YEAR),
    STREAK(ListeningPeriod.YEAR),
}

/**
 * A shared picture keeps its colours whatever the phone's theme, so these are fixed, not theme colours. [ink] is the
 * big text (at least 7:1 on the darker end of [brush]), [quiet] the captions and the mark (4.5:1 or more), [bar] the
 * bars and calendar days.
 */
internal enum class StatsCardLook(
    @StringRes val label: Int,
    private val top: Long,
    private val bottom: Long,
    val ink: Color,
    val quiet: Color,
    val bar: Color,
) {
    FOREST(R.string.stats_card_forest, 0xFF3E7A4E, 0xFF0F2A18, WHITE, Color(0xFFCFE3D3), Color(0xFFE6F2E8)),
    TEAL(R.string.stats_card_teal, 0xFF2F7A6E, 0xFF0E2C29, WHITE, Color(0xFFC8E6E0), Color(0xFFE2F4F0)),
    INDIGO(R.string.stats_card_indigo, 0xFF34508F, 0xFF121D3B, WHITE, Color(0xFFD3DCF2), Color(0xFFE6ECFA)),
    NIGHT(R.string.stats_card_night, 0xFF242B26, 0xFF0B0F0D, WHITE, Color(0xFFC9D2CB), Color(0xFFA8DDB4)),
    PAPER(
        R.string.stats_card_paper,
        0xFFF7FAF5,
        0xFFE3EBDD,
        Color(0xFF171D17),
        Color(0xFF414A40),
        Color(0xFF2E6B3F),
    ),
    ;

    /** A day or bar with nothing: the ink, faint. */
    val empty: Color get() = ink.copy(alpha = EMPTY_ALPHA)

    val brush: Brush get() = Brush.linearGradient(listOf(Color(top), Color(bottom)))

    /** The swatch shows the card's lighter corner, the colour the eye names it by. */
    val swatch: Color get() = Color(top)
}

private val WHITE = Color(0xFFFFFFFF)
private const val EMPTY_ALPHA = 0.18f
