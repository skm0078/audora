// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/LibraryHint.kt
// Purpose: The library's first-use hint (ADR 2026-10-30-first-use-hints): after a few app starts with one book, the
//          other ways to add books.

package io.github.skm0078.audora.ui.library

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import io.github.skm0078.audora.core.config.FirstUseHintsConfig
import io.github.skm0078.audora.core.hints.FirstUseHint
import io.github.skm0078.audora.ui.hints.HintCard
import io.github.skm0078.audora.ui.hints.rememberHints
import io.github.skm0078.audora.ui.kids.LocalKidsMode
import io.github.skm0078.audora.ui.theme.Dimens

/** Counts this app start's visit; [onAddBook] opens the Add book sheet, where photos, the web and catalogues are. */
@Composable
internal fun LibraryHint(listState: LibraryListState, onAddBook: () -> Unit, config: FirstUseHintsConfig = HINTS) {
    val hints = rememberHints()
    LaunchedEffect(hints) { hints.countLibraryVisit() }
    val oneBook = (listState as? LibraryListState.Loaded)?.all?.size == 1
    val due = oneBook && hints.libraryVisits >= config.libraryVisitsForMoreBooks && !LocalKidsMode.current
    val hint = hints.pick(if (due) listOf(FirstUseHint.MORE_BOOKS) else emptyList()) ?: return
    HintCard(
        hint,
        hints,
        onAct = onAddBook,
        Modifier.padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceS),
    )
}

private val HINTS = FirstUseHintsConfig()
