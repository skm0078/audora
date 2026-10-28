// File: app/src/main/kotlin/io/github/skm0078/audora/ui/made/MadeSetViews.kt
// Purpose: One made set as a tile in the Made grid or a row in its list (ADR 2026-10-28-made-tab): the book's cover
//          with what kind of file it is, its book, how long and large, and the icon buttons under the thumb.

package io.github.skm0078.audora.ui.made

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import io.github.skm0078.audora.ui.library.BookCover
import io.github.skm0078.audora.ui.library.COVER_RATIO
import io.github.skm0078.audora.ui.library.CoverSource
import io.github.skm0078.audora.ui.theme.Dimens

/** The book's cover across the tile, then its title and kind, then Play or Open, Share and ⋮ in one row. */
@Composable
internal fun MadeSetTile(
    item: MadeSetItem,
    covers: CoverSource,
    onPlay: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val made = item.made
    val primary = primaryAction(made, onPlay)
    val named = namedFor(item)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
        SetCover(
            item,
            covers,
            onClick = primary,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f / COVER_RATIO),
        )
        SetText(item, full = false, Modifier.padding(top = Dimens.SpaceXs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            primary?.let { PrimaryButton(made, named, it) }
            Spacer(Modifier.weight(1f))
            ShareAndMore(made, named)
        }
    }
}

/** A small cover, the words beside it, and the same buttons at the end: the library's row, for a made set. */
@Composable
internal fun MadeSetRow(
    item: MadeSetItem,
    covers: CoverSource,
    onPlay: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val made = item.made
    val primary = primaryAction(made, onPlay)
    val named = namedFor(item)
    Row(
        modifier.fillMaxWidth().padding(vertical = Dimens.SpaceS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
    ) {
        SetCover(
            item,
            covers,
            onClick = primary,
            modifier = Modifier.size(Dimens.CoverSmall, Dimens.CoverSmall * COVER_RATIO),
            badge = false,
        )
        SetText(item, full = true, Modifier.weight(1f))
        primary?.let { PrimaryButton(made, named, it) }
        ShareAndMore(made, named)
    }
}

/** "Gitanjali, Audiobook": what each button is for, in its spoken name. */
@Composable
private fun namedFor(item: MadeSetItem): String =
    listOfNotNull(item.bookTitle, kindLabel(item.made.set.kind)).joinToString(NAME_SEPARATOR)

/**
 * The book's cover (or the kind's icon on a plain card, for a book since removed), a tap away from playing. The
 * badge names the kind on a large cover; a row's small one leaves it to the words. Decoration for TalkBack: the
 * Play button beside it says the same.
 */
@Composable
private fun SetCover(
    item: MadeSetItem,
    covers: CoverSource,
    onClick: (() -> Unit)?,
    modifier: Modifier,
    badge: Boolean = true,
) {
    val shape = MaterialTheme.shapes.small
    Box(
        modifier
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .clearAndSetSemantics {},
    ) {
        val book = item.book
        if (book != null) {
            BookCover(book, covers, Modifier.fillMaxSize())
        } else {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceVariant) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painterResource(kindIcon(item.made.set.kind)), null)
                }
            }
        }
        if (badge) KindBadge(item, Modifier.align(Alignment.BottomStart).padding(Dimens.SpaceS))
    }
}

/** The kind on the cover in a dark pill, readable on any cover picture in either theme. */
@Composable
private fun KindBadge(item: MadeSetItem, modifier: Modifier) {
    Surface(
        modifier,
        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = BADGE_ALPHA),
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shape = RoundedCornerShape(Dimens.SpaceM),
    ) {
        Row(
            Modifier.padding(horizontal = Dimens.SpaceS, vertical = Dimens.SpaceXs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
        ) {
            Icon(painterResource(kindIcon(item.made.set.kind)), null, Modifier.size(Dimens.SpaceL))
            Text(kindLabel(item.made.set.kind), style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

/** The book's title (or the kind, in the book's own details), the kind under it, the details, and what is missing. */
@Composable
private fun SetText(item: MadeSetItem, full: Boolean, modifier: Modifier) {
    val kind = kindLabel(item.made.set.kind)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs / 2)) {
        Text(
            item.bookTitle ?: kind,
            style = MaterialTheme.typography.titleSmall,
            maxLines = TITLE_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
        val detail = detailLine(item.made, full)
        Text(
            if (item.bookTitle != null && full) "$kind$DETAIL_SEPARATOR$detail" else detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = DETAIL_LINES,
            overflow = TextOverflow.Ellipsis,
        )
        missingLine(item.made)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

private const val TITLE_LINES = 2
private const val DETAIL_LINES = 2
private const val NAME_SEPARATOR = ", "

/** Dark enough for light text on a pale cover, light enough to see the cover through. */
private const val BADGE_ALPHA = 0.82f
