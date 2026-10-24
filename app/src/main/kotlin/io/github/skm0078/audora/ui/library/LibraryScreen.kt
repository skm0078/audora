// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/LibraryScreen.kt
// Purpose: Library tab: Continue reading, a cover grid (a list at large text), Add book, the duplicate check (ADR-036).

package io.github.skm0078.audora.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
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
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import io.github.skm0078.audora.ui.kids.LocalKidsMode
import io.github.skm0078.audora.ui.shell.readableSidePadding
import io.github.skm0078.audora.ui.theme.AudoraTheme
import io.github.skm0078.audora.ui.theme.Dimens

/** [runningJobs]: how many jobs and downloads work now, for the badge on the Jobs button (ADR-067). */
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
) {
    val snackbar = remember { SnackbarHostState() }
    // Saveable: the picker can outlive this process, and its result must still reach the right book.
    var locating by rememberSaveable { mutableStateOf<String?>(null) }
    var details by rememberSaveable { mutableStateOf<String?>(null) } // the book whose details sheet is open
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
    MessageEffect(message.takeIf { duplicate == null }, snackbar, actions::onMessageShown)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = TAB_INSETS,
        topBar = { LibraryTopBar(listState, actions, runningJobs) },
        floatingActionButton = {
            if (!LocalKidsMode.current && listState is LibraryListState.Loaded && listState.all.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { if (!busy) pickPdf() },
                    icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                    text = { Text(stringResource(R.string.library_add_pdf)) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            banner()
            LibraryContent(
                listState = listState,
                busy = busy,
                onAddPdf = pickPdf,
                onTap = { item ->
                    if (item.needsLocating) {
                        locating = item.book.id.value
                        locatePicker.launch(BOOK_MIME_TYPES)
                    } else {
                        actions.onOpenBook(item.book)
                    }
                },
                covers = covers,
                menu = actions,
                onDetails = { details = it.book.id.value },
            )
        }
    }
    BookSheets(listState, actions, covers, details) { details = null }
    pendingRemoval?.let { RemovalDialog(it, actions) }
    duplicate?.let { DuplicateDialog(it, listState, actions) }
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
) {
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
            EmptyLibrary(enabled = !busy, onAddPdf = onAddPdf)
        } else {
            Column {
                ShelfRow(listState, menu::onQuery)
                if (listState.items.isEmpty()) {
                    Centered {
                        Text(
                            stringResource(R.string.library_no_match),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    val books = BookCallbacks(onTap, menu, onDetails)
                    // At 150 % text and above, two lines of title no longer fit under a cover: a list (item 39).
                    if (LocalDensity.current.fontScale >= LARGE_TEXT_SCALE) {
                        BookList(listState, books, covers)
                    } else {
                        BookGrid(listState, books, covers)
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

/** What a tap on a book, its menu or the Continue button does. */
private class BookCallbacks(
    val onTap: (LibraryItem) -> Unit,
    actions: LibraryActions,
    val onDetails: (LibraryItem) -> Unit,
) {
    val onRemove: (Book) -> Unit = actions::onRequestRemoval
    val onExport: (Book) -> Unit = actions::onExportAudiobook
    val onContinue: (Book) -> Unit = actions::onContinue
    val onAddToUpNext: (Book) -> Unit = actions::onAddToUpNext
    val onAction: (Book, BookAction) -> Unit = actions::onBookAction
}

@Composable
private fun BookGrid(list: LibraryListState.Loaded, books: BookCallbacks, covers: CoverSource) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(Dimens.CoverGridMin),
        contentPadding = PaddingValues(
            start = Dimens.ScreenPadding,
            end = Dimens.ScreenPadding,
            top = Dimens.SpaceS,
            bottom = FAB_CLEARANCE,
        ),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceL),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXl),
    ) {
        list.continueReading?.let { latest ->
            item(key = CONTINUE_KEY, span = { GridItemSpan(maxLineSpan) }) {
                ContinueReadingCard(
                    latest,
                    onContinue = { books.onContinue(latest.book) },
                    onOpen = { books.onTap(latest) },
                    covers = covers,
                )
            }
        }
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
            )
        }
    }
}

@Composable
private fun BookList(list: LibraryListState.Loaded, books: BookCallbacks, covers: CoverSource) {
    // Rows stay a readable width in the middle of a tablet or a phone in landscape.
    BoxWithConstraints {
        val side = readableSidePadding(maxWidth)
        LazyColumn(contentPadding = PaddingValues(start = side, end = side, bottom = FAB_CLEARANCE)) {
            list.continueReading?.let { latest ->
                item(key = CONTINUE_KEY) {
                    Box(Modifier.padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceS)) {
                        ContinueReadingCard(
                            latest,
                            onContinue = { books.onContinue(latest.book) },
                            onOpen = { books.onTap(latest) },
                            covers = covers,
                        )
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
                )
                HorizontalDivider()
            }
        }
    }
}

/**
 * Plan item 27: a picked PDF the library already has. Audora keeps one copy, never a second: the fingerprint keys the
 * book's place, so two copies would fight over it (ADR-036). The dialog says so and offers the book.
 */
@Composable
private fun DuplicateDialog(notice: DuplicateNotice, listState: LibraryListState, actions: LibraryActions) {
    val title = titleOrUntitled(notice.title)
    val onDismiss = actions::onMessageShown
    val onOpen: () -> Unit = {
        actions.onMessageShown()
        bookIn(listState, notice.id)?.let(actions::onOpenBook)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_duplicate_title)) },
        text = {
            val body = if (notice.moved) R.string.library_duplicate_moved else R.string.library_duplicate_same
            Text(stringResource(body, title))
        },
        confirmButton = { TextButton(onClick = onOpen) { Text(stringResource(R.string.library_duplicate_open)) } },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_duplicate_close)) }
        },
    )
}

@Composable
private fun EmptyLibrary(enabled: Boolean, onAddPdf: () -> Unit) {
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
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
    }
}

private val FAB_CLEARANCE = 88.dp

/** The app shell's bottom bar takes the bottom inset (ADR-036). */
private val TAB_INSETS: WindowInsets
    @Composable get() = ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)

/** Font scale from which the grid becomes a list (item 39). */
private const val LARGE_TEXT_SCALE = 1.5f

/** Book ids are UUIDs, so this key never clashes with a row's. */
private const val CONTINUE_KEY = "continue-reading"

/**
 * OpenDocument filters by MIME type; providers that mislabel files are caught by the header check. PDFs, EPUBs and
 * text files (items 42 and 43, ADR-042); Word, OpenDocument, RTF, HTML and Markdown files, and pictures, which become
 * a photo book (ADR-066). `text/` as a whole, since Markdown has several labels. The first-run picker uses it too.
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

@Composable
private fun MessageEffect(message: LibraryMessage?, snackbar: SnackbarHostState, onShown: () -> Unit) {
    val messageText = message?.let { messageText(it) }
    LaunchedEffect(message) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            onShown()
        }
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
