// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/brain/StoryBrainAnswer.kt
// Purpose: Reads the story brain's answer (ADR-078) into who speaks each spoken line, with a gender, and how.

package io.github.skm0078.audora.speech.brain

import io.github.skm0078.audora.core.model.Feeling
import io.github.skm0078.audora.speech.route.NameGenders
import io.github.skm0078.audora.speech.route.Speaker
import io.github.skm0078.audora.speech.sherpa.SpeakerGender

/** The brain's call on one spoken line: who says it, and the feeling it is said with (null: neutral). */
data class BrainNote(val speaker: Speaker, val feeling: Feeling?)

/**
 * Pure. The grammar already fixes the shape, so a line that still does not read (an answer cut at its token cap) is
 * left out, and that line keeps the rules' call. A speaker's gender comes from the name lists ([NameGenders]) when
 * they know the name or role, else from the answer's cast (its first call on the name), else from the cast met earlier
 * ([knownCast]), matched by the whole name or a word of it ("Mr Hale" and "Hale"); else it is unknown, and the
 * character voices pick as they do for a name the rules found. Only speakers enter the cast carried on: a small model
 * pads its cast line with nouns.
 */
object StoryBrainAnswer {
    /** Notes by 1-based line number, and the cast with what this answer added. */
    data class Parsed(val notes: Map<Int, BrainNote>, val cast: Map<String, SpeakerGender>)

    fun parse(answer: String, request: BrainRequest, knownCast: Map<String, SpeakerGender> = emptyMap()): Parsed {
        val rows = answer.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val heard = castOf(rows)
        val wanted = request.numbers.toSet()
        val lines = rows.mapNotNull { row -> line(row, wanted) }
        val cast = LinkedHashMap(knownCast)
        lines.forEach { (_, name, _) ->
            // The name lists know a role's or a common name's gender better than a small model does.
            val gender = NameGenders.of(name, request.lang).takeIf { it != SpeakerGender.UNKNOWN }
                ?: genderOf(name, heard).takeIf { it != SpeakerGender.UNKNOWN }
            if (gender != null) cast[name] = gender
        }
        val notes = lines.associate { (number, name, feeling) ->
            number to BrainNote(Speaker(name, genderOf(name, cast)), feeling.takeIf { it != Feeling.NEUTRAL })
        }
        return Parsed(notes, cast)
    }

    /** The cast line's pairs, the first call on a name kept (a model running on repeats itself, or contradicts). */
    private fun castOf(rows: List<String>): Map<String, SpeakerGender> {
        val heard = LinkedHashMap<String, SpeakerGender>()
        rows.lastOrNull { it.startsWith(CAST) }?.removePrefix(CAST)?.split(",")?.forEach { pair ->
            val name = pair.substringBeforeLast('=').trim()
            val gender = when (pair.substringAfterLast('=').trim()) {
                "F" -> SpeakerGender.FEMALE
                "M" -> SpeakerGender.MALE
                else -> null
            }
            if (name.isNotEmpty() && gender != null) heard.putIfAbsent(name, gender)
        }
        return heard
    }

    /** One answer row, or null when it does not read or names a line that was not asked about. */
    private fun line(row: String, wanted: Set<Int>): Triple<Int, String, Feeling>? {
        val parts = row.split('|')
        val number = parts.getOrNull(0)?.toIntOrNull()
        val name = parts.getOrNull(1)?.trim().orEmpty()
        val feeling = parts.getOrNull(2)?.trim()?.let(FEELING::get)
        val asked = parts.size == PARTS && number in wanted && name.isNotEmpty()
        return if (!asked || number == null || feeling == null) null else Triple(number, name, feeling)
    }

    private fun genderOf(name: String, cast: Map<String, SpeakerGender>): SpeakerGender {
        val key = bare(name)
        cast.entries.firstOrNull { bare(it.key) == key }?.let { return it.value }
        val words = key.split(' ').toSet()
        return cast.entries.firstOrNull { (other, _) ->
            val otherWords = bare(other).split(' ').toSet()
            words.any { it in otherWords && it.length > 1 }
        }?.value ?: SpeakerGender.UNKNOWN
    }

    private fun bare(name: String): String {
        val lower = name.trim().lowercase().replace(SPACES, " ")
        return ARTICLES.firstOrNull { lower.startsWith(it) }?.let { lower.removePrefix(it) } ?: lower
    }

    private const val CAST = "cast:"
    private const val PARTS = 3
    private val SPACES = Regex("""\s+""")
    private val ARTICLES = listOf("the ", "a ", "an ")

    /** The brain's words for feelings, onto the app's; "neutral" is kept as NEUTRAL so the row still reads. */
    private val FEELING = mapOf(
        "neutral" to Feeling.NEUTRAL,
        "joy" to Feeling.LIVELY,
        "sad" to Feeling.SAD,
        "angry" to Feeling.ANGRY,
        "scared" to Feeling.SCARED,
        "surprised" to Feeling.SURPRISED,
        "soft" to Feeling.SOFT,
        "loud" to Feeling.LOUD,
    )
}
