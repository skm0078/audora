// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/AppDocument.kt
// Purpose: "From ListenLock": documents that come with the app, shown as one labelled part of the library's home so
//          they are never mistaken for the listener's own books (ADR 2026-10-29-in-app-cookbook).

package io.github.skm0078.audora.ui.library

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import io.github.skm0078.audora.R
import io.github.skm0078.audora.ui.theme.Dimens

/**
 * A document ListenLock provides, opened through its library [action] (saved like the sample book, so opening it
 * again finds the same book). Only content the app may ship goes here: written for the app, or a third party's work
 * with its licence or written permission checked first (ADR 2026-10-29-in-app-cookbook).
 */
enum class AppDocument(@StringRes val title: Int, val action: LibraryAction) {
    COOKBOOK(R.string.cookbook_title, LibraryAction.COOKBOOK),
}

/**
 * One slim row per document, each labelled "From ListenLock" above its title so it reads as the app's own, not the
 * listener's. Kept to one line of label and one of title: at the largest text a taller part pushed the first book
 * under the Add book button.
 */
@Composable
internal fun FromListenLockRow(documents: List<AppDocument>, onOpen: (AppDocument) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
        documents.forEach { document -> AppDocumentCard(document) { onOpen(document) } }
    }
}

@Composable
private fun AppDocumentCard(document: AppDocument, onOpen: () -> Unit) {
    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceS),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(R.drawable.ic_headphones),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column {
                Text(
                    stringResource(R.string.home_section_from_listenlock),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(stringResource(document.title), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}
