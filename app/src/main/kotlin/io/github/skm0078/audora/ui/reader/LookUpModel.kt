// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/LookUpModel.kt
// Purpose: The dictionary on tap (plan item 33): a sentence's words, the word picked and its entries.

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.dictionary.Dictionary
import io.github.skm0078.audora.core.dictionary.DictionaryEntry
import io.github.skm0078.audora.core.dictionary.WordKey
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.speech.ocr.OcrModelsState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * @property words the sentence's words, to tap one.
 * @property english the sentence is English; the dictionary is English only (ADR-045).
 * @property word the word picked or typed, while its entries show.
 * @property entries its entries; null while they are looked up.
 */
data class LookUpState(
    val words: List<String> = emptyList(),
    val english: Boolean = true,
    val word: String? = null,
    val entries: List<DictionaryEntry>? = null,
)

interface LookUpActions {
    fun open(unit: ReadingUnit) = Unit

    fun pick(word: String) = Unit

    fun back() = Unit

    fun close() = Unit

    fun download() = Unit

    fun cancelDownload() = Unit

    companion object {
        val None = object : LookUpActions {}
    }
}

/** What the reader needs: [state] null while closed, the dictionary's [download] state, and the actions. */
class LookUpBinding(val state: LookUpState?, val download: OcrModelsState?, val actions: LookUpActions) {
    val enabled: Boolean get() = this !== None

    companion object {
        val None = LookUpBinding(null, null, LookUpActions.None)
    }
}

/**
 * Plain state holder, one per open book. Words looked up are the book's text: never logged. [dictionary] opens the
 * downloaded dictionary (null while it is not installed); look-ups read the file on [io].
 */
class LookUpModel(
    private val dictionary: () -> Dictionary?,
    private val scope: CoroutineScope,
    private val onDownload: () -> Unit,
    private val onCancelDownload: () -> Unit,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : LookUpActions {
    private val mutableState = MutableStateFlow<LookUpState?>(null)
    val state: StateFlow<LookUpState?> = mutableState.asStateFlow()
    private var lookUp: Job? = null

    override fun open(unit: ReadingUnit) {
        mutableState.value = LookUpState(WordKey.wordsOf(unit.text), english = unit.lang == Lang.EN)
    }

    override fun pick(word: String) {
        mutableState.update { it?.copy(word = word, entries = null) }
        lookUp?.cancel()
        lookUp = scope.launch {
            val entries = withContext(io) { dictionary()?.lookUp(word).orEmpty() }
            mutableState.update { if (it?.word == word) it.copy(entries = entries) else it }
        }
    }

    override fun back() {
        lookUp?.cancel()
        mutableState.update { it?.copy(word = null, entries = null) }
    }

    override fun close() {
        lookUp?.cancel()
        mutableState.value = null
    }

    override fun download() = onDownload()

    override fun cancelDownload() = onCancelDownload()
}
