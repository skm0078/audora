// File: app/src/main/kotlin/io/github/skm0078/audora/ui/guide/CharacterGuideModel.kt
// Purpose: Loads, edits and saves one book's character guide, shown up to the listener's place (plan item 80).

package io.github.skm0078.audora.ui.guide

import io.github.skm0078.audora.core.guide.CharacterGuide
import io.github.skm0078.audora.core.guide.CharacterGuideStore
import io.github.skm0078.audora.core.guide.GuideEntry
import io.github.skm0078.audora.core.guide.GuideView
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.resume.ResumeAnchorStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * [view] at the listener's place ([page], 0-based, and [charStart], from the book's resume point); [undoable] is the
 * entry just deleted, until the next change.
 */
data class GuideState(val page: Int, val charStart: Int, val view: GuideView, val undoable: GuideEntry? = null)

/**
 * The guide screen's state and actions; each change is saved at once, through one writer shared by every guide
 * screen, so saves land in order and the latest is the one kept.
 */
class CharacterGuideModel(
    private val bookId: BookId,
    private val store: CharacterGuideStore,
    private val anchors: ResumeAnchorStore,
    private val scope: CoroutineScope,
    private val guide: CharacterGuide = CharacterGuide(),
    private val io: CoroutineDispatcher = GUIDE_SAVES,
) {
    private var entries: List<GuideEntry> = emptyList()
    private val mutableState = MutableStateFlow<GuideState?>(null)

    val state: StateFlow<GuideState?> = mutableState.asStateFlow()

    /** Every entry, hidden ones too, so an editor reopened after a rotation finds its entry. */
    fun entry(id: Long): GuideEntry? = entries.firstOrNull { it.id == id }

    fun start() {
        scope.launch {
            val (saved, anchor) = withContext(io) { store.load(bookId.value) to anchors.get(bookId) }
            entries = saved
            val page = anchor?.page ?: 0
            val charStart = anchor?.charStart ?: 0
            mutableState.value = GuideState(page, charStart, guide.viewAt(saved, page, charStart))
            AudoraLog.i(TAG, "Character guide opened, book=${bookId.logLabel}, ${saved.size} entries, page $page")
        }
    }

    /**
     * Saves a new ([GuideEntry.id] 0) or edited entry; false when refused (a blank name, or the guide is full). A
     * new entry is met at the listener's place; an edited one keeps its place unless its page was changed.
     */
    fun save(entry: GuideEntry): Boolean {
        val current = mutableState.value
        val next = current?.let { guide.saved(entries, placed(entry, it)) }
        if (next != null) commit(next, undoable = null)
        return next != null
    }

    private fun placed(entry: GuideEntry, current: GuideState): GuideEntry {
        val before = entry(entry.id)
        return when {
            before == null -> entry.copy(charStart = if (entry.page == current.page) current.charStart else 0)
            before.page == entry.page -> entry.copy(charStart = before.charStart)
            else -> entry.copy(charStart = 0)
        }
    }

    fun delete(id: Long) = commit(guide.removed(entries, id), undoable = entry(id))

    /** Puts back the entry just deleted. */
    fun undoDelete() {
        val deleted = mutableState.value?.undoable ?: return
        commit(entries.filterNot { it.id == deleted.id } + deleted, undoable = null)
    }

    private fun commit(next: List<GuideEntry>, undoable: GuideEntry?) {
        entries = next
        mutableState.value?.let { current ->
            mutableState.value = current.copy(
                view = guide.viewAt(next, current.page, current.charStart),
                undoable = undoable,
            )
        }
        scope.launch(io) { store.save(bookId.value, next) }
    }

    private companion object {
        const val TAG = "Guide"
    }
}

/** One writer for the guide files across screens, so saves run in order and the latest is the one kept. */
private val GUIDE_SAVES = Dispatchers.IO.limitedParallelism(1)
