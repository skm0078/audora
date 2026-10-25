// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/reading/ExpressiveEval.kt
// Purpose: Scores who-speaks and how-it-is-said against the marked story passages in research/expressive-eval.

package io.github.skm0078.audora.playback.reading

import io.github.skm0078.audora.core.model.ContentIntent
import io.github.skm0078.audora.core.model.Feeling
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.speech.delivery.DeliveryPlanner
import io.github.skm0078.audora.speech.route.QuoteSplitter
import io.github.skm0078.audora.speech.route.Speaker
import io.github.skm0078.audora.speech.sherpa.SpeakerGender
import java.io.File

/** One marked sentence: [speaker] null for narration, [feeling] one of the set's labels (see passages/en.tsv). */
data class GoldLine(val text: String, val speaker: String?, val feeling: String, val paragraphEnd: Boolean)

data class GoldPassage(val id: String, val lang: Lang, val cast: Map<String, SpeakerGender>, val lines: List<GoldLine>)

/** What the app decided for one sentence, next to the marks. */
data class Judged(
    val passage: GoldPassage,
    val line: GoldLine,
    val heardSpeech: Boolean,
    val speaker: Speaker?,
    val feeling: String,
) {
    private val goldGender: SpeakerGender get() = line.speaker?.let { passage.cast[it] } ?: SpeakerGender.UNKNOWN

    val speakerRight: Boolean
        get() = line.speaker != null && speaker?.key?.let(ExpressiveEval::bare) == ExpressiveEval.bare(line.speaker)

    /** What a listener hears with character voices on: a voice of the right gender, the wrong one, or none. */
    val voice: String
        get() = when (speaker?.gender) {
            null, SpeakerGender.UNKNOWN -> "none"
            goldGender -> "right"
            else -> "wrong"
        }
}

/**
 * Reads the passages the way playback does: each passage one page of units, a paragraph's last unit with a
 * paragraph pause, [QuoteCarry] for quotes, speakers and moods, then [DeliveryPlanner] as a story.
 */
object ExpressiveEval {
    const val PARAGRAPH_PAUSE_MS = 400

    fun load(file: File): List<GoldPassage> {
        val lang = Lang.valueOf(file.nameWithoutExtension.uppercase())
        val passages = mutableListOf<GoldPassage>()
        var id: String? = null
        var cast = emptyMap<String, SpeakerGender>()
        val lines = mutableListOf<GoldLine>()
        fun flush() {
            val name = id ?: return
            closeParagraph(lines)
            passages.add(GoldPassage(name, lang, cast, lines.toList()))
            lines.clear()
        }
        file.readLines().forEach { raw ->
            val row = raw.trimEnd()
            when {
                row.startsWith("@cast ") -> cast = castOf(row.removePrefix("@cast "))

                row.startsWith("@ ") -> {
                    flush()
                    id = row.removePrefix("@ ").trim()
                }

                row.startsWith("#") -> Unit

                row.isBlank() -> closeParagraph(lines)

                else -> {
                    val (text, speaker, feeling) = row.split('\t').also { require(it.size == 3) { "bad row: $row" } }
                    lines.add(GoldLine(text, speaker.takeUnless { it == "-" }, feeling, paragraphEnd = false))
                }
            }
        }
        flush()
        return passages
    }

    fun judge(passages: List<GoldPassage>): List<Judged> {
        val splitter = QuoteSplitter()
        val planner = DeliveryPlanner()
        return passages.flatMapIndexed { page, passage ->
            val carry = QuoteCarry()
            val units = passage.lines.mapIndexed { i, line -> unitOf(page, i, line, passage.lang) }
            carry.page(units)
            units.zip(passage.lines).map { (unit, line) ->
                val quoted = carry.startsInQuote(unit) || splitter.split(unit.text).any { it.quoted }
                val feeling = planner.plan(unit, ContentIntent.STORY, quoted, carry.moodOf(unit)).feeling
                Judged(passage, line, quoted, carry.speakerOf(unit), labelOf(feeling))
            }
        }
    }

    /** The set's label for the app's [Feeling]; asking and trailing are how a line ends, not a feeling. */
    fun labelOf(feeling: Feeling): String = when (feeling) {
        Feeling.LOUD -> "loud"
        Feeling.SOFT -> "soft"
        Feeling.SAD -> "sad"
        Feeling.SCARED -> "scared"
        Feeling.SURPRISED -> "surprised"
        Feeling.LIVELY -> "joy"
        Feeling.EMPHATIC -> "emphatic"
        Feeling.TRAILING, Feeling.ASKING, Feeling.NEUTRAL -> "neutral"
    }

    /** Feelings a listener hears as near enough: an angry shout read loud, a sad line read softly. */
    private val CLOSE = setOf(
        "angry" to "loud",
        "joy" to "loud",
        "sad" to "soft",
        "scared" to "soft",
        "joy" to "emphatic",
        "angry" to "emphatic",
        "loud" to "emphatic",
        "surprised" to "emphatic",
    )

    /** A feeling read as its opposite, the slip a listener notices most: grief or fear read cheerfully. */
    private val OPPOSITE = setOf(
        "sad" to "joy",
        "scared" to "joy",
        "angry" to "joy",
        "joy" to "sad",
        "angry" to "soft",
        "loud" to "soft",
    )

    fun close(gold: String, heard: String): Boolean = gold == heard || (gold to heard) in CLOSE

    fun opposite(gold: String, heard: String): Boolean = (gold to heard) in OPPOSITE

    /** Scores by language, then every failure, as Markdown. */
    fun report(judged: List<Judged>): String = buildString {
        appendLine(
            "| Lang | Lines with speech | Speech found | Right speaker | Right-gender voice | Wrong-gender voice |",
        )
        appendLine("|---|---:|---:|---:|---:|---:|")
        val byLang = judged.groupBy { it.passage.lang }
        byLang.forEach { (lang, rows) ->
            val speech = rows.filter { it.line.speaker != null }
            appendLine(
                "| ${lang.name} | ${speech.size} | ${pct(speech.count { it.heardSpeech }, speech.size)} | " +
                    "${pct(speech.count { it.speakerRight }, speech.size)} | " +
                    "${pct(speech.count { it.voice == "right" }, speech.size)} | " +
                    "${pct(speech.count { it.voice == "wrong" }, speech.size)} |",
            )
        }
        appendLine()
        appendLine("| Lang | Lines | Feeling right | Near enough | Feeling missed | Neutral coloured | Opposite |")
        appendLine("|---|---:|---:|---:|---:|---:|---:|")
        byLang.forEach { (lang, rows) ->
            val felt = rows.filter { it.line.feeling != "neutral" }
            val calm = rows.filter { it.line.feeling == "neutral" }
            appendLine(
                "| ${lang.name} | ${rows.size} | ${pct(rows.count { it.feeling == it.line.feeling }, rows.size)} | " +
                    "${pct(rows.count { close(it.line.feeling, it.feeling) }, rows.size)} | " +
                    "${pct(felt.count { it.feeling == "neutral" }, felt.size)} | " +
                    "${pct(calm.count { it.feeling != "neutral" }, calm.size)} | " +
                    "${rows.count { opposite(it.line.feeling, it.feeling) }} |",
            )
        }
        appendLine()
        appendLine("## Every miss")
        appendLine()
        appendLine("| Passage | Sentence | Speaker: marked / found | Feeling: marked / found |")
        appendLine("|---|---|---|---|")
        judged.filter { (!it.speakerRight && it.line.speaker != null) || !close(it.line.feeling, it.feeling) }
            .forEach { row ->
                val found = row.speaker?.let { it.name ?: "(${it.gender.name.lowercase()})" }
                    ?: if (row.heardSpeech) "?" else "no speech found"
                val speaker = row.line.speaker?.let { "$it / $found" } ?: ""
                val feeling = "${row.line.feeling} / ${row.feeling}"
                appendLine("| ${row.passage.id} | ${row.line.text} | $speaker | $feeling |")
            }
    }

    private fun pct(part: Int, whole: Int): String = if (whole == 0) "-" else "${part * 100 / whole} %"

    /** "the old man" and "Old man" are one speaker; so are "Captain" and "the captain". */
    fun bare(name: String): String = name.trim().lowercase().removePrefix("the ").removePrefix("a ")

    private fun castOf(spec: String): Map<String, SpeakerGender> = Regex("""(.+?)=([FM])(?:\s+|$)""")
        .findAll(spec).associate { match ->
            val gender = if (match.groupValues[2] == "F") SpeakerGender.FEMALE else SpeakerGender.MALE
            match.groupValues[1].trim() to gender
        }

    private fun closeParagraph(lines: MutableList<GoldLine>) {
        val last = lines.lastOrNull() ?: return
        lines[lines.lastIndex] = last.copy(paragraphEnd = true)
    }

    private fun unitOf(page: Int, index: Int, line: GoldLine, lang: Lang): ReadingUnit = ReadingUnit(
        page = page,
        index = index,
        charStart = 0,
        charEnd = line.text.length,
        text = line.text,
        lang = lang,
        pauseAfterMs = if (line.paragraphEnd) PARAGRAPH_PAUSE_MS else 0,
        bounds = null,
    )
}
