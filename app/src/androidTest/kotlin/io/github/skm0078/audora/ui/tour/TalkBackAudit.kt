// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/tour/TalkBackAudit.kt
// Purpose: What TalkBack would trip over on one screen: a control with nothing to say, a control too small to find
//          by touch, and a screen with no heading to jump to.

package io.github.skm0078.audora.ui.tour

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull

/**
 * Problems on the screen whose merged semantics trees start at [roots] (every window, the topmost last). A control is
 * anything TalkBack can activate: a click, a long press, a switch or a slider. Its name is what TalkBack reads before
 * the role and state: its description or text, merged from what it holds. Its size is its laid-out size, which the
 * minimum touch target already counts, so a list item cut off by the screen edge is not called small. Only the
 * topmost window must have a heading: a menu has none ([needsHeading] false).
 */
internal fun talkBackProblems(roots: List<SemanticsNode>, needsHeading: Boolean): List<String> {
    val problems = roots.flatMap { root -> root.descendants().filter(::isControl).mapNotNull(::controlProblem) }
    val top = roots.lastOrNull() ?: return problems
    val headed = (sequenceOf(top) + top.descendants()).any { SemanticsProperties.Heading in it.config }
    return if (needsHeading && !headed) problems + NO_HEADING else problems
}

private fun controlProblem(node: SemanticsNode): String? {
    val name = spokenName(node)
    val density = node.layoutInfo.density.density
    val widthDp = node.size.width / density
    val heightDp = node.size.height / density
    val where = "${roleOf(node)} at ${node.boundsInRoot.left.toInt()},${node.boundsInRoot.top.toInt()} px"
    return when {
        name.isBlank() -> "unnamed control: $where"

        minOf(widthDp, heightDp) < MIN_TARGET_DP && node.size.width > 0 ->
            "small target \"$name\": ${widthDp.toInt()}x${heightDp.toInt()} dp, $where"

        else -> null
    }
}

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

private const val NO_HEADING = "no heading (TalkBack cannot jump to the top of this screen)"
