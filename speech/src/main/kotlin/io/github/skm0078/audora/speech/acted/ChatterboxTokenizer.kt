// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/acted/ChatterboxTokenizer.kt
// Purpose: Chatterbox Multilingual's text tokenizer, ported from its Hugging Face tokenizer.json (ADR-059).

package io.github.skm0078.audora.speech.acted

import java.io.Reader
import java.text.Normalizer
import java.util.Locale
import java.util.regex.Pattern

/**
 * What Hugging Face `tokenizers` does with this tokenizer.json, step for step, so the ids match the original
 * (ChatterboxTokenizerTest checks them against ids the original produced):
 * 1. Chatterbox's own preparation: lower case, NFKD, then the language tag (`[hi]`).
 * 2. Added tokens matched on the raw text (leftmost, longest), the tag among them.
 * 3. The normalizer: each space becomes `[SPACE]`, an added token matched after normalization.
 * 4. The whitespace pre-tokenizer: runs of word characters, or of other non-space characters.
 * 5. BPE on each such word, merges applied by rank; a character outside the vocabulary is `[UNK]`.
 * 6. The template: the exaggeration slot and the start token before, the stop token and two start-of-speech
 *    tokens after.
 *
 * Built from `assets/acted/tokenizer.tsv` (scripts/acted_tokenizer.py). Immutable, thread-safe.
 */
class ChatterboxTokenizer private constructor(private val table: Table) {
    /** The table's contents; [rawAdded] and [normalizedAdded] longest first. */
    private class Table(
        val template: Template,
        val unknown: Long,
        val rawAdded: List<Pair<String, Long>>,
        val normalizedAdded: List<Pair<String, Long>>,
        val vocab: Map<String, Long>,
        val ranks: Map<Pair<String, String>, Int>,
    )

    /** The ids the template puts before and after the text. */
    private class Template(val before: LongArray, val after: LongArray)

    /** The ids the model reads for [text] in Hindi. */
    fun encode(text: String): LongArray {
        val prepared = LANGUAGE_TAG + Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFKD)
        val ids = ArrayList<Long>(prepared.length + table.template.before.size + table.template.after.size)
        table.template.before.forEach(ids::add)
        for (raw in split(prepared, table.rawAdded)) {
            if (raw.id != null) ids.add(raw.id) else normalizeAndEncode(raw.text, ids)
        }
        table.template.after.forEach(ids::add)
        return ids.toLongArray()
    }

    /** Text between raw added tokens: spaces become [SPACE], the rest goes word by word through BPE. */
    private fun normalizeAndEncode(text: String, ids: MutableList<Long>) {
        for (normalized in split(text.replace(" ", SPACE), table.normalizedAdded)) {
            if (normalized.id != null) {
                ids.add(normalized.id)
            } else {
                WORDS.findAll(normalized.text).forEach { word -> bpe(word.value, ids) }
            }
        }
    }

    private class Segment(val text: String, val id: Long?)

    /** [text] cut around every [tokens] match, leftmost first and the longest where several start at one place. */
    private fun split(text: String, tokens: List<Pair<String, Long>>): List<Segment> {
        val out = ArrayList<Segment>()
        var plainFrom = 0
        var at = 0
        while (at < text.length) {
            val match = tokens.firstOrNull { (content, _) -> text.startsWith(content, at) }
            if (match == null) {
                at++
                continue
            }
            if (at > plainFrom) out.add(Segment(text.substring(plainFrom, at), null))
            out.add(Segment(match.first, match.second))
            at += match.first.length
            plainFrom = at
        }
        if (plainFrom < text.length) out.add(Segment(text.substring(plainFrom), null))
        return out
    }

    private fun bpe(word: String, out: MutableList<Long>) {
        val parts = ArrayList<String>()
        var i = 0
        while (i < word.length) {
            val next = word.offsetByCodePoints(i, 1)
            parts.add(word.substring(i, next))
            i = next
        }
        while (parts.size > 1) {
            var best = -1
            var bestRank = Int.MAX_VALUE
            for (j in 0 until parts.size - 1) {
                val rank = table.ranks[parts[j] to parts[j + 1]] ?: continue
                if (rank < bestRank) {
                    bestRank = rank
                    best = j
                }
            }
            if (best < 0) break
            parts[best] = parts[best] + parts[best + 1]
            parts.removeAt(best + 1)
        }
        parts.forEach { out.add(table.vocab[it] ?: table.unknown) }
    }

    companion object {
        const val ASSET = "acted/tokenizer.tsv"
        private const val LANGUAGE_TAG = "[hi]"
        private const val SPACE = "[SPACE]"
        private const val FIELDS = 4
        private const val CONTENT = 3 // an added token's content: "added", id, normalized, content

        /** `tokenizers`' Whitespace pre-tokenizer: `\w+|[^\w\s]+`, with Unicode word characters (marks included). */
        private val WORDS = Pattern.compile("\\w+|[^\\w\\s]+", Pattern.UNICODE_CHARACTER_CLASS).toRegex()

        /** Reads the table scripts/acted_tokenizer.py writes. Throws [IllegalArgumentException] if malformed. */
        fun read(reader: Reader): ChatterboxTokenizer {
            var before = LongArray(0)
            var after = LongArray(0)
            var unknown: Long? = null
            val raw = ArrayList<Pair<String, Long>>()
            val normalized = ArrayList<Pair<String, Long>>()
            val vocab = HashMap<String, Long>()
            val ranks = HashMap<Pair<String, String>, Int>()
            reader.buffered().useLines { lines ->
                lines.filter { it.isNotEmpty() && !it.startsWith("#") }.forEach { line ->
                    val fields = line.split('\t') + List(FIELDS) { "" }
                    val (kind, first, second) = fields
                    when (kind) {
                        "template" -> {
                            before = ids(first)
                            after = ids(second)
                        }

                        "unknown" -> unknown = first.toLong()

                        "added" -> (if (second == "1") normalized else raw).add(fields[CONTENT] to first.toLong())

                        "vocab" -> vocab[second] = first.toLong()

                        "merge" -> ranks.putIfAbsent(first to second, ranks.size)

                        else -> throw IllegalArgumentException("unknown tokenizer line: $kind")
                    }
                }
            }
            require(vocab.isNotEmpty() && before.isNotEmpty() && after.isNotEmpty()) { "incomplete tokenizer table" }
            // Longest first, so a match at one place takes the longest token there.
            val table = Table(
                Template(before, after),
                requireNotNull(unknown) { "tokenizer table has no unknown token" },
                raw.sortedByDescending { it.first.length },
                normalized.sortedByDescending { it.first.length },
                vocab,
                ranks,
            )
            return ChatterboxTokenizer(table)
        }

        private fun ids(field: String): LongArray =
            field.split(',').filter { it.isNotEmpty() }.map { it.toLong() }.toLongArray()
    }
}
