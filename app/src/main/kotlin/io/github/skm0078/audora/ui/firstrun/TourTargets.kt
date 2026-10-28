// File: app/src/main/kotlin/io/github/skm0078/audora/ui/firstrun/TourTargets.kt
// Purpose: Where each thing the tour points at sits on screen: a modifier on the Play button, the first book and the
//          places' tabs reports its bounds, and the tour's spotlight reads them (ADR 2026-10-28-home-and-tour).

package io.github.skm0078.audora.ui.firstrun

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import io.github.skm0078.audora.ui.AppTab

/**
 * The bounds of each target, in the window's root coordinates. Each target is owned by the node that placed it last,
 * so a tab bar swapped for a rail on rotation cannot remove the rail's bounds when it leaves. A write is skipped when
 * the bounds did not move, so a still screen writes nothing.
 */
@Stable
class TourTargets {
    private val placed = mutableStateMapOf<TourTarget, Placed>()

    fun boundsOf(target: TourTarget): Rect? = placed[target]?.bounds

    internal fun place(target: TourTarget, owner: Any, bounds: Rect) {
        val now = placed[target]
        if (now?.owner !== owner || now.bounds != bounds) placed[target] = Placed(owner, bounds)
    }

    internal fun remove(target: TourTarget, owner: Any) {
        if (placed[target]?.owner === owner) placed.remove(target)
    }

    private class Placed(val owner: Any, val bounds: Rect)
}

/** Null outside the places' frame (a screen test, a sheet's own window), where nothing is pointed at. */
val LocalTourTargets = staticCompositionLocalOf<TourTargets?> { null }

/** Marks this as what the tour's stop for [target] points at. */
fun Modifier.tourTarget(target: TourTarget): Modifier = this then TourTargetElement(target)

/** Marks a place's tab, rail item or sidebar row; Library has no stop of its own, since the tour starts there. */
fun Modifier.tourTarget(tab: AppTab): Modifier = when (tab) {
    AppTab.LIBRARY -> this
    AppTab.NOTES -> tourTarget(TourTarget.NOTES)
    AppTab.MADE -> tourTarget(TourTarget.MADE)
    AppTab.YOU -> tourTarget(TourTarget.YOU)
}

private data class TourTargetElement(val target: TourTarget) : ModifierNodeElement<TourTargetNode>() {
    override fun create() = TourTargetNode(target)

    override fun update(node: TourTargetNode) {
        node.leave()
        node.target = target
    }
}

private class TourTargetNode(var target: TourTarget) :
    Modifier.Node(),
    GlobalPositionAwareModifierNode,
    CompositionLocalConsumerModifierNode {
    private var placedIn: TourTargets? = null

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val targets = currentValueOf(LocalTourTargets) ?: return
        placedIn = targets
        targets.place(target, this, coordinates.boundsInRoot())
    }

    override fun onDetach() = leave()

    fun leave() {
        placedIn?.remove(target, this)
        placedIn = null
    }
}
