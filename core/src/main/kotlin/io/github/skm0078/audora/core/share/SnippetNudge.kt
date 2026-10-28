// File: core/src/main/kotlin/io/github/skm0078/audora/core/share/SnippetNudge.kt
// Purpose: The page snippet's box moved or resized one step at a time, for TalkBack, which cannot drag: the same
//          rules as a drag (on the page, at least a handle wide, at most a third of the page).

package io.github.skm0078.audora.core.share

import io.github.skm0078.audora.core.config.ShareCardConfig

/** One step a TalkBack action takes the box. */
enum class SnippetNudge { UP, DOWN, LEFT, RIGHT, BIGGER, SMALLER }

/**
 * The box one [ShareCardConfig.snippetNudgeStep] along [way]. Moves stop at the page's edges. Bigger and smaller pull
 * both corners, so the box grows or shrinks about its middle; a side already at the page's edge or at its smallest
 * stays there, and growth past a third of the page is trimmed as a drag's would be.
 */
fun SnippetBox.nudged(way: SnippetNudge, config: ShareCardConfig = ShareCardConfig()): SnippetBox {
    val step = config.snippetNudgeStep
    val half = step / 2
    return when (way) {
        SnippetNudge.UP -> moved(0f, -step)

        SnippetNudge.DOWN -> moved(0f, step)

        SnippetNudge.LEFT -> moved(-step, 0f)

        SnippetNudge.RIGHT -> moved(step, 0f)

        SnippetNudge.BIGGER -> resized(SnippetCorner.TOP_LEFT, -half, -half, config)
            .resized(SnippetCorner.BOTTOM_RIGHT, half, half, config)

        SnippetNudge.SMALLER -> resized(SnippetCorner.TOP_LEFT, half, half, config)
            .resized(SnippetCorner.BOTTOM_RIGHT, -half, -half, config)
    }
}
