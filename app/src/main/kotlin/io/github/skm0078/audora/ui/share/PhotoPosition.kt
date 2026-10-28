// File: app/src/main/kotlin/io/github/skm0078/audora/ui/share/PhotoPosition.kt
// Purpose: The listener places their own picture behind a card or video by dragging it on the card, or with
//          TalkBack's actions, which move it a fifth of the way at a time (ADR 2026-10-28-photo-position).

package io.github.skm0078.audora.ui.share

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.share.Extent
import io.github.skm0078.audora.core.share.PhotoFocus
import io.github.skm0078.audora.core.share.PhotoNudge

/**
 * Drags on the card move [photo] under it. The card is drawn at full size and scaled to fit the screen, so the drag
 * and the card's size are both in card pixels and a finger's move is the picture's move on the shared card.
 */
@Composable
internal fun Modifier.photoDrag(photo: CardPhoto, onFocus: (PhotoFocus) -> Unit): Modifier {
    val current by rememberUpdatedState(photo)
    val moved by rememberUpdatedState(onFocus)
    val image = Extent(photo.image.width.toFloat(), photo.image.height.toFloat())
    val actions = PhotoNudge.entries.map { way ->
        CustomAccessibilityAction(stringResource(way.label)) {
            moved(current.focus.nudged(way))
            true
        }
    }
    return semantics { customActions = actions }
        .pointerInput(photo.image) {
            // Kept here through a drag: moves can come faster than the screen redraws with the last one.
            var at = current.focus
            detectDragGestures(onDragStart = { at = current.focus }) { change, drag ->
                change.consume()
                at = at.dragged(drag.x, drag.y, Extent(size.width.toFloat(), size.height.toFloat()), image)
                moved(at)
            }
        }
}

private val PhotoNudge.label: Int
    get() = when (this) {
        PhotoNudge.LEFT -> R.string.share_card_picture_left
        PhotoNudge.RIGHT -> R.string.share_card_picture_right
        PhotoNudge.UP -> R.string.share_card_picture_up
        PhotoNudge.DOWN -> R.string.share_card_picture_down
    }
