// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/RemovalUndo.kt
// Purpose: How long a removed book can be brought back with Undo (CF-3), stretched for TalkBack users.

package io.github.skm0078.audora.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalAccessibilityManager

/**
 * Long enough to read the snackbar and reach Undo, short enough that the library is settled before the listener
 * moves on.
 */
const val REMOVAL_UNDO_MS = 8_000L

/** An Undo tapped as the snackbar closes reaches the controller before the removal starts. */
internal const val REMOVAL_UNDO_GRACE_MS = 1_000L

/**
 * [REMOVAL_UNDO_MS], or the longer time Android recommends for a message with a button when TalkBack or the phone's
 * "Time to take action" setting asks for it.
 */
@Composable
fun rememberRemovalUndoMs(): Long {
    val accessibility = LocalAccessibilityManager.current
    return remember(accessibility) {
        accessibility?.calculateRecommendedTimeoutMillis(
            REMOVAL_UNDO_MS,
            containsText = true,
            containsControls = true,
        ) ?: REMOVAL_UNDO_MS
    }
}
