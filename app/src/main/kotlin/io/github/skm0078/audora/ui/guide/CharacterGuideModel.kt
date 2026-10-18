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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** [view] at [page] (0-based, the book's resume point); null while loading. */
data class GuideState(val page: Int, val view: GuideView)

/** The guide screen's state and actions; each change is saved at once. */
class CharacterGuideModel(
    private val bookId: BookId,
    private val store: CharacterGuideStore,
    private val anchors: ResumeAnchorStore,
    private val scope: CoroutineScope,
    private val guide: CharacterGuide = CharacterGuide(),
) {
    private var entries: List<GuideEntry> = emptyList()
    private val mutableState = MutableStateFlow<GuideState?>(null)

    val state: StateFlow<GuideState?> = mutableState.asStateFlow()

    fun start() {
        scope.launch {
            val (saved, page) = withContext(Dispatchers.IO) {
                store.load(bookId.value) to (anchors.get(bookId)?.page ?: 0)
            }
            entries = saved
            publish(page)
            AudoraLog.i(TAG, "Character guide opened, book=${bookId.logLabel}, ${saved.size} entries, page $page")
        }
    }

    /** Saves a new ([GuideEntry.id] 0) or edited entry; false when refused (a blank name, or the guide is full). */
    fun save(entry: GuideEntry): Boolean {
        val next = guide.saved(entries, entry) ?: return false
        commit(next)
        return true
    }

    fun delete(id: Long) = commit(guide.removed(entries, id))

    private fun commit(next: List<GuideEntry>) {
        entries = next
        publish(mutableState.value?.page ?: 0)
        scope.launch { withContext(Dispatchers.IO) { store.save(bookId.value, next) } }
    }

    private fun publish(page: Int) {
        mutableState.value = GuideState(page, guide.viewAt(entries, page))
    }

    private companion object {
        const val TAG = "Guide"
    }
}
