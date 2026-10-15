// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/LibraryScreen.kt
// Purpose: Home screen: the books in the library, Add PDF, and finding a moved file again (P1-01).

package io.github.skm0078.audora.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import io.github.skm0078.audora.ui.theme.AudoraTheme

@Composable
fun LibraryScreen(
    listState: LibraryListState,
    busy: Boolean,
    message: LibraryMessage?,
    pendingRemoval: Book?,
    actions: LibraryActions,
    modifier: Modifier = Modifier,
    banner: @Composable () -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    // Saveable: the picker can outlive this process, and its result must still reach the right book.
    var locating by rememberSaveable { mutableStateOf<String?>(null) }
    val addPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(actions::onAddPdf)
    }
    val locatePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id = locating
        locating = null
        if (uri != null && id != null) actions.onLocate(BookId(id), uri)
    }
    val pickPdf = { addPicker.launch(PDF_MIME_TYPES) }

    LifecycleResumeEffect(Unit) {
        actions.onScreenResumed()
        onPauseOrDispose {}
    }
    val messageText = message?.let { messageText(it) }
    LaunchedEffect(message) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            actions.onMessageShown()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { LibraryTopBar(onOpenSettings = actions::onOpenSettings) },
        floatingActionButton = {
            if (listState is LibraryListState.Loaded && listState.items.isNotEmpty()) {
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
                        locatePicker.launch(PDF_MIME_TYPES)
                    } else {
                        actions.onOpenBook(item.book)
                    }
                },
                onRemove = actions::onRequestRemoval,
            )
        }
    }
    pendingRemoval?.let { RemovalDialog(it, actions) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryTopBar(onOpenSettings: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(R.string.library_title)) },
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.settings_title))
            }
        },
    )
}

@Composable
private fun LibraryContent(
    listState: LibraryListState,
    busy: Boolean,
    onAddPdf: () -> Unit,
    onTap: (LibraryItem) -> Unit,
    onRemove: (Book) -> Unit,
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

        is LibraryListState.Loaded -> if (listState.items.isEmpty()) {
            EmptyLibrary(enabled = !busy, onAddPdf = onAddPdf)
        } else {
            BookList(items = listState.items, onTap = onTap, onRemove = onRemove)
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

@Composable
private fun BookList(items: List<LibraryItem>, onTap: (LibraryItem) -> Unit, onRemove: (Book) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(bottom = FAB_CLEARANCE)) {
        items(items, key = { it.book.id.value }) { item ->
            BookRow(item = item, onTap = { onTap(item) }, onRemove = { onRemove(item.book) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun EmptyLibrary(enabled: Boolean, onAddPdf: () -> Unit) {
    Centered {
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

/** OpenDocument filters by MIME type; providers that mislabel PDFs are caught by the header check. */
private val PDF_MIME_TYPES = arrayOf("application/pdf")

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

private object PreviewActions : LibraryActions {
    override fun onAddPdf(documentUri: Uri) = Unit

    override fun onLocate(id: BookId, documentUri: Uri) = Unit

    override fun onOpenBook(book: Book) = Unit

    override fun onRequestRemoval(book: Book) = Unit

    override fun onConfirmRemoval() = Unit

    override fun onCancelRemoval() = Unit

    override fun onMessageShown() = Unit

    override fun onOpenSettings() = Unit

    override fun onScreenResumed() = Unit
}
