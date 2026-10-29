// File: app/src/main/kotlin/io/github/skm0078/audora/StudioReaderHooks.kt
// Purpose: MainActivity's reader-side studio features: search in the book, look up a word, voice notes, and the
//          clean-up of files left by removed books.

package io.github.skm0078.audora

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.Library
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.notes.VoiceNoteStore
import io.github.skm0078.audora.core.words.LookedUpWord
import io.github.skm0078.audora.di.LibraryEntryPoint
import io.github.skm0078.audora.pdf.PIPELINE_VERSION
import io.github.skm0078.audora.playback.notes.VoiceNoteAudio
import io.github.skm0078.audora.playback.search.SearchIndexes
import io.github.skm0078.audora.playback.studio.StudioMode
import io.github.skm0078.audora.speech.dictionary.Dictionaries
import io.github.skm0078.audora.speech.ocr.OcrModelsState
import io.github.skm0078.audora.ui.reader.BookNotesModel
import io.github.skm0078.audora.ui.reader.LookUpBinding
import io.github.skm0078.audora.ui.reader.LookUpModel
import io.github.skm0078.audora.ui.reader.PlaybackPort
import io.github.skm0078.audora.ui.reader.ReaderModel
import io.github.skm0078.audora.ui.reader.SearchModel
import io.github.skm0078.audora.ui.reader.SearchScreen
import io.github.skm0078.audora.ui.reader.SearchSource
import io.github.skm0078.audora.ui.reader.VoiceNotesBinding
import io.github.skm0078.audora.ui.reader.VoiceNotesModel
import io.github.skm0078.audora.ui.reader.rememberRecordWithPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Studio's reader features for one book: search, voice notes and look up. */
internal class ReaderStudio(val search: SearchModel, val voiceNotes: VoiceNotesBinding, val lookUp: LookUpBinding)

@Composable
internal fun rememberReaderStudio(
    id: BookId,
    model: ReaderModel,
    scope: CoroutineScope,
    notes: BookNotesModel,
    port: PlaybackPort,
    indexes: SearchIndexes,
    voiceNotes: VoiceNoteParts,
) = ReaderStudio(
    rememberSearch(id, model, scope, indexes),
    rememberVoiceNotes(id, notes, port, voiceNotes),
    rememberLookUp(id),
)

/** Search in the book (plan item 16): its index is built from the reader's own document while search is open. */
@Composable
private fun rememberSearch(
    id: BookId,
    model: ReaderModel,
    scope: CoroutineScope,
    indexes: SearchIndexes,
): SearchModel {
    val indexThread = remember { Dispatchers.IO.limitedParallelism(1) }
    val search = remember(model) {
        val source = SearchSource({ model.state.value.pageCount }, model::sentencesForSearch) { indexes.open(id) }
        SearchModel(id, source, scope, indexThread, PIPELINE_VERSION)
    }
    DisposableEffect(search) { onDispose { search.close() } }
    return search
}

/** The search sheet: picking a hit goes to its page and closes the sheet. */
@Composable
internal fun SearchRoute(search: SearchModel, onGoToPage: (Int) -> Unit, onClose: () -> Unit) {
    DisposableEffect(search) {
        search.open()
        onDispose { search.close() }
    }
    val state by search.state.collectAsStateWithLifecycle()
    SearchScreen(state, search::setQuery, onHit = { hit ->
        onGoToPage(hit.page)
        onClose()
    }, onDismiss = onClose)
}

/**
 * The dictionary on tap (plan item 33, ADR-047): the sentence menu's "Look up a word". The download state follows
 * the dictionary in use (the picked word's script, else the sentence's language); a word looked up while its
 * dictionary was missing is looked up again once it is installed. Each word found joins the book's "Words you looked
 * up".
 */
@Composable
private fun rememberLookUp(id: BookId): LookUpBinding {
    val context = LocalContext.current.applicationContext
    val dictionaries = remember { Dictionaries.get(context) }
    val scope = rememberCoroutineScope()
    val words = remember { LibraryEntryPoint.of(context).lookedUpWords() }
    val model = remember(id) {
        val found: (String, String, ReadingUnit) -> Unit = { word, meaning, at ->
            words.add(LookedUpWord(id, word, meaning, at.page, at.text, at.lang, System.currentTimeMillis()))
        }
        LookUpModel(dictionaries::forLanguage, scope, dictionaries::download, dictionaries::cancel, onFound = found)
    }
    val state by model.state.collectAsStateWithLifecycle()
    val lang = state?.dictionaryLang ?: Lang.EN
    val download by remember(lang) { dictionaries.state(lang) }.collectAsStateWithLifecycle(initialValue = null)
    val ready = download is OcrModelsState.Ready
    LaunchedEffect(ready, state?.missing) {
        val word = state?.word
        if (ready && state?.missing == true && word != null) model.pick(word)
    }
    return LookUpBinding(state, download, model)
}

/** Where voice notes live: the store, the audio files, and a scope that outlives the reader. */
internal class VoiceNoteParts(val store: VoiceNoteStore, val audio: VoiceNoteAudio, val scope: CoroutineScope)

/** Voice notes on sentences (plan item 32); reading pauses while one is recorded or played. */
@Composable
private fun rememberVoiceNotes(
    id: BookId,
    notes: BookNotesModel,
    port: PlaybackPort,
    parts: VoiceNoteParts,
): VoiceNotesBinding {
    val model = remember(id) {
        // Activity-wide scope: a recording saved as the reader closes must still be written.
        VoiceNotesModel(id, parts.store, parts.audio, parts.scope, notes::anchorOf, port::pause).also { it.start() }
    }
    DisposableEffect(model) { onDispose { model.close() } }
    val state by model.state.collectAsStateWithLifecycle()
    val record = rememberRecordWithPermission(model)
    return VoiceNotesBinding(state, model, record)
}

/** Search indexes and studio audio of removed books (and of older builds), and voice notes whose note is gone. */
internal suspend fun dropStaleBookFiles(
    context: Context,
    library: Library,
    indexes: SearchIndexes,
    voiceNotes: VoiceNoteParts,
) = withContext(Dispatchers.IO) {
    val books = library.books().first().mapTo(HashSet()) { it.id }
    indexes.dropBooksNotIn(books)
    StudioMode.reclaim(context, books)
    voiceNotes.audio.dropFilesNotIn(voiceNotes.store.observeAll().first().mapTo(HashSet()) { it.fileName })
}
