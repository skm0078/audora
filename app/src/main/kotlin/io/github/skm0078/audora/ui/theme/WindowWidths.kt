// File: app/src/main/kotlin/io/github/skm0078/audora/ui/theme/WindowWidths.kt
// Purpose: Material's window size class breakpoints, without the adaptive library (no new dependency).

package io.github.skm0078.audora.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Android 16 ignores orientation and resizability locks from 600 dp up, so every screen lays out for any width.
 * Screens read their own width (BoxWithConstraints) and compare it with these, as Material's size classes do.
 */
object WindowWidths {
    /** A phone in landscape, a small foldable open, a tablet in portrait: tabs move to a rail, panes may split. */
    val MEDIUM = 600.dp

    /** A large tablet or a foldable open in landscape. */
    val EXPANDED = 840.dp

    /**
     * The widest a column of text or settings rows grows; beyond it lines get too long to read and rows too wide to
     * scan, so the column stays centred.
     */
    val READABLE = 640.dp
}
