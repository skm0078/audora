// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/AddBookButton.kt
// Purpose: The library's Add book button, with a name TalkBack reads. It folds to its icon while the books scroll
//          down and opens again on the way back up, so at large text sizes it does not sit over a row's ⋮.

package io.github.skm0078.audora.ui.library

import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.skm0078.audora.R

/**
 * Named on the button itself: Material's animated label does not reach TalkBack. Folded ([expanded] false) it shows
 * only its icon and keeps its name.
 */
@Composable
internal fun AddBookButton(expanded: Boolean = true, onClick: () -> Unit) {
    val add = stringResource(R.string.library_add_pdf)
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
        text = { Text(add) },
        expanded = expanded,
        modifier = Modifier.semantics { contentDescription = add },
    )
}

/**
 * Watches the books scroll (put it on their parent with `Modifier.nestedScroll`): reading down folds Add book, going
 * back up opens it. It only watches; the list scrolls exactly as before.
 */
@Stable
internal class AddBookFolding : NestedScrollConnection {
    var expanded by mutableStateOf(true)
        private set

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        when {
            available.y < -SCROLL_SLOP_PX -> expanded = false
            available.y > SCROLL_SLOP_PX -> expanded = true
        }
        return Offset.Zero
    }

    private companion object {
        /** Ignores the jitter of a finger resting on the list. */
        const val SCROLL_SLOP_PX = 1f
    }
}
