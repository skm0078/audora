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
 * The header and, while [open], its [content], as one column. [header] gets the toggle to put on its tap: opening
 * through it brings the whole group into view on the next frame (the header and as many options as fit, the header at
 * the top when the group is taller than the sheet) and hands keyboard, D-pad and switch focus to the first option.
 * A group restored open after a rotation, or opened from the start by a shortcut, never moves anything.
 *
 * TalkBack's focus is left on the header on purpose: it is the standard pattern for a section that expands, the
 * header says "expanded", the next swipe reads the first option and the header is still there to close it.
 */
@Composable
fun InPlaceGroup(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    header: @Composable (toggle: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = Dimens.SpaceM,
    content: @Composable ColumnScope.() -> Unit,
) {
    val view = remember { BringIntoViewRequester() }
    val focus = remember { FocusRequester() }
    // Set only by the header's tap, so only a listener's opening reveals the group.
    var revealPending by remember { mutableStateOf(false) }
    Column(modifier.bringIntoViewRequester(view), verticalArrangement = Arrangement.spacedBy(spacing)) {
        header {
            revealPending = !open
            onOpenChange(!open)
        }
        if (open) {
            // A focus group: focusing it enters at its first option.
            Column(
                Modifier.focusRequester(focus).focusGroup(),
                verticalArrangement = Arrangement.spacedBy(spacing),
                content = content,
            )
            // Starts each time the options enter; keyed on nothing, so clearing the flag cannot restart and cancel it.
            LaunchedEffect(Unit) {
                if (!revealPending) return@LaunchedEffect
                revealPending = false
                withFrameNanos { } // the options laid out first, so there is something to scroll to
                view.bringIntoView()
                // In touch mode nothing takes input focus, so this is false there and changes nothing.
                focus.requestFocus()
            }
        }
    }
}
