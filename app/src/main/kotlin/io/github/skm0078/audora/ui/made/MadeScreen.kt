// File: app/src/main/kotlin/io/github/skm0078/audora/ui/made/MadeScreen.kt
// Purpose: Everything made from the books in one list, reached from the Jobs screen, and a book's own made files in
//          its details (ADR-068), so a saved audiobook is found, played, shared or deleted from the app.

package io.github.skm0078.audora.ui.made

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.di.LibraryEntryPoint
import io.github.skm0078.audora.ui.library.titleOrUntitled
import io.github.skm0078.audora.ui.theme.Dimens
import kotlinx.coroutines.flow.map

/** Every set from every book, newest first, with its book's title. */
@Composable
fun MadeRoute(onPlay: (String) -> Unit, onBack: () -> Unit) {
    val entry = LibraryEntryPoint.of(LocalContext.current)
    val outputs = remember { entry.outputs().observeAll() }
    val titles by remember {
        entry.library().books().map { books -> books.associate { it.id to it.title } }
    }.collectAsStateWithLifecycle(emptyMap())
    MadeScreen(rememberMadeSets(outputs), titles, onPlay, onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MadeScreen(
    sets: List<MadeSet>?,
    titles: Map<BookId, String>,
    onPlay: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.made_title), Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            Modifier.padding(innerPadding),
            contentPadding = PaddingValues(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceS),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
        ) {
            when {
                sets == null -> item { CircularProgressIndicator(Modifier.padding(Dimens.SpaceL)) }

                sets.isEmpty() -> item {
                    Text(stringResource(R.string.made_empty), style = MaterialTheme.typography.bodyLarge)
                }

                else -> items(sets, key = { it.set.key }) { made ->
                    MadeSetCard(made, titleOrUntitled(titles[made.set.bookId].orEmpty()), onPlay)
                }
            }
        }
    }
}

/** A book's made files in its details sheet; nothing at all while the book has none. */
@Composable
internal fun MadeFromBook(bookId: BookId, onPlay: (String) -> Unit) {
    val context = LocalContext.current
    val outputs = remember(bookId) { LibraryEntryPoint.of(context).outputs().observe(bookId) }
    val sets = rememberMadeSets(outputs)
    if (sets.isNullOrEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
        Text(
            stringResource(R.string.made_from_book),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        sets.forEach { made -> MadeSetCard(made, bookTitle = null, onPlay = onPlay) }
    }
}
