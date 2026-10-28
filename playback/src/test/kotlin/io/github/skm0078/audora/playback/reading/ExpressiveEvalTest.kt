// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/reading/ExpressiveEvalTest.kt
// Purpose: Guards who-speaks and how-it-is-said on the marked story passages, so a change can't quietly break them.

package io.github.skm0078.audora.playback.reading

import io.github.skm0078.audora.core.model.Lang
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Scores [ExpressiveEval] on the tuned passages and on held-out ones the rules were never tuned on, writes both
 * reports to build/expressive (for the PR and the plan), and fails when a score drops below its floor. Raise a floor
 * when a change lifts it; never lower one to make a change pass: a lower score is a listener hearing the wrong
 * character or the wrong feeling.
 */
class ExpressiveEvalTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "research/expressive-eval").isDirectory }
    private val out = File(root, "playback/build/expressive").apply { mkdirs() }

    private fun judged(set: String): List<Judged> {
        val dir = File(root, "research/expressive-eval/$set")
        val passages = listOf("en", "hi", "bn").map { File(dir, "$it.tsv") }.filter { it.isFile }
            .flatMap { ExpressiveEval.load(it) }
        return ExpressiveEval.judge(passages).also { File(out, "$set.md").writeText(ExpressiveEval.report(it)) }
    }

    /** Percent of lines with speech that get a character voice of the right gender. */
    private fun rightVoice(rows: List<Judged>, lang: Lang): Int {
        val speech = rows.filter { it.passage.lang == lang && it.line.speaker != null }
        return speech.count { it.voice == "right" } * PERCENT / speech.size
    }

    @Test
    fun `tuned passages keep their scores`() {
        val rows = judged("passages")
        assertTrue("every spoken line is found", rows.filter { it.line.speaker != null }.all { it.heardSpeech })
        assertEquals("no character gets the other gender's voice", 0, rows.count { it.voice == "wrong" })
        assertEquals("no feeling is read as its opposite", 0, rows.count { opposite(it) })
        assertTrue(rightVoice(rows, Lang.EN) >= 85)
        assertTrue(rightVoice(rows, Lang.HI) >= 88)
        assertTrue(rightVoice(rows, Lang.BN) >= 100)
    }

    @Test
    fun `held-out passages keep their scores`() {
        val rows = judged("holdout")
        assertTrue("every spoken line is found", rows.filter { it.line.speaker != null }.all { it.heardSpeech })
        assertEquals("no character gets the other gender's voice", 0, rows.count { it.voice == "wrong" })
        assertTrue(rows.count { opposite(it) } <= 1)
        assertTrue(rightVoice(rows, Lang.EN) >= 62)
        assertTrue(rightVoice(rows, Lang.HI) >= 61)
        assertTrue(rightVoice(rows, Lang.BN) >= 46)
    }

    /**
     * Written after the Hindi and Bengali name-finding change (ADR-077) and scored once before its Bengali half. One
     * Hindi line goes to the wrong person: the arriving man, the narration's last subject, gets his wife's question.
     */
    @Test
    fun `second held-out passages keep their scores`() {
        val rows = judged("holdout2")
        assertTrue("every spoken line is found", rows.filter { it.line.speaker != null }.all { it.heardSpeech })
        assertTrue("at most one line has the other gender's voice", rows.count { it.voice == "wrong" } <= 1)
        assertTrue(rightVoice(rows, Lang.HI) >= 60)
        assertTrue(rightVoice(rows, Lang.BN) >= 50)
    }

    /**
     * What a listener hears with the default narrators ([Narrator.DEFAULTS]): a line whose speaker or gender is
     * unknown is read by the narrator, so it counts as wrong when the narrator is the other gender. Measured before
     * the character-genders change (dev ce1caf2): tuned 2 / 3 / 8, held-out 9 / 7 / 4, second held-out - / 3 / 2
     * lines (EN / HI / BN, Bengali then with no voice for its women).
     */
    @Test
    fun `few spoken lines are heard in the other gender's voice`() {
        assertEquals(listOf(2, 3, 0), heardWrong(judged("passages")))
        assertTrue(heardWrong(judged("holdout")).zip(listOf(8, 4, 2)).all { (now, floor) -> now <= floor })
        assertTrue(heardWrong(judged("holdout2")).zip(listOf(0, 1, 1)).all { (now, floor) -> now <= floor })
    }

    /** Spoken lines heard in the wrong gender's voice, EN, HI, BN. */
    private fun heardWrong(rows: List<Judged>): List<Int> = listOf(Lang.EN, Lang.HI, Lang.BN).map { lang ->
        rows.count { it.passage.lang == lang && it.heardWrong(Narrator.DEFAULTS.getValue(lang)) }
    }

    private fun opposite(row: Judged) = ExpressiveEval.opposite(row.line.feeling, row.feeling)

    private companion object {
        const val PERCENT = 100
    }
}
