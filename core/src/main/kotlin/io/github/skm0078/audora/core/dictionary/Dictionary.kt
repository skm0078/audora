// File: core/src/main/kotlin/io/github/skm0078/audora/core/dictionary/Dictionary.kt
// Purpose: Looks a word up in a downloaded dictionary: the word, its listed forms and its base forms (33, ADR-047).

package io.github.skm0078.audora.core.dictionary

import io.github.skm0078.audora.core.config.DictionaryConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.Lang
import java.io.IOException
import java.text.Normalizer

/** The parts of speech the dictionaries label; Wiktionary's others (particles, phrases) read as [OTHER]. */
enum class PartOfSpeech(val code: String) {
    NOUN("n"),
    VERB("v"),
    ADJECTIVE("a"),
    ADVERB("r"),
    PRONOUN("p"),
    POSTPOSITION("o"),
    CONJUNCTION("c"),
    INTERJECTION("i"),
    NUMBER("u"),
    OTHER("x"),
    ;

    companion object {
        fun of(code: String): PartOfSpeech = entries.firstOrNull { it.code == code } ?: OTHER
    }
}

data class DictionarySense(val partOfSpeech: PartOfSpeech, val definition: String, val synonyms: List<String>)

data class DictionaryEntry(val headword: String, val senses: List<DictionarySense>)

/** The forms a word is tried in, the word itself first. */
fun interface BaseForms {
    fun of(word: String): List<String>

    companion object {
        /** Only the word: for dictionaries whose own form lines list the inflections (Hindi, Bengali). */
        val AsWritten = BaseForms { listOf(it) }
    }
}

/**
 * Over the dictionary's sorted lines (see scripts/build_dictionary.py): `headword TAB pos|definition|syn,syn ...`, or
 * `form TAB =headword,...` for an inflected form the source lists ("mice", "went"); a word that has senses and is
 * also a form of others ends with that `=` field. A word not found as it is is tried in its [baseForms]
 * ([EnglishBaseForms]: "leaves" as "leave" and "leaf"). Blocking file reads: call off the main thread. Words looked
 * up are never logged.
 */
class Dictionary(
    private val lines: SortedLines,
    private val baseForms: BaseForms = EnglishBaseForms(),
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
        val redirects = line.split('\t').drop(1).filter { it.startsWith(REDIRECT) }
            .flatMap { it.removePrefix(REDIRECT).split(',') }
            .filter { it.isNotEmpty() && it != key }
        return listOfNotNull(entryOf(line)) + redirects.mapNotNull { headword -> lineOf(headword)?.let(::entryOf) }
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
    // Edges keep combining marks: a Hindi or Bengali word can end in one (a vowel sign, virama or nukta).
    private val edges = Regex("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{M}\\p{N}]+$")
    private val possessive = Regex("['’]s$")
    private val joiners = Regex("[\\u200C\\u200D]")

    /**
     * NFC, without zero-width joiners (they change only how a word is drawn), edge punctuation or a possessive "'s",
     * lower case; null when nothing is left or it is too long. The build script keys headwords the same way.
     */
    fun of(word: String, maxChars: Int): String? {
        val normal = Normalizer.normalize(word.trim(), Normalizer.Form.NFC).replace(joiners, "")
        val trimmed = normal.replace(edges, "").replace(possessive, "").replace('’', '\'').lowercase()
        return trimmed.takeIf { it.isNotEmpty() && it.length <= maxChars }
    }

    private val word = Regex("[\\p{L}\\p{M}][\\p{L}\\p{M}'’-]*")

    /** The words of [text] a listener may want to look up, in order, each once whatever its case. */
    fun wordsOf(text: String): List<String> = word.findAll(text)
        .map { it.value.trimEnd('\'', '’', '-') }
        .distinctBy { it.lowercase() }
        .toList()
}

/** Which language's dictionary a word belongs in, by its script: Devanagari is Hindi, Bengali script Bengali. */
object DictionaryLanguage {
    private const val DEVANAGARI_FIRST = 0x0900
    private const val DEVANAGARI_LAST = 0x097F
    private const val BENGALI_FIRST = 0x0980
    private const val BENGALI_LAST = 0x09FF

    /** The script of [word]'s first letter decides; a word with no Indic letter is English. */
    fun of(word: String): Lang {
        val letter = word.firstOrNull { Character.isLetter(it) } ?: return Lang.EN
        return when (letter.code) {
            in DEVANAGARI_FIRST..DEVANAGARI_LAST -> Lang.HI
            in BENGALI_FIRST..BENGALI_LAST -> Lang.BN
            else -> Lang.EN
        }
    }
}

/**
 * English base forms by WordNet's "morphy" suffix rules: the word itself first, then what plural, verb and comparative
 * endings come off to ("boxes" -> "box", "studied" -> "study", "running" -> "run", "bigger" -> "big"). Irregular forms
 * come from the dictionary's own form lines instead.
 */
class EnglishBaseForms : BaseForms {
    override fun of(word: String): List<String> {
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
