// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/kws/CommandPhrases.kt
// Purpose: Finds "ListenLock, <command>" in what the phone's speech recogniser wrote (plan item 38).

package io.github.skm0078.audora.speech.kws

/**
 * The recogniser writes words, not commands, and the app's made-up name comes out in several spellings ("listenlock",
 * "listen lock", "listen log" in a strong accent). A command counts only right after one of them, within
 * [MAX_GAP_WORDS] words ("ListenLock, um, pause"), so the book's own "stop" or "next" through the speaker never acts.
 * Pure and stateless: one call per partial or final result.
 */
object CommandPhrases {
    /** The app's name as the recogniser writes it, each spelling as its words. */
    private val WAKE_SPELLINGS: List<List<String>> = listOf(
        "listenlock",
        "listen lock",
        "listen locks",
        "listen log",
        "listen look",
        "listen luck",
        "lesson lock",
        "listening lock",
    ).map { it.split(' ') }

    private const val MAX_GAP_WORDS = 1

    /** A command and the word it ended on, so a growing partial result is not acted on twice. */
    data class Found(val command: VoiceCommand, val endWord: Int)

    /** Every "ListenLock, command" in [text], in the order said. */
    fun find(text: String): List<Found> {
        val words = wordsOf(text)
        val found = mutableListOf<Found>()
        var i = 0
        while (i < words.size) {
            val wakeEnd = wakeEndingAt(words, i)
            val hit = wakeEnd?.let { commandAfter(words, it) }
            if (hit != null) found += hit
            i = hit?.endWord?.plus(1) ?: (wakeEnd ?: (i + 1))
        }
        return found
    }

    /** Lower-case letters only: punctuation and digits split words, as the recogniser's commas would. */
    internal fun wordsOf(text: String): List<String> = text.lowercase().split(NON_LETTERS).filter { it.isNotEmpty() }

    /** The index just past a wake spelling starting at [start], or null. */
    private fun wakeEndingAt(words: List<String>, start: Int): Int? = WAKE_SPELLINGS
        .filter { spelling -> spelling.indices.all { words.getOrNull(start + it) == spelling[it] } }
        .maxOfOrNull { start + it.size }

    /** The first command starting within [MAX_GAP_WORDS] words of [from], longest wording first ("go back"). */
    private fun commandAfter(words: List<String>, from: Int): Found? {
        for (start in from..from + MAX_GAP_WORDS) {
            for ((command, wording) in WORDINGS) {
                if (wording.indices.all { words.getOrNull(start + it) == wording[it] }) {
                    return Found(command, start + wording.size - 1)
                }
            }
        }
        return null
    }

    private val WORDINGS: List<Pair<VoiceCommand, List<String>>> = VoiceCommand.entries
        .flatMap { command -> command.words.map { command to it.split(' ') } }
        .sortedByDescending { it.second.size }

    private val NON_LETTERS = Regex("[^\\p{L}]+")
}
