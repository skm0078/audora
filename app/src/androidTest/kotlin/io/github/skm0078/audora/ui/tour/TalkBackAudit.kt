// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/tour/TalkBackAudit.kt
// Purpose: What TalkBack would trip over on one screen: a control with nothing to say, a control too small to find
//          by touch, controls that all say the same thing, a control lying under another one, and a screen with no
//          heading to jump to.

package io.github.skm0078.audora.ui.tour

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull

/**
 * Problems on the screen whose merged semantics trees start at [roots] (every window, the topmost last). A control is
 * anything TalkBack can activate: a click, a long press, a switch or a slider. Its name is what TalkBack reads before
 * the role and state: its description or text, merged from what it holds. Its size is what TalkBack and touch use:
 * its touch bounds, which Compose widens to the minimum touch target where nothing else is in the way. A control cut
 * off by the edge of its window (a list item half scrolled away) is not measured. Within one window, controls that
 * read the same ("Delete", "Delete") cannot be told apart by ear, and two that overlap (a list row under a floating
 * button) are hard to reach by touch. Only the topmost window must have a heading: a menu has none ([needsHeading]
 * false).
 */
internal fun talkBackProblems(roots: List<SemanticsNode>, needsHeading: Boolean): List<String> {
    val problems = roots.flatMap { root ->
        val window = root.boundsInRoot
        // A sheet's scrim (Close sheet) fills its window and lies under everything on it: not a control to compare.
        val controls = root
            .descendants()
            .filter(::isControl)
            .filterNot { it.boundsInRoot.isEmpty || it.boundsInRoot.fills(window) }
            .toList()
        controls.mapNotNull { controlProblem(it, window) } + sameNames(controls) + overlaps(controls)
    }
    val top = roots.lastOrNull() ?: return problems
    val headed = (sequenceOf(top) + top.descendants()).any { SemanticsProperties.Heading in it.config }
    return if (needsHeading && !headed) problems + NO_HEADING else problems
}

private fun controlProblem(node: SemanticsNode, window: Rect): String? {
    val name = spokenName(node)
    val bounds = node.boundsInRoot
    if (bounds.isEmpty) return null // scrolled out of sight: TalkBack does not reach it either
    val density = node.layoutInfo.density.density
    val touch = node.touchBoundsInRoot
    val widthDp = touch.width / density
    val heightDp = touch.height / density
    val where = "${roleOf(node)} at ${bounds.left.toInt()},${bounds.top.toInt()} px"
    val whole = window.contains(touch.topLeft) && window.contains(touch.bottomRight - Offset(1f, 1f))
    return when {
        // Its semantics say what it is, for a control this audit cannot name from its parts.
        name.isBlank() -> "unnamed control: $where, ${widthDp.toInt()}x${heightDp.toInt()} dp, ${node.config}"

        whole && (widthDp < MIN_TARGET_DP || heightDp < minHeightDp(node)) ->
            "small target \"$name\": ${widthDp.toInt()}x${heightDp.toInt()} dp, $where"

        else -> null
    }
}

/**
 * Material's slider lays itself out 44 dp tall whatever it is given, and Compose widens its touch area to 48 dp for the
 * finger; TalkBack moves it with swipes, not by touch. So a slider is held to Material's own height.
 */
private fun minHeightDp(node: SemanticsNode): Float =
    if (SemanticsActions.SetProgress in node.config) MATERIAL_SLIDER_DP else MIN_TARGET_DP

/** One line per name that more than one control reads, with how many do. */
private fun sameNames(controls: List<SemanticsNode>): List<String> = controls
    .groupBy(::spokenName)
    .filter { (name, same) -> name.isNotBlank() && same.size > 1 }
    .map { (name, same) -> "${same.size} controls all read \"$name\"" }

/**
 * Each pair of controls where one covers the middle of the other, neither holding the other (a button inside a card
 * is fine). The middle is what a finger aims at.
 */
private fun overlaps(controls: List<SemanticsNode>): List<String> = controls.flatMapIndexed { i, a ->
    controls
        .drop(i + 1)
        .filter { b -> !a.holds(b) && !b.holds(a) && (a.coversMiddleOf(b) || b.coversMiddleOf(a)) }
        .map { b -> "\"${spokenName(a)}\" and \"${spokenName(b)}\" overlap at ${b.boundsInRoot.center.text()} px" }
}

private fun SemanticsNode.coversMiddleOf(other: SemanticsNode): Boolean =
    boundsInRoot.contains(other.boundsInRoot.center)

private fun SemanticsNode.holds(other: SemanticsNode): Boolean =
    generateSequence(other.parent) { it.parent }.any { it.id == id }

private fun Rect.fills(window: Rect): Boolean =
    left <= window.left && top <= window.top && right >= window.right && bottom >= window.bottom

private fun Offset.text(): String = "${x.toInt()},${y.toInt()}"

private fun isControl(node: SemanticsNode): Boolean = with(node.config) {
    SemanticsActions.OnClick in this || SemanticsActions.OnLongClick in this ||
        SemanticsProperties.ToggleableState in this || SemanticsActions.SetProgress in this
}

private fun spokenName(node: SemanticsNode): String = with(node.config) {
    listOfNotNull(
        getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" "),
        getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text },
        getOrNull(SemanticsProperties.EditableText)?.text,
    ).joinToString(" ").trim()
}

private fun roleOf(node: SemanticsNode): String =
    node.config.getOrNull(SemanticsProperties.Role)?.toString() ?: "control"

private fun SemanticsNode.descendants(): Sequence<SemanticsNode> =
    children.asSequence().flatMap { sequenceOf(it) + it.descendants() }

/** Android's and WCAG's smallest comfortable touch target. */
private const val MIN_TARGET_DP = 48f

/** Material 3's slider height: its thumb. */
private const val MATERIAL_SLIDER_DP = 44f

private const val NO_HEADING = "no heading (TalkBack cannot jump to the top of this screen)"
