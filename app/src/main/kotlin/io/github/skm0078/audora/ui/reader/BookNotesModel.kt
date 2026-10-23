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
import io.github.skm0078.audora.core.notes.Highlight
import io.github.skm0078.audora.core.notes.HighlightPlacement
import io.github.skm0078.audora.core.notes.HighlightStore
import io.github.skm0078.audora.core.notes.PronunciationRule
import io.github.skm0078.audora.core.notes.PronunciationStore
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.map
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
    val highlights: List<Highlight> = emptyList(),
    val placed: PlacedHighlights = PlacedHighlights(),
) {
    /** The highlights covering [unit] on the page showing (plan item 30); two when sentences were merged. */
    fun highlightsOn(unit: ReadingUnit): List<Highlight> =
        placed.byIndex[unit.index]?.takeIf { placed.page == unit.page }.orEmpty()
}

/**
 * The showing page's highlights by the [ReadingUnit.index] of each sentence they cover, found again after a pipeline
 * change merged, split or moved sentences ([HighlightPlacement]). [page] is null before a page shows.
 */
data class PlacedHighlights(val page: Int? = null, val byIndex: Map<Int, List<Highlight>> = emptyMap())

/** What the screen calls; [BookNotesModel] implements it, [None] stands in where notes are not wired (previews). */
@Suppress("TooManyFunctions") // One per control in the sentence menu, the bookmarks sheet and the fix dialog.
interface BookNotesActions {
    fun openMenu(unit: ReadingUnit) = Unit

    fun dismissMenu() = Unit

    fun bookmark(unit: ReadingUnit) = Unit

    /** Highlights [unit], or removes its highlight when it has one (plan item 30). */
    fun toggleHighlight(unit: ReadingUnit) = Unit

    fun showList() = Unit

    fun hideList() = Unit

    fun editNote(bookmark: Bookmark) = Unit

    fun saveNote(note: String) = Unit

    fun dismissNote() = Unit

    fun delete(bookmark: Bookmark) = Unit

    fun fix(unit: ReadingUnit) = Unit

    fun addRule(written: String, spoken: String, wholeWord: Boolean, thisBookOnly: Boolean) = Unit

    fun deleteRule(rule: PronunciationRule) = Unit

    /** The reader shows [units] (one page's, current pipeline): highlights are placed on them. */
    fun showing(units: List<ReadingUnit>) = Unit

    fun dismissFix() = Unit

    fun clearFailure() = Unit

    companion object {
        /** Notes not available (previews): every control does nothing. */
        val None = object : BookNotesActions {}
    }
}

/** The stores one book's notes live in; highlights are absent where the screen has none (previews, older tests). */
class NoteStores(
    val bookmarks: BookmarkStore,
    val pronunciations: PronunciationStore,
    val highlights: HighlightStore? = null,
)

/**
 * Plain state holder, one per open book, beside [ReaderModel]. Bookmarks are anchored like resume positions (offsets
 * and hashes, never the PDF's text) at [pipelineVersion], so a later pipeline finds them again. Notes and fixes are
 * the listener's own words: stored, never logged.
 */
@Suppress("TooManyFunctions") // The actions, each a one-liner or a store write.
class BookNotesModel(
    private val bookId: BookId,
    stores: NoteStores,
    private val scope: CoroutineScope,
    private val pipelineVersion: Int,
    private val clock: () -> Long = System::currentTimeMillis,
) : BookNotesActions {
    private val bookmarks = stores.bookmarks
    private val pronunciations = stores.pronunciations
    private val highlights = stores.highlights
    private val fingerprint = SentenceFingerprint()
    private val placement = HighlightPlacement(pipelineVersion)
    private var pageUnits: List<ReadingUnit> = emptyList()
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
        highlights?.let { store ->
            scope.launch {
                store.observe(bookId).collect { list ->
                    mutableState.update { it.copy(highlights = list, placed = placed(list)) }
                }
            }
        }
    }

    override fun showing(units: List<ReadingUnit>) {
        pageUnits = units
        mutableState.update { it.copy(placed = placed(it.highlights)) }
    }

    private fun placed(highlights: List<Highlight>) =
        PlacedHighlights(pageUnits.firstOrNull()?.page, placement.place(highlights, pageUnits))

    override fun openMenu(unit: ReadingUnit) = mutableState.update { it.copy(menuFor = unit) }

    override fun dismissMenu() = mutableState.update { it.copy(menuFor = null) }

    /** Bookmarks [unit] and opens its note, so a note can be written straight away (or the dialog closed). */
    override fun bookmark(unit: ReadingUnit) {
        mutableState.update { it.copy(menuFor = null) }
        val anchor = anchorOf(unit)
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

    /** Highlights [unit], or removes every highlight its band shows (a merged sentence can carry two). */
    override fun toggleHighlight(unit: ReadingUnit) {
        mutableState.update { it.copy(menuFor = null) }
        val store = highlights ?: return
        val existing = state.value.highlightsOn(unit)
        scope.launch {
            val saved = if (existing.isNotEmpty()) {
                existing.map { store.delete(it.id) }.firstOrNull { it is Outcome.Failure } ?: Outcome.Success(Unit)
            } else {
                store.add(Highlight(0, anchorOf(unit), unit.page, unit.charEnd, unit.text)).map { }
            }
            when (saved) {
                is Outcome.Success -> AudoraLog.i(
                    TAG,
                    "Highlight ${if (existing.isEmpty()) "added" else "removed (${existing.size})"}, " +
                        "$logContext page=${unit.page}",
                )

                is Outcome.Failure -> failed("highlight", saved.error)
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

    /** Where [unit] is, as a resume anchor: offsets and hashes, never its text. Voice notes anchor the same way. */
    fun anchorOf(unit: ReadingUnit) = ResumeAnchor(
        bookId = bookId,
        page = unit.page,
        charStart = unit.charStart,
        fingerprint = fingerprint.head(unit),
        tailFingerprint = fingerprint.tail(unit),
        pipelineVersion = pipelineVersion,
        savedAtMillis = clock(),
    )

    private fun failed(what: String, error: StorageError) {
        AudoraLog.w(TAG, "Could not save $what, $logContext: ${error.detail}")
        mutableState.update { it.copy(failed = true) }
    }

    private companion object {
        const val TAG = "Audora.Notes"
    }
}
