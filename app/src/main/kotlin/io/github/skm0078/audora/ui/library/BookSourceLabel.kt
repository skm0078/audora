// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/BookSourceLabel.kt
// Purpose: How the library says where a book's file lives: a line on a row, a small cloud on a tile's cover and the
//          details sheet's "Where" line (ADR 2026-10-28-book-source).

package io.github.skm0078.audora.ui.library

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.BookSource
import io.github.skm0078.audora.ui.theme.Dimens

@StringRes
internal fun sourceName(source: BookSource): Int = when (source) {
    BookSource.PHONE -> R.string.book_source_phone
    BookSource.GOOGLE_DRIVE -> R.string.book_source_google_drive
    BookSource.ONEDRIVE -> R.string.book_source_onedrive
    BookSource.DROPBOX -> R.string.book_source_dropbox
    BookSource.OTHER_APP -> R.string.book_source_other_app
}

/**
 * The words a row or tile adds for a book read through another app; null for a book on the phone, which is most of a
 * library: a label on every row would only be noise, so only the exception is marked.
 */
@Composable
internal fun sourceLabel(source: BookSource): String? =
    if (source.isOnPhone) null else stringResource(sourceName(source))

/** "☁ Google Drive" under a row's title; the row's text already carries the words for TalkBack. */
@Composable
internal fun SourceLine(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
        Icon(
            painterResource(R.drawable.ic_cloud),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(SOURCE_ICON),
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A small cloud in the cover's top corner of a tile read through another app. Decoration only: the tile's own
 * description says where the book is.
 */
@Composable
internal fun SourceBadge(modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = BADGE_ALPHA),
        modifier = modifier.padding(Dimens.SpaceXs).clearAndSetSemantics {},
    ) {
        Box(Modifier.padding(BADGE_PADDING), contentAlignment = Alignment.Center) {
            Icon(
                painterResource(R.drawable.ic_cloud),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(SOURCE_ICON),
            )
        }
    }
}

private val SOURCE_ICON = 16.dp
private val BADGE_PADDING = 4.dp

/** Readable over any cover without hiding it. */
private const val BADGE_ALPHA = 0.85f
