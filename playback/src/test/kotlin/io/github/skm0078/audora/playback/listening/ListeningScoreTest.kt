// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/listening/ListeningScoreTest.kt
// Purpose: The listening score counts words only, and the units TSV and report files read back what was written.

package io.github.skm0078.audora.playback.listening

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.UnitKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningScoreTest {
    @Test
    fun `case, punctuation, spacing and zero-width characters do not count`() {
        val cases = listOf(
            "Article one." to "article ONE",
            "post-operative" to "post operative",
            "স্বীকৃতি‌ই" to "স্বীকৃতিই",
            "  ten,\n twenty " to "ten twenty",
        )
        cases.forEach { (script, heard) ->
            assertEquals("$script / $heard", ListeningScore(1.0, 1.0, 1.0), ListeningScore.of(script, heard))
        }
    }

    @Test
    fun `junk read aloud lowers precision, skipped text lowers recall`() {
        val junk = ListeningScore.of("the cat sat", "the cat sat e c zero")
        assertEquals(1.0, junk.wordRecall, 1e-9)
        assertEquals(0.5, junk.wordPrecision, 1e-9)
        val skipped = ListeningScore.of("the cat sat on the mat", "the cat sat")
        assertEquals(0.5, skipped.wordRecall, 1e-9)
        assertEquals(1.0, skipped.wordPrecision, 1e-9)
        assertTrue(skipped.similarity < 1.0)
    }

    @Test
    fun `units TSV lines read back with their kind, score, note and table info`() {
        val tsv = listOf(
            "page\tindex\tcharStart\tcharEnd\tkind\tlang\tpauseAfterMs\tspeakScore\tnoteRef\ttableIndex\trow\t" +
                "rowCount\tcolumnCount\theaders\tcells\ttext",
            "0\t0\t0\t9\tBODY\tEN\t400\t\t\t\t\t\t\t\t\ttab\\there",
            "0\t1\t10\t16\tTABLE_ROW\tEN\t0\t0.25\t\t0\t1\t3\t2\tName\u001FAge\t0-3,4-6\tRam 34",
            "0\t2\t20\t25\tFOOTNOTE\tEN\t0\t\t1\t\t\t\t\t\t\tNote.",
        ).joinToString("\n")
        val (body, row, note) = ListeningUnits.parse(tsv)
        assertEquals("tab\there", body.text)
        assertNull(body.speakScore)
        assertEquals(UnitKind.TABLE_ROW, row.kind)
        assertEquals(0.25f, row.speakScore)
        assertEquals(listOf("Name", "Age"), row.table?.headers)
        assertEquals(4, row.table?.cells?.last()?.start)
        assertEquals(1, note.noteRef)
        assertEquals(Lang.EN, note.lang)
    }

    @Test
    fun `the gate compares shared documents only and skips without a baseline`() {
        val doc = ListeningDocument("a", "clean-en", "a", "draft")
        val results = listOf(ListeningResult(doc, 3, ListeningScore(0.9, 1.0, 1.0)))
        val scores = ListeningReport.parseScores(ListeningReport.scoresTsv(results))
        assertEquals(mapOf("a" to 0.9), scores)

        assertTrue(ListeningReport.compare(emptyMap(), results).first)
        assertTrue(ListeningReport.compare(mapOf("a" to 0.9, "gone" to 1.0), results).first)
        assertFalse(ListeningReport.compare(mapOf("a" to 0.95), results).first)
    }
}
