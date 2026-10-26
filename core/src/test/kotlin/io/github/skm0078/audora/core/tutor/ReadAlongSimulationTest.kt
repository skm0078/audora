// File: core/src/test/kotlin/io/github/skm0078/audora/core/tutor/ReadAlongSimulationTest.kt
// Purpose: Simulated readers (pauses, a skipped line, an imperfect recogniser) get through a whole page (Q-5).

package io.github.skm0078.audora.core.tutor

import io.github.skm0078.audora.core.model.Lang
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Before Q-5, a reader who paused after every word or two (a child, or anyone with the Hindi model's short phrases)
 * never finished a page here: one missed word held the place still for good. Each run is seeded, so a failure
 * repeats exactly; [Reader] is the reader and the recogniser together.
 */
class ReadAlongSimulationTest {
    private val page = listOf(
        "Whereas recognition of the inherent dignity and of the equal and inalienable rights of all members of the " +
            "human family is the foundation of freedom, justice and peace in the world,",
        "Whereas disregard and contempt for human rights have resulted in barbarous acts which have outraged the " +
            "conscience of mankind,",
        "and the advent of a world in which human beings shall enjoy freedom of speech and belief and freedom from " +
            "fear and want has been proclaimed as the highest aspiration of the common people,",
        "Whereas it is essential, if man is not to be compelled to have recourse, as a last resort, to rebellion " +
            "against tyranny and oppression, that human rights should be protected by the rule of law.",
    )
    private val words = PageWords.of(page, Lang.EN)
    private val keys = words.map { it.key }

    /**
     * Reads the page in stretches of 1 to [maxStretch] words with one line of [SKIPPED_LINE] words passed over. Each
     * word is heard right with [accuracy]; otherwise it is heard as another word, or not at all. Stretches grow word
     * by word before they end, as a streaming recogniser reports them.
     */
    private class Reader(val maxStretch: Int, val accuracy: Double)

    private data class Run(val along: ReadAlong, val readButUnread: Int)

    private fun read(reader: Reader, seed: Int): Run {
        val random = Random(seed)
        val skipped = random.nextInt(LINE_MARGIN, words.size - LINE_MARGIN).let { it until it + SKIPPED_LINE }
        var along = ReadAlong.start(words)
        var at = 0
        while (at < words.size) {
            val end = minOf(words.size, at + 1 + random.nextInt(reader.maxStretch))
            val stretch = (at until end).filter { it !in skipped }
            at = end
            val heard = stretch.mapNotNull { index ->
                val roll = random.nextDouble()
                when {
                    roll < reader.accuracy -> keys[index]
                    roll < (1 + reader.accuracy) / 2 -> "q" + keys[random.nextInt(keys.size)].reversed()
                    else -> null
                }
            }
            heard.indices.forEach { along = along.heard(heard.take(it + 1), final = false) }
            along = along.heard(heard, final = true)
        }
        val readButUnread = words.indices.count { it !in skipped && along.marks[it] == WordMark.UNREAD }
        return Run(along, readButUnread)
    }

    @Test
    fun `a slow reader with a clean recogniser finishes every page`() {
        repeat(RUNS) { seed ->
            val run = read(Reader(maxStretch = 2, accuracy = 1.0), seed)
            assertTrue("seed $seed", run.along.finished)
            assertEquals("seed $seed", 0, run.readButUnread)
            // The phone checks' pass line: at most one false "to check" per 50 words. A short stretch just after a
            // skipped line can fit a word before the gap as well as after it ("in the" for "is the").
            assertTrue("seed $seed", run.along.report().toCheck.size * WORDS_PER_FALSE_CHECK <= words.size)
        }
    }

    @Test
    fun `slow and fast readers with an imperfect recogniser finish nearly every page`() {
        listOf(Reader(maxStretch = 2, accuracy = 0.85), Reader(maxStretch = 10, accuracy = 0.85)).forEach { reader ->
            val finished = (0 until RUNS).count { read(reader, it).along.finished }
            assertTrue("${reader.maxStretch} words a stretch: $finished of $RUNS", finished >= RUNS * 9 / 10)
        }
    }

    private companion object {
        const val RUNS = 60
        const val SKIPPED_LINE = 8
        const val LINE_MARGIN = 12
        const val WORDS_PER_FALSE_CHECK = 50
    }
}
