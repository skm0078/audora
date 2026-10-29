// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/BookVoiceNote.kt
// Purpose: "Your book remembers you" (touch 8): the book menu says which voice and speed this book keeps for itself,
//          set with "Use for this book only" in the reader (plan item 4), so the listener sees it was remembered.

package io.github.skm0078.audora.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.notes.BookSettings
import io.github.skm0078.audora.di.LibraryEntryPoint
import io.github.skm0078.audora.speech.voices.VoiceLibrary
import io.github.skm0078.audora.speech.voices.VoiceRow
import io.github.skm0078.audora.ui.reader.speedLabel
import io.github.skm0078.audora.ui.theme.Dimens
import kotlinx.coroutines.flow.emptyFlow

/** What a book keeps for itself: the voice's name for its language, its own speed; each null when it follows all. */
internal data class BookVoiceNote(val voice: String?, val rate: Float?) {
    companion object {
        /**
         * The note for [settings], naming the voice the book uses for [lang] (its own language, else the first it
         * keeps) from [rows]; null when the book keeps neither a voice nor a speed. A voice no longer on the phone
         * is left out rather than named wrongly.
         */
        fun of(settings: BookSettings?, lang: Lang?, rows: List<VoiceRow>): BookVoiceNote? {
            if (settings == null) return null
            val voice = (lang?.let(settings.voices::get) ?: settings.voices.values.firstOrNull())
                ?.let { id -> rows.firstOrNull { it.id == id }?.label }
            return BookVoiceNote(voice, settings.rate).takeIf { voice != null || settings.rate != null }
        }
    }
}

/** The book's own language as one the app reads, or null (unknown, or one it does not read). */
internal fun bookLang(book: Book): Lang? = Lang.entries.firstOrNull { it.bcp47Tag == book.language }

/** The note for [book], read while the menu shows; null until read, and when there is nothing to say. */
@Composable
internal fun rememberBookVoiceNote(book: Book): BookVoiceNote? {
    val context = LocalContext.current.applicationContext
    val store = remember(context) { LibraryEntryPoint.of(context).bookSettings() }
    var settings by remember(book.id) { mutableStateOf<BookSettings?>(null) }
    LaunchedEffect(book.id, store) { settings = store.get(book.id) }
    // Only once the book keeps something: the voice list is read only then.
    val rows by remember(settings != null) {
        if (settings == null) emptyFlow() else VoiceLibrary.get(context).rows()
    }.collectAsState(emptyList())
    return BookVoiceNote.of(settings, bookLang(book), rows)
}

/**
 * "Bruno reads this one, at 1.1×. Kept for this book only." [onChange] opens the book, where the speed button
 * changes it.
 */
@Composable
internal fun BookVoiceCard(note: BookVoiceNote, onChange: () -> Unit, modifier: Modifier = Modifier) {
    val line = when {
        note.voice != null && note.rate != null ->
            stringResource(R.string.book_voice_reads_at, note.voice, speedLabel(note.rate))

        note.voice != null -> stringResource(R.string.book_voice_reads, note.voice)

        else -> stringResource(R.string.book_voice_at, speedLabel(note.rate ?: 1f))
    }
    Surface(modifier.fillMaxWidth(), MaterialTheme.shapes.medium, MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            Modifier.padding(start = Dimens.SpaceL, top = Dimens.SpaceS, bottom = Dimens.SpaceS, end = Dimens.SpaceXs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
        ) {
            Icon(painterResource(R.drawable.ic_headphones), contentDescription = null)
            Column(Modifier.weight(1f)) {
                Text(line, style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.book_voice_kept),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onChange) { Text(stringResource(R.string.book_voice_change)) }
        }
    }
}
