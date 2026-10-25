// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/notes/BookContext.kt
// Purpose: The open book's own settings and pronunciation fixes, for the renderer, voices and player (items 4, 1).

package io.github.skm0078.audora.playback.notes

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.StorageError
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.SpeechStyle
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.notes.BookSettings
import io.github.skm0078.audora.core.notes.BookSettingsStore
import io.github.skm0078.audora.core.notes.PronunciationStore
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.speech.normalize.PronunciationFixer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * One per playback service; [open] runs on the main thread before a book's first sentence is rendered, so that
 * sentence already has the book's speed, voices and fixes. The renderer reads [style], [voice] and [fixer] on its
 * own thread, so the state is volatile and replaced whole.
 *
 * [onRulesChanged] runs when the open book's fixes change after it opened (the reader added or removed one), so the
 * sentences rendered ahead are rendered again.
 */
class BookContext(
    private val settingsStore: BookSettingsStore,
    private val pronunciations: PronunciationStore,
    private val scope: CoroutineScope,
    private val onRulesChanged: () -> Unit = {},
) {
    @Volatile
    var bookId: BookId? = null
        private set

    /** The open book's own settings; null when it follows the global ones. */
    @Volatile
    var settings: BookSettings? = null
        private set

    @Volatile
    var fixer: PronunciationFixer = PronunciationFixer(emptyList())
        private set

    private var rulesJob: Job? = null

    suspend fun open(id: BookId) {
        if (id == bookId) return
        bookId = id
        settings = settingsStore.get(id)
        rulesJob?.cancel()
        fixer = PronunciationFixer(emptyList())
        // The first list is the book's fixes as it opens; later lists are edits while it plays.
        val rules = pronunciations.observe(id)
        rulesJob = scope.launch {
            var first = true
            rules.collect { list ->
                fixer = PronunciationFixer(list)
                AudoraLog.i(TAG, "Pronunciation fixes book=${id.logLabel} rules=${list.size}")
                if (!first) onRulesChanged()
                first = false
            }
        }
        AudoraLog.i(TAG, "Book context book=${id.logLabel} ownSettings=${settings != null}")
    }

    /** [global] with the open book's speed, pace and pitch on top, when it has its own. */
    fun style(global: SpeechStyle): SpeechStyle = settings?.over(global) ?: global

    /** The open book's own voice for [lang]; null to use the global choice. */
    fun voice(lang: Lang): VoiceId? = settings?.voices?.get(lang)

    /** Turns the open book's own settings on (starting from [from]) or off; false with no book open. */
    suspend fun setOwnSettings(on: Boolean, from: SpeechStyle): Outcome<Boolean, StorageError> {
        val id = bookId ?: return Outcome.Success(false)
        val next = if (on) {
            settings ?: BookSettings(
                id,
                rate = from.rate,
                pace = from.pace,
                pitch = from.pitch,
            )
        } else {
            null
        }
        return save(id, next)
    }

    /** With own settings on, the change is the book's and true is returned; otherwise nothing is saved. */
    suspend fun updateIfOwn(change: (BookSettings) -> BookSettings): Outcome<Boolean, StorageError> {
        val id = bookId
        val current = settings
        return if (id == null || current == null) Outcome.Success(false) else save(id, change(current))
    }

    private suspend fun save(id: BookId, next: BookSettings?): Outcome<Boolean, StorageError> {
        val saved = if (next == null) settingsStore.clear(id) else settingsStore.put(next)
        return when (saved) {
            is Outcome.Success -> {
                settings = next
                AudoraLog.i(TAG, "Book settings book=${id.logLabel} own=${next != null} voices=${next?.voices?.size}")
                Outcome.Success(true)
            }

            is Outcome.Failure -> {
                AudoraLog.w(TAG, "Book settings not saved, book=${id.logLabel}: ${saved.error}")
                saved
            }
        }
    }

    private companion object {
        const val TAG = "Playback.BookContext"
    }
}
