// File: app/src/main/kotlin/io/github/skm0078/audora/ui/theme/Dimens.kt
// Purpose: One set of spacing and size tokens, so screens cannot drift apart (ADR-036).

package io.github.skm0078.audora.ui.theme

import androidx.compose.ui.unit.dp

/** Spacing steps and the few fixed sizes the screens share. Screens use these, never their own numbers. */
object Dimens {
    val SpaceXs = 4.dp
    val SpaceS = 8.dp
    val SpaceM = 12.dp
    val SpaceL = 16.dp
    val SpaceXl = 24.dp
    val SpaceXxl = 32.dp

    /** Left and right margin of every screen. */
    val ScreenPadding = 16.dp

    /** Material's minimum touch target; every tappable control is at least this big (item 39). */
    val TouchTarget = 48.dp

    /** The main play button: the biggest target on a listening screen, in the thumb's reach. */
    val PlayButton = 72.dp
    val PlayButtonSmall = 48.dp

    /** A book cover in the library grid; columns fit as many as this width allows. */
    val CoverGridMin = 104.dp
    val CoverMini = 40.dp
    val CoverSmall = 48.dp
    val CoverMedium = 72.dp

    /** A menu whose entries carry a line of explanation wraps it at this width instead of spanning a tablet. */
    val MenuMaxWidth = 320.dp

    /** Mini player height; it sits above the tabs on every tab. */
    val MiniPlayerHeight = 64.dp
}
