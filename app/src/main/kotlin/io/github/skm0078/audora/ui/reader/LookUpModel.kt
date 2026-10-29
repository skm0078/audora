// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/LookUpModel.kt
// Purpose: The dictionary on tap (item 33, ADR-047): a sentence's words, the word picked and its entries.

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.dictionary.Dictionary
import io.github.skm0078.audora.core.dictionary.DictionaryEntry
import io.github.skm0078.audora.core.dictionary.DictionaryLanguage
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
 * @property lang the sentence's language: its dictionary is offered first.
 * @property word the word picked or typed, while its entries show.
 * @property entries its entries; null while they are looked up.
 * @property missing the word's dictionary was not installed when it was looked up.
 */
data class LookUpState(
    val words: List<String> = emptyList(),
    val lang: Lang = Lang.EN,
    val word: String? = null,
    val entries: List<DictionaryEntry>? = null,
    val missing: Boolean = false,
) {
    /** The dictionary in use: the picked word's, by its script, else the sentence's. */
    val dictionaryLang: Lang get() = word?.let(DictionaryLanguage::of) ?: lang
}

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

/** What the reader needs: [state] null while closed, [download] of the dictionary in use, and the actions. */
class LookUpBinding(val state: LookUpState?, val download: OcrModelsState?, val actions: LookUpActions) {
    val enabled: Boolean get() = this !== None

    companion object {
        val None = LookUpBinding(null, null, LookUpActions.None)
    }
}

/**
 * Plain state holder, one per open book. Words looked up are the book's text: never logged. [dictionary] opens a
 * language's downloaded dictionary (null while it is not installed); look-ups read the file on [io]. Downloads act on
 * the dictionary in use ([LookUpState.dictionaryLang]). A word found is handed to [onFound] with its first meaning
 * and the sentence it was picked from ("Words you looked up"), off the main thread.
 */
class LookUpModel(
    private val dictionary: (Lang) -> Dictionary?,
    private val scope: CoroutineScope,
    private val onDownload: (Lang) -> Unit,
    private val onCancelDownload: (Lang) -> Unit,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val onFound: (word: String, meaning: String, sentence: ReadingUnit) -> Unit = { _, _, _ -> },
) : LookUpActions {
    private val mutableState = MutableStateFlow<LookUpState?>(null)
    val state: StateFlow<LookUpState?> = mutableState.asStateFlow()
    private var lookUp: Job? = null
    private var sentence: ReadingUnit? = null

    override fun open(unit: ReadingUnit) {
        sentence = unit
        mutableState.value = LookUpState(WordKey.wordsOf(unit.text), lang = unit.lang)
    }

    /** Looks [word] up in its script's dictionary; called again once a missing dictionary is installed. */
    override fun pick(word: String) {
        mutableState.update { it?.copy(word = word, entries = null, missing = false) }
        lookUp?.cancel()
        lookUp = scope.launch {
            val opened = withContext(io) { dictionary(DictionaryLanguage.of(word)) }
            val entries = withContext(io) { opened?.lookUp(word).orEmpty() }
            val meaning = entries.firstNotNullOfOrNull { entry -> entry.senses.firstOrNull()?.definition }
            val from = sentence
            if (meaning != null && from != null) withContext(io) { onFound(word, meaning, from) }
            mutableState.update { if (it?.word == word) it.copy(entries = entries, missing = opened == null) else it }
        }
    }

    override fun back() {
        lookUp?.cancel()
        mutableState.update { it?.copy(word = null, entries = null) }
    }

    override fun close() {
        lookUp?.cancel()
        sentence = null
        mutableState.value = null
    }

    override fun download() = onDownload(currentLang())

    override fun cancelDownload() = onCancelDownload(currentLang())

    private fun currentLang(): Lang = mutableState.value?.dictionaryLang ?: Lang.EN
}
