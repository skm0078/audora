// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/LibraryTopBar.kt
// Purpose: The library's top bar: a greeting over the title (ADR 2026-10-28-home-and-tour), search field, the sort
//          sheet's button (plan item 22, ADR 2026-10-28-book-menu-sort-sheet), the Jobs button with its count
//          (ADR-067), ⋮ tools menu.

package io.github.skm0078.audora.ui.library

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.LibraryQuery
import io.github.skm0078.audora.ui.shell.LocalOpenDrawer
import io.github.skm0078.audora.ui.theme.HelpButton
import java.time.LocalTime

@Composable
internal fun LibraryTopBar(
    listState: LibraryListState,
    actions: LibraryActions,
    runningJobs: Int = 0,
    view: LibraryLayoutState = LibraryLayoutState(LibraryLayout.AUTO) {},
    name: String = "",
) {
    val loaded = listState as? LibraryListState.Loaded
    LibraryTopBar(
        query = loaded?.query ?: LibraryQuery(),
        hasBooks = loaded?.all?.isNotEmpty() == true,
        onQuery = actions::onQuery,
        onAction = actions::onLibraryAction,
        runningJobs = runningJobs,
        onOpenJobs = actions::onOpenJobs,
        shown = loaded?.items?.size ?: 0,
        view = view,
        name = name,
    )
}

/**
 * Search and the sort sheet's button show once the library has a book; [shown] is how many books the sheet leaves.
 * Closing the search clears its text but keeps the sort and filter, which stay until changed. Jobs live in the Made
 * place; the Jobs button here is only a shortcut while something runs, with a badge counting what works now (ADR
 * 2026-10-28-four-places). On a phone the menu button at the start opens the side drawer (ADR 2026-10-28-phone-drawer).
 * [name]: what the greeting calls the listener, from "Make it yours"; blank greets no one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryTopBar(
    query: LibraryQuery,
    hasBooks: Boolean,
    onQuery: (LibraryQuery) -> Unit,
    onAction: (LibraryAction) -> Unit = {},
    runningJobs: Int = 0,
    onOpenJobs: () -> Unit = {},
    shown: Int = 0,
    view: LibraryLayoutState = LibraryLayoutState(LibraryLayout.AUTO) {},
    name: String = "",
) {
    var searching by rememberSaveable { mutableStateOf(query.text.isNotEmpty()) }
    // The keyboard rises only after a tap on search, never when the library is rebuilt with a search still open (a
    // back swipe's preview, a rotation).
    var focusOnOpen by remember { mutableStateOf(false) }
    val closeSearch = {
        searching = false
        onQuery(query.copy(text = ""))
    }
    // Back closes the search first, as its arrow does (predictive back delivers it here, never to onBackPressed).
    BackHandler(enabled = searching, onBack = closeSearch)
    TopAppBar(
        title = {
            if (searching) {
                SearchField(query.text, focusOnOpen) { onQuery(query.copy(text = it)) }
            } else {
                LibraryTitle(name)
            }
        },
        navigationIcon = {
            val openDrawer = LocalOpenDrawer.current
            if (searching) {
                IconButton(onClick = closeSearch) {
                    Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.library_search_close))
                }
            } else if (openDrawer != null) {
                IconButton(onClick = openDrawer, modifier = Modifier.testTag(DRAWER_BUTTON_TAG)) {
                    Icon(painterResource(R.drawable.ic_menu), stringResource(R.string.drawer_open))
                }
            }
        },
        actions = {
            if (hasBooks && !searching) {
                IconButton(onClick = {
                    focusOnOpen = true
                    searching = true
                }) {
                    Icon(painterResource(R.drawable.ic_search), stringResource(R.string.library_search))
                }
            }
            if (hasBooks) SortButton(query, shown, view, onQuery)
            if (!searching && runningJobs > 0) JobsButton(runningJobs, onOpenJobs)
            if (!searching) LibraryActionsMenu(onAction)
        },
    )
}

/**
 * "Good evening" (or "Good evening, Asha") over "Library": the hour of the day the library was opened, so it does not
 * change under the eye.
 */
@Composable
private fun LibraryTitle(name: String) {
    val hour = remember { LocalTime.now().hour }
    val greeting = if (name.isBlank()) {
        stringResource(
            greetingFor(hour),
        )
    } else {
        stringResource(greetingFor(hour, true), name)
    }
    Column {
        Text(
            greeting,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.library_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
    }
}

/** Morning from 5, afternoon from noon, evening from 5 pm until 5 the next morning; [named] takes the name as %1$s. */
@StringRes
internal fun greetingFor(hour: Int, named: Boolean = false): Int = when (hour) {
    in MORNING_FROM until AFTERNOON_FROM ->
        if (named) R.string.library_greeting_morning_named else R.string.library_greeting_morning

    in AFTERNOON_FROM until EVENING_FROM ->
        if (named) R.string.library_greeting_afternoon_named else R.string.library_greeting_afternoon

    else -> if (named) R.string.library_greeting_evening_named else R.string.library_greeting_evening
}

/** For UI tests: the phone's menu button. */
internal const val DRAWER_BUTTON_TAG = "drawer_button"

private const val MORNING_FROM = 5
private const val AFTERNOON_FROM = 12
private const val EVENING_FROM = 17

/** TalkBack hears "Jobs, 2 running"; the badge shows the number. */
@Composable
private fun JobsButton(running: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        BadgedBox(badge = { Badge { Text(running.toString()) } }) {
            val label = pluralStringResource(R.plurals.jobs_open_running, running, running)
            Icon(painterResource(R.drawable.ic_jobs), label)
        }
    }
}

@Composable
private fun SearchField(text: String, focusOnOpen: Boolean, onChange: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (focusOnOpen) focus.requestFocus() }
    TextField(
        value = text,
        onValueChange = onChange,
        placeholder = { Text(stringResource(R.string.library_search_hint)) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
        ),
        modifier = Modifier.focusRequester(focus),
    )
}

/** Opens the sort sheet; saveable, so the sheet is still open after a rotation. */
@Composable
private fun SortButton(query: LibraryQuery, shown: Int, view: LibraryLayoutState, onQuery: (LibraryQuery) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(painterResource(R.drawable.ic_sort), stringResource(R.string.library_sort_filter))
    }
    if (open) LibrarySortSheet(query, shown, view, onQuery) { open = false }
}

/** The library's own tools (plan items 62, 26, 28); shown with or without books, since they add books. */
@Composable
private fun LibraryActionsMenu(onAction: (LibraryAction) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.reader_more))
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
        ) {
            LibraryAction.entries.filter { it.inMenu }.forEach { action ->
                // One line per tool; what it does waits behind its info button (ADR-062).
                val label = stringResource(action.label)
                DropdownMenuItem(
                    text = { Text(label) },
                    trailingIcon = { HelpButton(label, stringResource(action.hint)) },
                    onClick = {
                        open = false
                        onAction(action)
                    },
                )
            }
        }
    }
}
