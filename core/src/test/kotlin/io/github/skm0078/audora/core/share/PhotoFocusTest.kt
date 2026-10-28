// File: core/src/test/kotlin/io/github/skm0078/audora/core/share/PhotoFocusTest.kt
// Purpose: The listener's own picture behind a card follows a drag along the side it overflows, stops at its edges,
//          and TalkBack's steps move it the same way (ADR 2026-10-28-photo-position).

package io.github.skm0078.audora.core.share

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoFocusTest {
    // A 9:16 story frame and a landscape photo: cropped to fill, the photo overflows sideways only.
    private val story = Extent(360f, 640f)
    private val landscape = Extent(4000f, 3000f)

    @Test
    fun aDragRightShowsMoreOfTheLeftAndStopsAtTheEdge() {
        // Scaled to 640 high the photo is 853 wide: 493 hidden across.
        val hidden = landscape.width * (story.height / landscape.height) - story.width
        val moved = PhotoFocus.CENTRE.dragged(hidden / 4, 0f, story, landscape)
        assertEquals(0.25f, moved.x, EPSILON)
        assertEquals(0f, PhotoFocus.CENTRE.dragged(hidden * 2, 0f, story, landscape).x, EPSILON)
        assertEquals(1f, PhotoFocus.CENTRE.dragged(-hidden * 2, 0f, story, landscape).x, EPSILON)
    }

    @Test
    fun theSideThatFitsNeverMoves() {
        val moved = PhotoFocus.CENTRE.dragged(0f, 300f, story, landscape)
        assertEquals(PhotoFocus.CENTRE, moved)
    }

    @Test
    fun aPortraitPhotoMovesUpAndDown() {
        val tall = Extent(1000f, 4000f)
        val moved = PhotoFocus.CENTRE.dragged(50f, -100f, story, tall)
        assertEquals(0.5f, moved.x, EPSILON)
        assertEquals(true, moved.y > 0.5f)
    }

    @Test
    fun aNudgeMovesThePictureTheWayItSaysAndStopsAtTheEdge() {
        assertEquals(0.5f + PhotoFocus.NUDGE, PhotoFocus.CENTRE.nudged(PhotoNudge.LEFT).x, EPSILON)
        assertEquals(0.5f - PhotoFocus.NUDGE, PhotoFocus.CENTRE.nudged(PhotoNudge.RIGHT).x, EPSILON)
        val atTop = generateSequence(PhotoFocus.CENTRE) { it.nudged(PhotoNudge.DOWN) }.elementAt(STEPS_PAST_EDGE)
        assertEquals(0f, atTop.y, EPSILON)
    }

    @Test
    fun anEmptyFrameOrPictureLeavesTheFocus() {
        val focus = PhotoFocus(0.3f, 0.7f)
        assertEquals(focus, focus.dragged(10f, 10f, Extent(0f, 0f), landscape))
        assertEquals(focus, focus.dragged(10f, 10f, story, Extent(0f, 10f)))
    }

    private companion object {
        const val EPSILON = 1e-4f
        const val STEPS_PAST_EDGE = 5
    }
}
