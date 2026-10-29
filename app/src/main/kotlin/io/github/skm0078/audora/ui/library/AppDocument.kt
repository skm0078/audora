// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/AppDocument.kt
// Purpose: "From ListenLock": documents that come with the app, shown as one labelled part of the library's home so
//          they are never mistaken for the listener's own books (ADR 2026-10-29-in-app-cookbook).

package io.github.skm0078.audora.ui.library

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.ui.theme.Dimens

/**
 * A document ListenLock provides, opened through its library [action] (saved like the sample book, so opening it
 * again finds the same book). Only content the app may ship goes here: written for the app, or a third party's work
 * with its licence or written permission checked first (ADR 2026-10-29-in-app-cookbook).
 */
enum class AppDocument(@StringRes val title: Int, @StringRes val line: Int, val action: LibraryAction) {
    COOKBOOK(R.string.cookbook_title, R.string.cookbook_line, LibraryAction.COOKBOOK),
}

/** The "From ListenLock" heading, a line saying these come with the app, and one slim card per document. */
@Composable
internal fun FromListenLockRow(documents: List<AppDocument>, onOpen: (AppDocument) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
        Text(
            stringResource(R.string.home_section_from_listenlock),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.from_listenlock_line),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
            contentPadding = PaddingValues(end = Dimens.SpaceL),
        ) {
            items(documents, key = { it.name }) { document -> AppDocumentCard(document) { onOpen(document) } }
        }
    }
}

@Composable
private fun AppDocumentCard(document: AppDocument, onOpen: () -> Unit) {
    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier.widthIn(max = CARD_MAX_WIDTH),
    ) {
        Row(
            Modifier.padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceM),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(R.drawable.ic_headphones),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
                Text(stringResource(document.title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(document.line),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = LINE_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val CARD_MAX_WIDTH = 320.dp
private const val LINE_LINES = 2
