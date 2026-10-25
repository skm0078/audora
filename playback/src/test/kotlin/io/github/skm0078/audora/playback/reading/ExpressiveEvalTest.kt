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
        val passages = listOf("en", "hi", "bn").flatMap { ExpressiveEval.load(File(dir, "$it.tsv")) }
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
        assertTrue(rightVoice(rows, Lang.EN) >= 55)
    }

    private fun opposite(row: Judged) = ExpressiveEval.opposite(row.line.feeling, row.feeling)

    private companion object {
        const val PERCENT = 100
    }
}
