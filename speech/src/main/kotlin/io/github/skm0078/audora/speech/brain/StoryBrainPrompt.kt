// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/brain/StoryBrainPrompt.kt
// Purpose: What the story brain is asked about a passage (ADR 2026-10-25-story-brain): the prompt, and a grammar that lets it answer only
//          one line per spoken sentence, in order, and a cast line.

package io.github.skm0078.audora.speech.brain

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.speech.sherpa.SpeakerGender

/** One sentence of a passage. [spoken]: it has words said aloud (the quote tracking's call, not the brain's). */
data class BrainLine(val text: String, val spoken: Boolean, val paragraph: Int)

/**
 * One question to the model. [numbers]: the 1-based line numbers it answers for, in order (the spoken lines);
 * [maxTokens] caps its answer.
 */
data class BrainRequest(
    val prompt: String,
    val grammar: String,
    val numbers: List<Int>,
    val maxTokens: Int,
    val lang: Lang,
)

/**
 * Builds [BrainRequest]s. The template (`brain/prompt.txt`, a resource the eval script reads too, so the scores are
 * for this exact prompt) is in Qwen's chat format with thinking left empty: the answer starts at once.
 *
 * Only spoken lines are answered: narration needs no speaker, and its moods are the rules' (ADR-058). The grammar
 * names each spoken line's number, so the model can neither skip nor invent one, and bounds each name and the cast,
 * so an answer always ends. [knownCast]: speakers met earlier in the book, so a character keeps one name (and voice)
 * from page to page; [earlier]: the sentences just before the passage, for a conversation that runs on.
 */
object StoryBrainPrompt {
    /** Bump with any change to the prompt or the grammar: notes saved under another version are read again. */
    const val VERSION = 2

    /** Answer tokens per spoken line ("12|the old woman|surprised\n" is about 10) and for the cast line. */
    private const val TOKENS_PER_LINE = 16
    private const val CAST_TOKENS = 120
    private const val MAX_NAME_CHARS = 40
    private const val MAX_CAST = 12

    val FEELINGS = listOf("neutral", "joy", "sad", "angry", "scared", "surprised", "soft", "loud")

    private val template: String by lazy {
        requireNotNull(StoryBrainPrompt::class.java.getResourceAsStream("/brain/prompt.txt")) { "brain/prompt.txt" }
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    /** Null when no line is spoken: nothing to ask. */
    fun build(
        lang: Lang,
        lines: List<BrainLine>,
        earlier: List<String> = emptyList(),
        knownCast: Map<String, SpeakerGender> = emptyMap(),
    ): BrainRequest? {
        val numbers = lines.indices.filter { lines[it].spoken }.map { it + 1 }
        if (numbers.isEmpty()) return null
        val prompt = template
            .replace("{language}", LANGUAGE.getValue(lang))
            .replace("{context}", context(earlier, knownCast))
            .replace("{sentences}", numbered(lines))
        return BrainRequest(prompt, grammar(numbers), numbers, numbers.size * TOKENS_PER_LINE + CAST_TOKENS, lang)
    }

    private fun numbered(lines: List<BrainLine>): String = buildString {
        lines.forEachIndexed { i, line ->
            if (i > 0) append(if (line.paragraph != lines[i - 1].paragraph) "\n\n" else "\n")
            append(i + 1).append(if (line.spoken) "* " else ". ").append(oneLine(line.text))
        }
    }

    private fun context(earlier: List<String>, knownCast: Map<String, SpeakerGender>): String = buildString {
        val known = knownCast.entries.filter { it.value != SpeakerGender.UNKNOWN }
        if (known.isNotEmpty()) {
            append("\nCharacters met so far: ")
            append(
                known.joinToString(", ") { (name, gender) ->
                    "$name=${if (gender == SpeakerGender.FEMALE) "F" else "M"}"
                },
            )
            append("\n")
        }
        if (earlier.isNotEmpty()) {
            append("\nThe story just before (no answers needed):\n")
            append(earlier.joinToString("\n") { oneLine(it) })
            append("\n")
        }
    }

    /** GBNF: each spoken line's number in order, a name, a feeling; then the cast. */
    private fun grammar(numbers: List<Int>): String = buildString {
        append("root ::= ")
        append(numbers.joinToString(" ") { "\"$it|\" who \"|\" how \"\\n\"" })
        append(" cast\n")
        append("who ::= [^|\\n]{1,$MAX_NAME_CHARS}\n")
        append("how ::= ").append(FEELINGS.joinToString(" | ") { "\"$it\"" }).append("\n")
        // No more pairs than spoken lines: past the real speakers a small model fills the list with nouns.
        val pairs = minOf(numbers.size, MAX_CAST)
        append("cast ::= \"cast: \" pair (\", \" pair){0,${pairs - 1}} \"\\n\"\n")
        append("pair ::= [^|\\n=,]{1,$MAX_NAME_CHARS} \"=\" [FM]\n")
    }

    private fun oneLine(text: String): String = text.replace(NEWLINES, " ").trim()

    private val NEWLINES = Regex("""\s*\n\s*""")
    private val LANGUAGE = mapOf(Lang.EN to "English", Lang.HI to "Hindi", Lang.BN to "Bengali")
}
