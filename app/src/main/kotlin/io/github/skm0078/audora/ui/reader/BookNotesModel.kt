// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/BookNotesModel.kt
// Purpose: The reader's bookmarks with notes (plan item 29), pronunciation fixes (item 1) and the sentence menu.

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.config.HighlightConfig
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
 * @property menuOnShare the menu opened straight on its Share choices (the reader's Share button).
 * @property rangeFrom where a highlight over several sentences starts, while the listener picks where it ends.
 * @property rangeForShare the passage being picked is to share ("Choose more lines"), not to highlight.
 * @property rangeRefused why the last such highlight was not made, shown once.
 * @property sharing lines picked with "Choose more lines", while their Share choices show.
 * @property quickBookmark the bookmark the player's one-tap button just saved (CF-2), while its notice offers Add note
 *   and Undo.
 */
data class NotesState(
    val bookmarks: List<Bookmark> = emptyList(),
    val rules: List<PronunciationRule> = emptyList(),
    val menuFor: ReadingUnit? = null,
    val menuOnShare: Boolean = false,
    val fixing: ReadingUnit? = null,
    val editing: Bookmark? = null,
    val listOpen: Boolean = false,
    val failed: Boolean = false,
    val highlights: List<Highlight> = emptyList(),
    val placed: PlacedHighlights = PlacedHighlights(),
    val rangeFrom: ReadingUnit? = null,
    val rangeForShare: Boolean = false,
    val rangeRefused: RangeRefusal? = null,
    val quickBookmark: Bookmark? = null,
    val sharing: SharedPassage? = null,
) {
    /** [unit] already has a bookmark: the player shows its bookmark button filled. */
    fun isBookmarked(unit: ReadingUnit): Boolean = bookmarks.any { it.marks(unit) }

    /** The highlights covering [unit] on the page showing (plan item 30); two when sentences were merged. */
    fun highlightsOn(unit: ReadingUnit): List<Highlight> =
        placed.byIndex[unit.index]?.takeIf { placed.page == unit.page }.orEmpty()

    /** [unit] shows as highlighted: a highlight covers it, or a passage being picked starts at it. */
    fun showsHighlight(unit: ReadingUnit): Boolean = highlightsOn(unit).isNotEmpty() || isRangeStart(unit)

    /** [unit] is where the highlight being picked starts: it shows as highlighted until the end is picked. */
    fun isRangeStart(unit: ReadingUnit): Boolean =
        rangeFrom?.let { it.page == unit.page && it.index == unit.index } == true
}

/** Why a highlight over several sentences was not made. */
enum class RangeRefusal {
    /** It would cover more than [HighlightConfig.maxPassagePages] pages. */
    TOO_LONG,

    /** A page in it could not be read now (a scanned page not read yet, or a page that failed). */
    PAGE_UNREADABLE,

    /** Lines to share would cover more than [HighlightConfig.maxPassagePages] pages. */
    SHARE_TOO_LONG,
}

/** Lines picked to share, from the start of [first] to the end of [last]; [quote] their words, reading order. */
data class SharedPassage(val first: ReadingUnit, val last: ReadingUnit, val quote: String)

/**
 * The showing page's highlights by the [ReadingUnit.index] of each sentence they cover, found again after a pipeline
 * change merged, split or moved sentences ([HighlightPlacement]). [page] is null before a page shows.
 */
data class PlacedHighlights(val page: Int? = null, val byIndex: Map<Int, List<Highlight>> = emptyMap())

/** What the screen calls; [BookNotesModel] implements it, [None] stands in where notes are not wired (previews). */
@Suppress("TooManyFunctions") // One per control in the sentence menu, the bookmarks sheet and the fix dialog.
interface BookNotesActions {
    fun openMenu(unit: ReadingUnit) = Unit

    /** The reader's Share button: [unit]'s menu, opened on its Share choices. */
    fun openShare(unit: ReadingUnit) = Unit

    fun dismissMenu() = Unit

    fun bookmark(unit: ReadingUnit) = Unit

    /** The player's one-tap bookmark (CF-2): saves [unit] without opening its note, and offers Add note and Undo. */
    fun quickBookmark(unit: ReadingUnit) = Unit

    /** "Add note" on the one-tap bookmark's notice: its note opens. */
    fun noteQuickBookmark() = Unit

    /** "Undo" on the one-tap bookmark's notice: the bookmark is deleted again. */
    fun undoQuickBookmark() = Unit

    /** The notice timed out or was swiped: the bookmark stays. */
    fun dismissQuickBookmark() = Unit

    /** Highlights [unit], or removes its highlight when it has one (plan item 30). */
    fun toggleHighlight(unit: ReadingUnit) = Unit

    /** "Highlight from here": [unit] starts a highlight; the next sentence tapped ends it ([endRange]). */
    fun startRange(unit: ReadingUnit) = Unit

    /** Highlights from the start picked to [unit], on any page after it or before it. */
    fun endRange(unit: ReadingUnit) = Unit

    /** "Choose more lines": [unit] starts lines to share; the next sentence tapped ends them ([endRange]). */
    fun startShareRange(unit: ReadingUnit) = Unit

    fun cancelRange() = Unit

    /** The Share choices for picked lines closed, shared or not. */
    fun dismissSharing() = Unit

    fun dismissRangeRefusal() = Unit

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
 *
 * [unitsOf] reads another page's sentences for a highlight over several pages (null when the page cannot be read now);
 * it must not run OCR, which is slow on a low-end phone: the pages turned through on the way are read already.
 */
@Suppress("TooManyFunctions") // The actions, each a one-liner or a store write.
class BookNotesModel(
    private val bookId: BookId,
    stores: NoteStores,
    private val scope: CoroutineScope,
    private val pipelineVersion: Int,
    private val unitsOf: suspend (Int) -> List<ReadingUnit>? = { null },
    private val clock: () -> Long = System::currentTimeMillis,
) : BookNotesActions {
    private val bookmarks = stores.bookmarks
    private val pronunciations = stores.pronunciations
    private val highlights = stores.highlights
    private val fingerprint = SentenceFingerprint()
    private val placement = HighlightPlacement(pipelineVersion)
    private val config = HighlightConfig()
    private var pageUnits: List<ReadingUnit> = emptyList()
    private val mutableState = MutableStateFlow(NotesState())
    val state: StateFlow<NotesState> = mutableState.asStateFlow()
    private val logContext = "book=${bookId.logLabel}"
    private var started = false

    /** A one-tap bookmark is being written: a second tap meanwhile must not save the sentence twice. */
    private var quickSaving = false

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

    override fun openMenu(unit: ReadingUnit) = mutableState.update { it.copy(menuFor = unit, menuOnShare = false) }

    override fun openShare(unit: ReadingUnit) = mutableState.update { it.copy(menuFor = unit, menuOnShare = true) }

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

    /**
     * Saves [unit] with no note and shows the notice; a sentence that already has a bookmark opens its note instead,
     * so a second tap never makes a twin.
     */
    override fun quickBookmark(unit: ReadingUnit) {
        state.value.bookmarks.firstOrNull { it.marks(unit) }?.let { existing ->
            editNote(existing)
            return
        }
        if (quickSaving) return
        quickSaving = true
        val anchor = anchorOf(unit)
        scope.launch {
            when (val added = bookmarks.add(Bookmark(0, anchor))) {
                is Outcome.Success -> {
                    AudoraLog.i(TAG, "Player bookmark added, $logContext page=${unit.page} id=${added.value}")
                    mutableState.update { it.copy(quickBookmark = Bookmark(added.value, anchor)) }
                }

                is Outcome.Failure -> failed("bookmark", added.error)
            }
            quickSaving = false
        }
    }

    override fun noteQuickBookmark() {
        val saved = state.value.quickBookmark ?: return
        mutableState.update { it.copy(quickBookmark = null, editing = saved) }
    }

    override fun undoQuickBookmark() {
        val saved = state.value.quickBookmark ?: return
        mutableState.update { it.copy(quickBookmark = null) }
        delete(saved)
    }

    override fun dismissQuickBookmark() = mutableState.update { it.copy(quickBookmark = null) }

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

    override fun startRange(unit: ReadingUnit) =
        mutableState.update { it.copy(menuFor = null, rangeFrom = unit, rangeForShare = false) }

    override fun startShareRange(unit: ReadingUnit) =
        mutableState.update { it.copy(menuFor = null, rangeFrom = unit, rangeForShare = true) }

    override fun cancelRange() = mutableState.update { it.copy(rangeFrom = null) }

    override fun dismissSharing() = mutableState.update { it.copy(sharing = null) }

    override fun dismissRangeRefusal() = mutableState.update { it.copy(rangeRefused = null) }

    /**
     * One highlight from the earlier of the two sentences to the later, its quote their words (sentences without any,
     * "* * *", are left out of it); for "Choose more lines", the same lines' Share choices instead. Pages between are
     * read through [unitsOf]; a passage longer than [HighlightConfig.maxPassagePages] pages, or one over a page that
     * cannot be read now, is refused and said so.
     */
    override fun endRange(unit: ReadingUnit) {
        val picking = state.value
        val from = picking.rangeFrom ?: return
        mutableState.update { it.copy(rangeFrom = null) }
        val store = highlights
        val (first, last) = if (unit.isBefore(from)) unit to from else from to unit
        val tooLong = last.page - first.page >= config.maxPassagePages
        when {
            picking.rangeForShare && tooLong -> refuse(RangeRefusal.SHARE_TOO_LONG)
            picking.rangeForShare -> sharePassage(first, last)
            store == null -> Unit
            tooLong -> refuse(RangeRefusal.TOO_LONG)
            else -> highlightPassage(store, first, last)
        }
    }

    private fun sharePassage(first: ReadingUnit, last: ReadingUnit) {
        scope.launch {
            val passage = passage(first, last) ?: return@launch refuse(RangeRefusal.PAGE_UNREADABLE)
            val quote = passage.filter { it.hasWords() }.joinToString(" ") { it.text.trim() }
            if (quote.isEmpty()) return@launch
            AudoraLog.i(TAG, "Lines picked to share, $logContext pages=${first.page}-${last.page}")
            mutableState.update { it.copy(sharing = SharedPassage(first, last, quote)) }
        }
    }

    private fun highlightPassage(store: HighlightStore, first: ReadingUnit, last: ReadingUnit) {
        scope.launch {
            val passage = passage(first, last) ?: return@launch refuse(RangeRefusal.PAGE_UNREADABLE)
            val quote = passage.filter { it.hasWords() }.joinToString(" ") { it.text.trim() }
            if (quote.isEmpty()) return@launch
            val added = store.add(Highlight(0, anchorOf(first), last.page, last.charEnd, quote))
            when (added) {
                is Outcome.Success -> AudoraLog.i(
                    TAG,
                    "Passage highlighted, $logContext pages=${first.page}-${last.page} sentences=${passage.size}",
                )

                is Outcome.Failure -> failed("highlight", added.error)
            }
        }
    }

    /** The units from [first] to [last], reading order; null when a page of it cannot be read now. */
    private suspend fun passage(first: ReadingUnit, last: ReadingUnit): List<ReadingUnit>? {
        val showing = pageUnits
        val pages = (first.page..last.page).map { page ->
            (showing.takeIf { it.firstOrNull()?.page == page } ?: unitsOf(page)) ?: return null
        }
        return pages.flatten().filter { unit ->
            !unit.isBefore(first) && !last.isBefore(unit)
        }
    }

    private fun refuse(why: RangeRefusal) {
        AudoraLog.i(TAG, "Passage highlight refused ($why), $logContext")
        mutableState.update { it.copy(rangeRefused = why) }
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

        /** Reading order: an earlier page, or the same page and an earlier sentence. */
        fun ReadingUnit.isBefore(other: ReadingUnit): Boolean =
            page < other.page || (page == other.page && index < other.index)

        /** A sentence a highlight can mark: one with a letter or digit, not "* * *". */
        fun ReadingUnit.hasWords(): Boolean = text.any(Char::isLetterOrDigit)
    }
}

/**
 * This bookmark is on [unit]: same page and first character. A bookmark saved by an older reading pipeline whose
 * offsets moved is not matched; it is still found again from the bookmarks list.
 */
internal fun Bookmark.marks(unit: ReadingUnit): Boolean = anchor.page == unit.page && anchor.charStart == unit.charStart
