// File: core/src/main/kotlin/io/github/skm0078/audora/core/dictionary/Dictionary.kt
// Purpose: Looks a word up in the downloaded dictionary: the word, its listed forms and its base forms (item 33).

package io.github.skm0078.audora.core.dictionary

import io.github.skm0078.audora.core.config.DictionaryConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import java.io.IOException

enum class PartOfSpeech(val code: String) {
    NOUN("n"),
    VERB("v"),
    ADJECTIVE("a"),
    ADVERB("r"),
    ;

    companion object {
        fun of(code: String): PartOfSpeech = entries.firstOrNull { it.code == code } ?: NOUN
    }
}

data class DictionarySense(val partOfSpeech: PartOfSpeech, val definition: String, val synonyms: List<String>)

data class DictionaryEntry(val headword: String, val senses: List<DictionarySense>)

/**
 * Over the dictionary's sorted lines (see scripts/build_dictionary.py): `headword TAB pos|definition|syn,syn ...`, or
 * `form TAB =headword,...` for an inflected form the wordnet lists ("mice", "went"). A word not found as it is is
 * tried in its base forms ([EnglishBaseForms]: "leaves" as "leave" and "leaf"). Blocking file reads: call off the
 * main thread. Words looked up are never logged.
 */
class Dictionary(
    private val lines: SortedLines,
    private val baseForms: EnglishBaseForms = EnglishBaseForms(),
    private val config: DictionaryConfig = DictionaryConfig(),
) {
    /** Entries for [word] (any case, surrounding punctuation ignored), best first; empty when there are none. */
    fun lookUp(word: String): List<DictionaryEntry> {
        val key = WordKey.of(word, config.maxWordChars) ?: return emptyList()
        val found = LinkedHashMap<String, DictionaryEntry>()
        for (candidate in baseForms.of(key)) {
            entriesFor(candidate).forEach { found.putIfAbsent(it.headword, it) }
            if (found.size >= config.maxEntries) break
        }
        return found.values.take(config.maxEntries)
    }

    private fun entriesFor(key: String): List<DictionaryEntry> {
        val line = lineOf(key) ?: return emptyList()
        val fields = line.split('\t')
        val first = fields.getOrNull(1).orEmpty()
        return if (first.startsWith(REDIRECT)) {
            first.removePrefix(REDIRECT).split(',').mapNotNull { headword -> lineOf(headword)?.let(::entryOf) }
        } else {
            listOfNotNull(entryOf(line))
        }
    }

    private fun lineOf(key: String): String? = try {
        lines.find(key)
    } catch (e: IOException) {
        AudoraLog.w(TAG, "Dictionary unreadable: ${e.javaClass.simpleName}")
        null
    }

    private fun entryOf(line: String): DictionaryEntry? {
        val fields = line.split('\t')
        val senses = fields.drop(1).filterNot { it.startsWith(REDIRECT) }.mapNotNull(::senseOf)
        return if (senses.isEmpty()) null else DictionaryEntry(fields[0], senses)
    }

    private fun senseOf(field: String): DictionarySense? {
        val parts = field.split('|')
        if (parts.size != SENSE_PARTS) return null
        val synonyms = parts[2].split(',').filter { it.isNotBlank() }
        return DictionarySense(PartOfSpeech.of(parts[0]), parts[1], synonyms)
    }

    private companion object {
        const val TAG = "Audora.Dictionary"
        const val REDIRECT = "="
        const val SENSE_PARTS = 3
    }
}

/** A tapped word as the dictionary keys it. */
object WordKey {
    private val edges = Regex("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$")
    private val possessive = Regex("['’]s$")

    /** Lower case, without edge punctuation or a possessive "'s"; null when nothing is left or it is too long. */
    fun of(word: String, maxChars: Int): String? {
        val trimmed = word.trim().replace(edges, "").replace(possessive, "").replace('’', '\'').lowercase()
        return trimmed.takeIf { it.isNotEmpty() && it.length <= maxChars }
    }

    private val word = Regex("[\\p{L}\\p{M}][\\p{L}\\p{M}'’-]*")

    /** The words of [text] a listener may want to look up, in order, each once whatever its case. */
    fun wordsOf(text: String): List<String> = word.findAll(text)
        .map { it.value.trimEnd('\'', '’', '-') }
        .distinctBy { it.lowercase() }
        .toList()
}

/**
 * English base forms by WordNet's "morphy" suffix rules: the word itself first, then what plural, verb and comparative
 * endings come off to ("boxes" -> "box", "studied" -> "study", "running" -> "run", "bigger" -> "big"). Irregular forms
 * come from the dictionary's own form lines instead.
 */
class EnglishBaseForms {
    fun of(word: String): List<String> {
        val forms = LinkedHashSet<String>()
        forms += word
        for ((suffix, replacement) in RULES) {
            if (word.length > suffix.length + 1 && word.endsWith(suffix)) {
                val stem = word.dropLast(suffix.length) + replacement
                forms += stem
                if (replacement.isEmpty() && stem.isDoubled()) forms += stem.dropLast(1)
            }
        }
        return forms.toList()
    }

    private fun String.isDoubled(): Boolean =
        length >= MIN_DOUBLED && this[lastIndex] == this[lastIndex - 1] && this[lastIndex] !in "aeiouls"

    private companion object {
        const val MIN_DOUBLED = 3

        /** Noun, verb and adjective endings, longest first within each kind, as WordNet's morphy applies them. */
        val RULES = listOf(
            "ses" to "s", "xes" to "x", "zes" to "z", "ches" to "ch", "shes" to "sh", "men" to "man", "ies" to "y",
            "es" to "e", "es" to "", "s" to "",
            "ied" to "y", "ed" to "e", "ed" to "",
            "ing" to "e", "ing" to "",
            "est" to "e", "est" to "", "er" to "e", "er" to "",
        )
    }
}
