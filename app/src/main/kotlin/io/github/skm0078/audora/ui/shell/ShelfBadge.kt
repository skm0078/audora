// File: app/src/main/kotlin/io/github/skm0078/audora/ui/shell/ShelfBadge.kt
// Purpose: A collection's dot and emoji ("Make it yours", touch 9), the same in the sidebar, the phone drawer, the
//          library's chips and the Collections sheet, so a shelf is recognised before its name is read.

package io.github.skm0078.audora.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.core.library.ShelfLook
import io.github.skm0078.audora.ui.theme.Dimens
import io.github.skm0078.audora.ui.theme.shelfDotColour

/**
 * A collection's dot, in its colour (or one picked from its id, the same every time), with its emoji beside it when
 * it has one. Decoration only: the name next to it is what TalkBack reads.
 */
@Composable
internal fun ShelfBadge(id: String, look: ShelfLook, modifier: Modifier = Modifier) {
    Row(
        modifier.clearAndSetSemantics {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
    ) {
        Box(Modifier.size(DOT_SIZE).clip(CircleShape).background(shelfDotColour(id, look.colour)))
        look.emoji?.let { Text(it.glyph, style = MaterialTheme.typography.bodyMedium) }
    }
}

private val DOT_SIZE = 12.dp
