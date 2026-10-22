// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/punct/RunOnPunctuation.kt
// Purpose: Lets the punctuation model add pauses to a scanned run-on sentence, keeping every word as it was (RI-11).

package io.github.skm0078.audora.speech.punct

import io.github.skm0078.audora.core.config.PunctuationConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.result.Outcome

/** Adds punctuation to a text; a failure carries a log-safe reason (never the text). */
fun interface Punctuator {
    fun punctuate(text: String): Outcome<String, String>
}

/**
 * Only the marks are taken from the model's answer: its words are matched one for one against the original's, and
 * any difference (a word changed, split, merged or dropped) keeps the original text whole. Words keep their own
 * spelling and case, so an acronym the model lower-cases still reads as before. A mark goes in only after a word
 * that has none; the voice then pauses there (RI-6's clause pauses). Full stops are softened to commas
 * ([PunctuationConfig.softenedMarks]): the model places them poorly, so a wrong one costs only a short pause.
 *
 * @param punctuator the installed model, or null while none is downloaded (the text then passes unchanged).
 */
class RunOnPunctuation(
    private val config: PunctuationConfig = PunctuationConfig(),
    private val punctuator: () -> Punctuator?,
) {
    /** [text] with the model's marks added when it is a run-on in a language the model reads, else [text]. */
    fun apply(text: String, lang: Lang, logContext: String): String {
        val model = (if (isRunOn(text, lang)) punctuator() else null) ?: return text
        return when (val answer = model.punctuate(text)) {
            is Outcome.Failure -> text.also { AudoraLog.w(TAG, "$logContext: punctuation failed: ${answer.error}") }

            is Outcome.Success -> merge(text, answer.value).also { merged ->
                if (merged == null) AudoraLog.d(TAG, "$logContext: the model changed words; kept as written")
            } ?: text
        }
    }

    /** A run-on: long enough, in a language the model reads, and with no sentence end inside. */
    fun isRunOn(text: String, lang: Lang): Boolean = lang in config.langs &&
        text.none { it in config.sentenceEnds } &&
        words(text).size >= config.minRunWords

    /** The original's words with the answer's marks after them; null when the two word sequences differ. */
    internal fun merge(original: String, answer: String): String? {
        val mine = original.trim().split(SPACE)
        val theirs = answer.trim().split(SPACE)
        return when {
            mine.size != theirs.size -> null
            mine.indices.any { !bare(mine[it]).equals(bare(theirs[it]), ignoreCase = true) } -> null
            else -> mine.indices.joinToString(" ") { mine[it] + markAfter(mine[it], theirs[it]) }
        }
    }

    /** The answer's first mark after [word] (softened), when [word] ends in a letter or digit (it has no mark). */
    private fun markAfter(word: String, answered: String): String {
        val mark = answered.substring(bare(answered).length).take(1)
        return when {
            !word.last().isLetterOrDigit() -> ""
            mark.isNotEmpty() && mark[0] in config.softenedMarks -> config.softMark.toString()
            else -> mark
        }
    }

    private fun bare(word: String): String = word.trimEnd { it in config.addedMarks }

    private fun words(text: String): List<String> = text.split(SPACE).filter { it.any(Char::isLetterOrDigit) }

    private companion object {
        const val TAG = "Speech.Punct"
        val SPACE = Regex("\\s+")
    }
}
