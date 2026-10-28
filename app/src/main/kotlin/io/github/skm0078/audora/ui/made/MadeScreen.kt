// File: app/src/main/kotlin/io/github/skm0078/audora/ui/made/MadeScreen.kt
// Purpose: The Made place: everything made from the books as covers or a list, picked like the library's, with the
//          running jobs one tap away and a filter by kind (ADR-068, ADR 2026-10-28-made-tab); and a book's own made
//          files in its details, so a saved audiobook is found, played, shared or deleted.

package io.github.skm0078.audora.ui.made

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.OutputKind
import io.github.skm0078.audora.di.LibraryEntryPoint
import io.github.skm0078.audora.ui.jobs.rememberRunningJobCount
import io.github.skm0078.audora.ui.library.CoverSource
import io.github.skm0078.audora.ui.library.LibraryLayout
import io.github.skm0078.audora.ui.library.LibraryLayoutState
import io.github.skm0078.audora.ui.library.rememberLibraryLayout
import io.github.skm0078.audora.ui.library.titleOrUntitled
import io.github.skm0078.audora.ui.theme.Dimens
import kotlinx.coroutines.flow.map

/** Every set from every book, newest first, with its book; grid or list follows the library's own choice. */
@Composable
fun MadeRoute(onPlay: (String) -> Unit, onOpenJobs: () -> Unit) {
    val entry = LibraryEntryPoint.of(LocalContext.current)
    val outputs = remember { entry.outputs().observeAll() }
    val books by remember {
        entry.library().books().map { books -> books.associateBy { it.id } }
    }.collectAsStateWithLifecycle(emptyMap())
    MadeScreen(
        sets = rememberMadeSets(outputs),
        titles = books.mapValues { it.value.title },
        onPlay = onPlay,
        runningJobs = rememberRunningJobCount(),
        onOpenJobs = onOpenJobs,
        look = MadeLook(books, entry.covers(), rememberLibraryLayout()),
    )
}

/**
 * How the sets look: their books (for covers), where covers come from, and grid or list. The layout is the
 * library's own, so the listener sets it once, in either place, for both (ADR 2026-10-28-made-tab).
 */
class MadeLook(
    val books: Map<BookId, Book> = emptyMap(),
    val covers: CoverSource = CoverSource.None,
    val view: LibraryLayoutState = LibraryLayoutState(LibraryLayout.AUTO) {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MadeScreen(
    sets: List<MadeSet>?,
    titles: Map<BookId, String>,
    onPlay: (String) -> Unit,
    runningJobs: Int,
    onOpenJobs: () -> Unit,
    modifier: Modifier = Modifier,
    look: MadeLook = MadeLook(),
) {
    val list = (look.view.layout ?: LibraryLayout.AUTO).showsList(LocalDensity.current.fontScale)
    Scaffold(
        modifier = modifier.fillMaxSize(),
        // A place, not a page: no back arrow, as on the library (ADR 2026-10-28-four-places).
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.tab_made),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                },
                actions = {
                    JobsChip(runningJobs, onOpenJobs)
                    LayoutToggle(list, look.view.onLayout, Modifier.padding(horizontal = Dimens.SpaceS))
                },
            )
        },
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            var kindName by rememberSaveable { mutableStateOf<String?>(null) }
            val kind = kindName?.let { name -> OutputKind.entries.firstOrNull { it.name == name } }
            sets?.let { KindChips(it, kind) { picked -> kindName = picked?.name } }
            when {
                sets == null -> Centered { CircularProgressIndicator() }

                sets.isEmpty() -> Centered {
                    Text(
                        stringResource(R.string.made_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                }

                else -> {
                    val shown = sets.filter { kind == null || it.set.kind == kind }.map { made ->
                        val book = look.books[made.set.bookId]
                        MadeSetItem(made, titleOrUntitled(titles[made.set.bookId].orEmpty()), book)
                    }
                    if (list) MadeList(shown, look.covers, onPlay) else MadeGrid(shown, look.covers, onPlay)
                }
            }
        }
    }
}

/** All, then each kind there is with how many: only when there is more than one kind to tell apart. */
@Composable
private fun KindChips(sets: List<MadeSet>, selected: OutputKind?, onPick: (OutputKind?) -> Unit) {
    val counts = sets.groupingBy { it.set.kind }.eachCount()
    if (counts.size < 2) return
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceXs),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onPick(null) },
            label = {
                Text(stringResource(R.string.made_filter_count, stringResource(R.string.made_filter_all), sets.size))
            },
        )
        OutputKind.entries.filter { it in counts }.forEach { kind ->
            FilterChip(
                selected = selected == kind,
                onClick = { onPick(kind) },
                label = { Text(stringResource(R.string.made_filter_count, kindLabel(kind), counts.getValue(kind))) },
            )
        }
    }
}

@Composable
private fun MadeGrid(items: List<MadeSetItem>, covers: CoverSource, onPlay: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(TILE_MIN_WIDTH),
        contentPadding = PaddingValues(Dimens.ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceL),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXl),
    ) {
        items(items, key = { it.made.set.key }) { MadeSetTile(it, covers, onPlay) }
    }
}

@Composable
private fun MadeList(items: List<MadeSetItem>, covers: CoverSource, onPlay: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceS)) {
        items(items, key = { it.made.set.key }) { item ->
            MadeSetRow(item, covers, onPlay)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(Dimens.SpaceXl), contentAlignment = Alignment.Center) { content() }
}

/** A book's made files in its details sheet, as rows; nothing at all while the book has none. */
@Composable
internal fun MadeFromBook(bookId: BookId, onPlay: (String) -> Unit) {
    val context = LocalContext.current
    val entry = remember { LibraryEntryPoint.of(context) }
    val outputs = remember(bookId) { entry.outputs().observe(bookId) }
    val sets = rememberMadeSets(outputs)
    if (sets.isNullOrEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
        Text(
            stringResource(R.string.made_from_book),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        sets.forEach { made -> MadeSetRow(MadeSetItem(made, bookTitle = null, book = null), entry.covers(), onPlay) }
    }
}

/** Two tiles across a phone, with room under each cover for its three buttons. */
private val TILE_MIN_WIDTH = 152.dp
