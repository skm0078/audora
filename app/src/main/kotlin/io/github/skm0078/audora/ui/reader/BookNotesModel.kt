// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/BookNotesModel.kt
// Purpose: The reader's bookmarks with notes (plan item 29), pronunciation fixes (item 1) and the sentence menu.

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.StorageError
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.notes.Bookmark
import io.github.skm0078.audora.core.notes.BookmarkStore
import io.github.skm0078.audora.core.notes.PronunciationRule
import io.github.skm0078.audora.core.notes.PronunciationStore
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.resume.ResumeAnchor
import io.github.skm0078.audora.core.resume.SentenceFingerprint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * @property bookmarks this book's bookmarks in reading order.
 * @property rules the pronunciation fixes that apply to this book: its own and every-book ones.
 * @property menuFor the sentence a long-press picked, while its menu shows.
 * @property fixing the sentence whose words are being fixed, while the pronunciation dialog shows.
 * @property editing the bookmark whose note is being written.
 * @property listOpen the bookmarks sheet is showing.
 * @property failed the last save failed; the screen says so once.
 */
data class NotesState(
    val bookmarks: List<Bookmark> = emptyList(),
    val rules: List<PronunciationRule> = emptyList(),
    val menuFor: ReadingUnit? = null,
    val fixing: ReadingUnit? = null,
    val editing: Bookmark? = null,
    val listOpen: Boolean = false,
    val failed: Boolean = false,
)

/** What the screen calls; [BookNotesModel] implements it, [None] stands in where notes are not wired (previews). */
@Suppress("TooManyFunctions") // One per control in the sentence menu, the bookmarks sheet and the fix dialog.
interface BookNotesActions {
    fun openMenu(unit: ReadingUnit) = Unit

    fun dismissMenu() = Unit

    fun bookmark(unit: ReadingUnit) = Unit

    fun showList() = Unit

    fun hideList() = Unit

    fun editNote(bookmark: Bookmark) = Unit

    fun saveNote(note: String) = Unit

    fun dismissNote() = Unit

    fun delete(bookmark: Bookmark) = Unit

    fun fix(unit: ReadingUnit) = Unit

    fun addRule(written: String, spoken: String, wholeWord: Boolean, thisBookOnly: Boolean) = Unit

    fun deleteRule(rule: PronunciationRule) = Unit

    fun dismissFix() = Unit

    fun clearFailure() = Unit

    companion object {
        /** Notes not available (previews): every control does nothing. */
        val None = object : BookNotesActions {}
    }
}

/**
 * Plain state holder, one per open book, beside [ReaderModel]. Bookmarks are anchored like resume positions (offsets
 * and hashes, never the PDF's text) at [pipelineVersion], so a later pipeline finds them again. Notes and fixes are
 * the listener's own words: stored, never logged.
 */
@Suppress("TooManyFunctions") // The actions, each a one-liner or a store write.
class BookNotesModel(
    private val bookId: BookId,
    private val bookmarks: BookmarkStore,
    private val pronunciations: PronunciationStore,
    private val scope: CoroutineScope,
    private val pipelineVersion: Int,
    private val clock: () -> Long = System::currentTimeMillis,
) : BookNotesActions {
    private val fingerprint = SentenceFingerprint()
    private val mutableState = MutableStateFlow(NotesState())
    val state: StateFlow<NotesState> = mutableState.asStateFlow()
    private val logContext = "book=${bookId.logLabel}"
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch { bookmarks.observe(bookId).collect { list -> mutableState.update { it.copy(bookmarks = list) } } }
        scope.launch {
            pronunciations.observe(bookId).collect { list -> mutableState.update { it.copy(rules = list) } }
        }
    }

    override fun openMenu(unit: ReadingUnit) = mutableState.update { it.copy(menuFor = unit) }

    override fun dismissMenu() = mutableState.update { it.copy(menuFor = null) }

    /** Bookmarks [unit] and opens its note, so a note can be written straight away (or the dialog closed). */
    override fun bookmark(unit: ReadingUnit) {
        mutableState.update { it.copy(menuFor = null) }
        val anchor = ResumeAnchor(
            bookId = bookId,
            page = unit.page,
            charStart = unit.charStart,
            fingerprint = fingerprint.head(unit),
            tailFingerprint = fingerprint.tail(unit),
            pipelineVersion = pipelineVersion,
            savedAtMillis = clock(),
        )
        scope.launch {
            when (val added = bookmarks.add(Bookmark(0, anchor))) {
                is Outcome.Success -> {
                    AudoraLog.i(TAG, "Bookmark added, $logContext page=${unit.page} id=${added.value}")
                    mutableState.update { it.copy(editing = Bookmark(added.value, anchor)) }
                }

                is Outcome.Failure -> failed("bookmark", added.error)
            }
        }
    }

    override fun showList() = mutableState.update { it.copy(listOpen = true) }

    override fun hideList() = mutableState.update { it.copy(listOpen = false) }

    override fun editNote(bookmark: Bookmark) = mutableState.update { it.copy(editing = bookmark) }

    override fun saveNote(note: String) {
        val bookmark = state.value.editing ?: return
        mutableState.update { it.copy(editing = null) }
        if (note.trim() == bookmark.note) return
        scope.launch {
            val saved = bookmarks.updateNote(bookmark.id, note.trim())
            if (saved is Outcome.Failure) failed("note", saved.error)
        }
    }

    override fun dismissNote() = mutableState.update { it.copy(editing = null) }

    override fun delete(bookmark: Bookmark) {
        scope.launch {
            when (val deleted = bookmarks.delete(bookmark.id)) {
                is Outcome.Success -> AudoraLog.i(TAG, "Bookmark deleted, ${bookmark.logLabel}")
                is Outcome.Failure -> failed("delete bookmark", deleted.error)
            }
        }
    }

    override fun fix(unit: ReadingUnit) = mutableState.update { it.copy(menuFor = null, fixing = unit) }

    /** A fix for the language of the sentence it was opened from; it applies from the next sentence rendered. */
    override fun addRule(written: String, spoken: String, wholeWord: Boolean, thisBookOnly: Boolean) {
        val lang = state.value.fixing?.lang ?: Lang.EN
        if (written.isBlank() || spoken.isBlank()) return
        val rule = PronunciationRule(0, bookId.takeIf { thisBookOnly }, lang, written.trim(), spoken.trim(), wholeWord)
        scope.launch {
            when (val added = pronunciations.add(rule)) {
                is Outcome.Success -> AudoraLog.i(
                    TAG,
                    "Pronunciation fix added, ${rule.copy(id = added.value).logLabel}",
                )

                is Outcome.Failure -> failed("pronunciation", added.error)
            }
        }
    }

    override fun deleteRule(rule: PronunciationRule) {
        scope.launch {
            when (val deleted = pronunciations.delete(rule.id)) {
                is Outcome.Success -> AudoraLog.i(TAG, "Pronunciation fix deleted, ${rule.logLabel}")
                is Outcome.Failure -> failed("delete pronunciation", deleted.error)
            }
        }
    }

    override fun dismissFix() = mutableState.update { it.copy(fixing = null) }

    override fun clearFailure() = mutableState.update { it.copy(failed = false) }

    private fun failed(what: String, error: StorageError) {
        AudoraLog.w(TAG, "Could not save $what, $logContext: ${error.detail}")
        mutableState.update { it.copy(failed = true) }
    }

    private companion object {
        const val TAG = "Audora.Notes"
    }
}
