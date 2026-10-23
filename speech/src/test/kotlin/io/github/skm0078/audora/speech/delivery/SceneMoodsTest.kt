// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/delivery/SceneMoodsTest.kt
// Purpose: Sad and frightened lines carry into the next plain sentence of their paragraph, and no further.

package io.github.skm0078.audora.speech.delivery

import io.github.skm0078.audora.core.model.Feeling
import org.junit.Assert.assertEquals
import org.junit.Test

class SceneMoodsTest {
    @Test
    fun `a sad line colours the plain sentence after it, once`() {
        val moods = SceneMoods.of(listOf("মিনু কেঁদে ফেলল।", "এত বছর পরে দেখা।", "তারপর চা এল।"))

        assertEquals(listOf(null, Feeling.SAD, null), moods)
    }

    @Test
    fun `a moment never carries, and a sentence with its own feeling keeps it`() {
        assertEquals(listOf(null, null), SceneMoods.of(listOf("He laughed.", "The door opened.")))
        assertEquals(listOf(null, null), SceneMoods.of(listOf("वह डरकर पीछे हटा।", "“कौन है?”")))
        assertEquals(listOf(null, Feeling.SCARED), SceneMoods.of(listOf("She trembled.", "The house was dark.")))
    }
}
