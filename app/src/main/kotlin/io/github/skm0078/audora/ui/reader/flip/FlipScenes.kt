// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/flip/FlipScenes.kt
// Purpose: The curl a PDF page's turn draws at each moment (ADR 2026-10-29-page-curl), read from its FlipState: kept
//          apart so FlipState holds only where the page is and how it moves.

package io.github.skm0078.audora.ui.reader.flip

import io.github.skm0078.audora.core.flip.CurlPoint
import io.github.skm0078.audora.core.flip.PageCurl

/** The curl to draw now, on a page [width] by [height]; null while the page lies flat. */
internal fun FlipState.curl(pages: FlipPages, width: Float, height: Float): CurlScene? {
    val turning = auto
    val start = from
    return when {
        turning != null -> autoScene(turning, pages, width, height)
        start == null || offset == 0f -> null
        else -> dragScene(start, pages, width)
    }
}

/** On: the page left behind curls away over the new one. Back: the new one curls in over it. */
private fun FlipState.autoScene(turning: AutoTurn, pages: FlipPages, width: Float, height: Float): CurlScene {
    val finger = PageCurl.turning(width, height, turning.progress, turning.forward, config.readingLift)
    val before = pages.nearby[turning.before]?.imageBitmap()
    val shown = pages.nearby[pages.page]?.imageBitmap()
    return CurlScene(
        contentOnTop = !turning.forward,
        image = before,
        back = if (turning.forward) before else shown,
        finger = finger,
        cornerY = height,
    )
}

/** On: the shown page lifts off the next. Back: the page before curls in over the shown one. */
private fun FlipState.dragScene(start: Int, pages: FlipPages, width: Float): CurlScene? {
    val towards = start + if (offset < 0f) 1 else -1
    if (towards !in 0 until pages.pageCount) return null
    val other = pages.nearby[towards]?.imageBitmap()
    val corner = cornerY + lift
    return if (offset < 0f) {
        CurlScene(true, other, pages.nearby[start]?.imageBitmap(), CurlPoint(width + 2 * offset, corner), cornerY)
    } else {
        CurlScene(false, other, other, CurlPoint(-width + 2 * offset, corner), cornerY)
    }
}
