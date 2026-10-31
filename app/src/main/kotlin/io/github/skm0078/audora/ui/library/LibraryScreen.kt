// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/LibraryScreen.kt
// Purpose: Library tab: the listener's home parts, a cover grid (a list at large text), Add book and its sheet, the
//          duplicate check (ADR-036, ADR 2026-10-28-make-it-yours, ADR 2026-10-29-add-a-book).

package io.github.skm0078.audora.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookAvailability
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.ui.firstrun.TourTarget
import io.github.skm0078.audora.ui.firstrun.tourTarget
import io.github.skm0078.audora.ui.kids.LocalKidsMode
import io.github.skm0078.audora.ui.search.FoundCard
import io.github.skm0078.audora.ui.search.FoundOnly
import io.github.skm0078.audora.ui.search.FoundRow
import io.github.skm0078.audora.ui.search.FoundTarget
import io.github.skm0078.audora.ui.search.open
import io.github.skm0078.audora.ui.search.rememberFoundRows
import io.github.skm0078.audora.ui.settings.SettingKey
import io.github.skm0078.audora.ui.shell.readableSidePadding
import io.github.skm0078.audora.ui.theme.AudoraTheme
import io.github.skm0078.audora.ui.theme.Dimens
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [runningJobs]: how many jobs and downloads work now, for the badge on the Jobs button (ADR-067). [view]: grid or
 * list as picked in the sort sheet; the books wait the few milliseconds the saved choice takes to read, so they never
 * jump from one layout to the other on opening. [home]: the parts above the books and the "Make it yours" choices.
 */
@Composable
fun LibraryScreen(
    listState: LibraryListState,
    busy: Boolean,
    message: LibraryMessage?,
    pendingRemoval: Book?,
    actions: LibraryActions,
    modifier: Modifier = Modifier,
    banner: @Composable () -> Unit = {},
    covers: CoverSource = CoverSource.None,
    runningJobs: Int = 0,
    view: LibraryLayoutState = LibraryLayoutState(LibraryLayout.AUTO) {},
    settingValues: Map<SettingKey, String> = emptyMap(),
    home: LibraryHome = LibraryHome.Default,
    requested: LibraryAction? = null,
    onRequestTaken: () -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    // Saveable: the picker can outlive this process, and its result must still reach the right book.
    var locating by rememberSaveable { mutableStateOf<String?>(null) }
    var details by rememberSaveable { mutableStateOf<String?>(null) } // the book whose details sheet is open
    var picked by rememberSaveable { mutableStateOf<String?>(null) } // the book the wide screen's pane shows
    val ownSheets = rememberOwnSheets(actions, requested, onRequestTaken)
    val screenActions = ownSheets.actions
    val addPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(actions::onAddPdf)
    }
    val locatePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id = locating
        locating = null
        if (uri != null && id != null) actions.onLocate(BookId(id), uri)
    }
    val pickPdf = { addPicker.launch(BOOK_MIME_TYPES) }

    LifecycleResumeEffect(Unit) {
        actions.onScreenResumed()
        onPauseOrDispose {}
    }
    val duplicate = message?.let(::duplicateOf)
    MessageEffect(message.takeIf { duplicate == null }, snackbar, actions::onUndoRemoval, actions::onMessageShown)

    BoxWithConstraints(modifier.fillMaxSize()) {
        val loaded = listState as? LibraryListState.Loaded
        val pane = loaded?.takeIf { showsBookPane(maxWidth, LocalKidsMode.current) }?.let { paneBook(it, picked) }
        val onTap: (LibraryItem) -> Unit = { item ->
            when {
                item.needsLocating -> {
                    locating = item.book.id.value
                    locatePicker.launch(BOOK_MIME_TYPES)
                }

                // Beside the pane a first tap picks the book; a tap on the picked one opens it.
                pane != null && pane.book.id != item.book.id -> picked = item.book.id.value

                else -> actions.onOpenBook(item.book)
            }
        }
        Row(Modifier.fillMaxSize()) {
            LibraryMain(
                listState = listState,
                busy = busy,
                actions = screenActions,
                snackbar = snackbar,
                onAddPdf = { screenActions.onLibraryAction(LibraryAction.ADD_BOOK) },
                onTap = onTap,
                onDetails = { details = it.book.id.value },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                parts = LibraryParts(banner, covers, runningJobs, view, settingValues, home),
                picked = pane?.let { shown -> PanePick(shown.book.id.value) { picked = it.book.id.value } },
            )
            pane?.let { shown ->
                val choices = paneActions(shown.book, actions) { details = shown.book.id.value }
                BookPane(shown, covers, choices, onOpen = { actions.onOpenBook(shown.book) })
            }
        }
    }
    BookSheets(listState, actions, covers, details) { details = null }
    pendingRemoval?.let { RemovalDialog(it, actions) }
    duplicate?.let { DuplicateDialog(it, listState, actions) }
    OwnSheetHost(ownSheets, listState, home, view, onFile = pickPdf)
}

/**
 * What the library's frame shows besides the books: the banner, covers, the Jobs badge, grid or list, the current
 * values search shows beside the settings it finds, and the listener's home.
 */
private class LibraryParts(
    val banner: @Composable () -> Unit,
    val covers: CoverSource,
    val runningJobs: Int,
    val view: LibraryLayoutState,
    val settingValues: Map<SettingKey, String>,
    val home: LibraryHome,
)

/** The top bar, the books and Add book; beside the book pane ([picked]) the pane keeps clear of the end side. */
@Composable
private fun LibraryMain(
    listState: LibraryListState,
    busy: Boolean,
    actions: LibraryActions,
    snackbar: SnackbarHostState,
    onAddPdf: () -> Unit,
    onTap: (LibraryItem) -> Unit,
    onDetails: (LibraryItem) -> Unit,
    modifier: Modifier,
    parts: LibraryParts,
    picked: PanePick?,
) {
    val folding = remember { AddBookFolding() }
    Scaffold(
        modifier = modifier,
        contentWindowInsets = if (picked == null) TAB_INSETS else BESIDE_PANE_INSETS,
        topBar = {
            LibraryTopBar(listState, actions, parts.runningJobs, parts.view, parts.home.layout?.name.orEmpty())
        },
        floatingActionButton = {
            if (!LocalKidsMode.current && listState is LibraryListState.Loaded && listState.all.isNotEmpty()) {
                AddBookButton(folding.expanded) { if (!busy) onAddPdf() }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize().nestedScroll(folding)) {
            if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            parts.banner()
            LibraryHint(listState, onAddPdf)
            LibraryContent(
                listState = listState,
                busy = busy,
                onAddPdf = onAddPdf,
                onTap = onTap,
                covers = parts.covers,
                menu = actions,
                onDetails = onDetails,
                layout = parts.view.layout,
                picked = picked,
                settingValues = parts.settingValues,
                home = parts.home,
            )
        }
    }
}

@Composable
private fun LibraryContent(
    listState: LibraryListState,
    busy: Boolean,
    onAddPdf: () -> Unit,
    onTap: (LibraryItem) -> Unit,
    covers: CoverSource,
    menu: LibraryActions,
    onDetails: (LibraryItem) -> Unit,
    layout: LibraryLayout?,
    picked: PanePick? = null,
    settingValues: Map<SettingKey, String> = emptyMap(),
    home: LibraryHome = LibraryHome.Default,
) {
    // Both choices are a few milliseconds' read; waiting for them keeps the books and the home parts from jumping.
    if (layout == null || home.layout == null) {
        Centered { CircularProgressIndicator() }
        return
    }
    when (listState) {
        LibraryListState.Loading -> Centered { CircularProgressIndicator() }

        LibraryListState.Failed -> Centered {
            Text(
                stringResource(R.string.library_load_failed),
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }

        is LibraryListState.Loaded -> if (listState.all.isEmpty()) {
            EmptyLibrary(
                enabled = !busy,
                onAddPdf = onAddPdf,
                onTrySample = { menu.onLibraryAction(LibraryAction.TRY_SAMPLE) },
                onCookbook = { menu.onLibraryAction(LibraryAction.COOKBOOK) },
            )
        } else {
            Column {
                if (showsShelfChips(home)) LibraryChipRow(listState, menu::onQuery)
                // Actions and settings the search names, above the books (ADR 2026-10-28-action-search).
                val found = rememberFoundRows(listState, settingValues)
                if (listState.items.isEmpty() && found.isNotEmpty()) {
                    FoundOnly(found, listState.narrowedFor) { menu.open(it) }
                } else if (listState.items.isEmpty()) {
                    Centered {
                        Text(
                            stringResource(R.string.library_no_match),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    val books = BookCallbacks(onTap, menu, onDetails, picked, found)
                    val actions = HomeActions(
                        onContinue = { menu.onContinue(it.book) },
                        onTap = onTap,
                        onLibraryAction = menu::onLibraryAction,
                    )
                    val top = homeParts(listState, home, covers, actions)
                    if (layout.showsList(LocalDensity.current.fontScale)) {
                        BookList(listState, books, covers, top)
                    } else {
                        BookGrid(listState, books, covers, top)
                    }
                }
            }
        }
    }
}

@Composable
private fun RemovalDialog(book: Book, actions: LibraryActions) {
    AlertDialog(
        onDismissRequest = actions::onCancelRemoval,
        title = { Text(stringResource(R.string.library_remove_title, displayTitle(book))) },
        text = { Text(stringResource(R.string.library_remove_body)) },
        confirmButton = {
            TextButton(onClick = actions::onConfirmRemoval) { Text(stringResource(R.string.library_remove)) }
        },
        dismissButton = {
            TextButton(onClick = actions::onCancelRemoval) { Text(stringResource(R.string.library_cancel)) }
        },
    )
}

/**
 * What a tap on a book, its menu or the Continue button does; [picked] beside the wide screen's book pane; [found] the
 * actions and settings the search names, shown above the books.
 */
private class BookCallbacks(
    val onTap: (LibraryItem) -> Unit,
    actions: LibraryActions,
    val onDetails: (LibraryItem) -> Unit,
    val picked: PanePick?,
    val found: List<FoundRow>,
) {
    val onFound: (FoundTarget) -> Unit = { actions.open(it) }
    val onRemove: (Book) -> Unit = actions::onRequestRemoval
    val onExport: (Book) -> Unit = actions::onExportAudiobook
    val onContinue: (Book) -> Unit = actions::onContinue
    val onAddToUpNext: (Book) -> Unit = actions::onAddToUpNext
    val onAction: (Book, BookAction) -> Unit = actions::onBookAction
    val onPlay: (Book) -> Unit = actions::onContinue

    fun pane(item: LibraryItem): PaneChoice? =
        picked?.let { pick -> PaneChoice(item.book.id.value == pick.shownId) { pick.onPick(item) } }

    /** The first book shown is what the tour's book menu stop points at. */
    fun tourMark(item: LibraryItem, list: LibraryListState.Loaded): Modifier =
        if (item === list.items.firstOrNull()) Modifier.tourTarget(TourTarget.BOOK) else Modifier
}

/** Beside the book pane: the book it shows, and how a ⋮ or a long press picks another one for it. */
private class PanePick(val shownId: String, val onPick: (LibraryItem) -> Unit)

@Composable
private fun BookGrid(list: LibraryListState.Loaded, books: BookCallbacks, covers: CoverSource, top: List<HomePart>) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(Dimens.CoverGridMin),
        contentPadding = PaddingValues(
            start = Dimens.ScreenPadding,
            end = Dimens.ScreenPadding,
            top = Dimens.SpaceS,
            bottom = FAB_CLEARANCE * LocalDensity.current.fontScale.coerceAtLeast(1f),
        ),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceL),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXl),
    ) {
        if (books.found.isNotEmpty()) {
            item(key = FOUND_KEY, span = { GridItemSpan(maxLineSpan) }) { FoundCard(books.found, books.onFound) }
        }
        top.forEach { part -> item(key = part.key, span = { GridItemSpan(maxLineSpan) }) { part.content() } }
        items(list.items, key = { it.book.id.value }) { item ->
            BookTile(
                item = item,
                onTap = { books.onTap(item) },
                onRemove = { books.onRemove(item.book) },
                onExport = { books.onExport(item.book) },
                covers = covers,
                onAddToUpNext = { books.onAddToUpNext(item.book) },
                onDetails = { books.onDetails(item) },
                onAction = { action -> books.onAction(item.book, action) },
                onPlay = { books.onPlay(item.book) },
                pane = books.pane(item),
                modifier = books.tourMark(item, list),
            )
        }
    }
}

@Composable
private fun BookList(list: LibraryListState.Loaded, books: BookCallbacks, covers: CoverSource, top: List<HomePart>) {
    // Rows stay a readable width in the middle of a tablet or a phone in landscape.
    BoxWithConstraints {
        val side = readableSidePadding(maxWidth)
        val bottom = FAB_CLEARANCE * LocalDensity.current.fontScale.coerceAtLeast(1f)
        LazyColumn(contentPadding = PaddingValues(start = side, end = side, bottom = bottom)) {
            if (books.found.isNotEmpty()) {
                item(key = FOUND_KEY) {
                    FoundCard(
                        books.found,
                        books.onFound,
                        Modifier.padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceS),
                    )
                }
            }
            top.forEach { part ->
                item(key = part.key) {
                    Box(Modifier.padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceS)) {
                        part.content()
                    }
                }
            }
            items(list.items, key = { it.book.id.value }) { item ->
                BookRow(
                    item = item,
                    onTap = { books.onTap(item) },
                    onRemove = { books.onRemove(item.book) },
                    onExport = { books.onExport(item.book) },
                    covers = covers,
                    onAddToUpNext = { books.onAddToUpNext(item.book) },
                    onDetails = { books.onDetails(item) },
                    onAction = { action -> books.onAction(item.book, action) },
                    onPlay = { books.onPlay(item.book) },
                    pane = books.pane(item),
                    modifier = books.tourMark(item, list),
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun EmptyLibrary(enabled: Boolean, onAddPdf: () -> Unit, onTrySample: () -> Unit, onCookbook: () -> Unit) {
    Centered {
        Icon(
            painterResource(R.drawable.ic_library),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(stringResource(R.string.library_empty_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.library_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onAddPdf, enabled = enabled) { Text(stringResource(R.string.library_add_pdf)) }
        // Hear the app work in one tap before finding a file: a short page that also says how to use it.
        OutlinedButton(onClick = onTrySample, enabled = enabled) { Text(stringResource(R.string.sample_try)) }
        // Then how to combine features for real situations, read aloud like any book.
        TextButton(onClick = onCookbook, enabled = enabled) { Text(stringResource(R.string.cookbook_open)) }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(Dimens.SpaceXl), contentAlignment = Alignment.Center) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
    }
}

/** Room under the last book for the Add book button at 100% text; it grows with the text size, as the button does. */
private val FAB_CLEARANCE = 88.dp

/** The app shell's bottom bar takes the bottom inset (ADR-036). */
private val TAB_INSETS: WindowInsets
    @Composable get() = ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)

/** Beside the book pane, which keeps clear of the end side itself. */
private val BESIDE_PANE_INSETS: WindowInsets
    @Composable get() = ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Start)

/** Book ids are UUIDs, so this key and the home parts' never clash with a row's. */
private const val FOUND_KEY = "found-actions"

/**
 * OpenDocument filters by MIME type; providers that mislabel files are caught by the header check. PDFs, EPUBs and
 * text files (items 42 and 43, ADR-042); Word, OpenDocument, RTF, HTML and Markdown files, and pictures, which become
 * a photo book (ADR-066). `text/` as a whole, since Markdown has several labels. The first-run picker uses it too.
 * FB2, Kindle (MOBI, AZW3), CHM and comic archives (ADR 2026-10-26-more-ebook-formats) are rarely labelled right by
 * Android, so files of no known type and ZIPs (a zipped FB2, a comic) are offered too; their first bytes decide.
 * DjVu too (ADR 2026-10-27-djvu-books).
 */
internal val BOOK_MIME_TYPES = arrayOf(
    "application/pdf",
    "application/epub+zip",
    "text/*",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.oasis.opendocument.text",
    "application/rtf",
    "image/png",
    "image/jpeg",
    "image/webp",
    "image/heic",
    "image/heif",
    "application/x-fictionbook+xml",
    "application/x-mobipocket-ebook",
    "application/vnd.amazon.mobi8-ebook",
    "application/vnd.ms-htmlhelp",
    "application/vnd.comicbook+zip",
    "image/vnd.djvu",
    "image/x-djvu",
    "application/zip",
    "application/octet-stream",
)

@Preview(showBackground = true)
@Composable
private fun LibraryScreenPreview() {
    val book = Book(BookId("preview-1"), "content://x/1", "Moby Dick", 1_600_000, "v1:a", 574, 0, null)
    AudoraTheme {
        LibraryScreen(
            listState = LibraryListState.Loaded(
                listOf(
                    LibraryItem(book, BookAvailability.AVAILABLE),
                    LibraryItem(book.copy(id = BookId("preview-2"), title = "Gazette"), BookAvailability.MISSING),
                ),
            ),
            busy = false,
            message = null,
            pendingRemoval = null,
            actions = PreviewActions,
        )
    }
}

/**
 * One message at a time. A removal that can still be undone (CF-3) shows Undo for its undo window, then closes by
 * itself; the controller removes the book a moment after.
 */
@Composable
private fun MessageEffect(
    message: LibraryMessage?,
    snackbar: SnackbarHostState,
    onUndo: (BookId) -> Unit,
    onShown: () -> Unit,
) {
    val messageText = message?.let { messageText(it) }
    val undoLabel = stringResource(R.string.library_removed_undo)
    LaunchedEffect(message) {
        if (messageText == null) return@LaunchedEffect
        val undoable = (message as? LibraryMessage.Removed)?.takeIf { it.undo != null }
        if (undoable == null) {
            snackbar.showSnackbar(messageText)
        } else {
            val result = withTimeoutOrNull(undoable.undoMs) {
                snackbar.showSnackbar(messageText, actionLabel = undoLabel, duration = SnackbarDuration.Indefinite)
            }
            if (result == SnackbarResult.ActionPerformed) undoable.undo?.let(onUndo)
        }
        onShown()
    }
}

@Suppress("TooManyFunctions") // Implements LibraryActions.
private object PreviewActions : LibraryActions {
    override fun onAddPdf(documentUri: Uri) = Unit

    override fun onLocate(id: BookId, documentUri: Uri) = Unit

    override fun onOpenBook(book: Book) = Unit

    override fun onContinue(book: Book) = Unit

    override fun onRequestRemoval(book: Book) = Unit

    override fun onExportAudiobook(book: Book) = Unit

    override fun onConfirmRemoval() = Unit

    override fun onCancelRemoval() = Unit

    override fun onMessageShown() = Unit

    override fun onScreenResumed() = Unit
}
