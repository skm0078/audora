// File: app/src/main/kotlin/io/github/skm0078/audora/ui/theme/OpenInPlace.kt
// Purpose: A group that opens in place under its header scrolls itself into view and hands keyboard and switch focus
//          to its first option, so what just opened is never left below the edge of the sheet (phone test,
//          2026-10-30). Used by the Voice and speed groups, More options and Save as.

package io.github.skm0078.audora.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusGroup
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.Dp

/**
 * One group's opening. [opened] is called from the header's tap, so only a listener's opening moves anything: a
 * group restored open after a rotation, or opened from the start by a shortcut, stays where it is.
 *
 * TalkBack's focus is left on the header on purpose: it is the standard pattern for a section that expands, the
 * header says "expanded", the next swipe reads the first option and the header is still there to close it.
 */
@Stable
class InPlaceOpening internal constructor() {
    internal val view = BringIntoViewRequester()
    internal val focus = FocusRequester()
    internal var pending by mutableStateOf(false)

    /** The listener opened the group: reveal it once its options are laid out. */
    fun opened() {
        pending = true
    }
}

@Composable
fun rememberInPlaceOpening(): InPlaceOpening = remember { InPlaceOpening() }

/**
 * The header and, while [open], its [content], as one column that is brought into view as a whole: the header and as
 * many options as fit, the header at the top when the group is taller than the sheet.
 */
@Composable
fun InPlaceGroup(
    opening: InPlaceOpening,
    open: Boolean,
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = Dimens.SpaceM,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.bringIntoViewRequester(opening.view),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        header()
        if (open) {
            // A focus group: focusing it enters at its first option (with a keyboard, D-pad or switch).
            Column(
                Modifier.focusRequester(opening.focus).focusGroup(),
                verticalArrangement = Arrangement.spacedBy(spacing),
                content = content,
            )
            RevealWhenOpened(opening)
        }
    }
}

@Composable
private fun RevealWhenOpened(opening: InPlaceOpening) {
    LaunchedEffect(opening.pending) {
        if (!opening.pending) return@LaunchedEffect
        opening.pending = false
        withFrameNanos { } // the options laid out first, so there is something to scroll to
        opening.view.bringIntoView()
        // In touch mode nothing takes input focus, so this is false there and changes nothing.
        opening.focus.requestFocus()
    }
}
