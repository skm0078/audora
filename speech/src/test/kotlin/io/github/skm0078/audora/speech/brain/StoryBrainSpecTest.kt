// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/brain/StoryBrainSpecTest.kt
// Purpose: The app's story brain pins match speech/story-brain.json, and its licence allows commercial use (ADR
//          story-brain).

package io.github.skm0078.audora.speech.brain

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryBrainSpecTest {
    private val json = (File("story-brain.json").takeIf { it.isFile } ?: File("speech/story-brain.json")).readText()
    private val model = StoryBrainModel.QWEN35_4B

    private fun string(key: String): List<String> =
        Regex(""""$key"\s*:\s*"([^"]*)"""").findAll(json).map { it.groupValues[1] }.toList()

    private fun number(key: String): List<Long> =
        Regex(""""$key"\s*:\s*(\d+)""").findAll(json).map { it.groupValues[1].toLong() }.toList()

    @Test
    fun `the app's pins are the published ones`() {
        assertEquals(model.id, string("id").single())
        // The whole file's pin first, then each part's, in order.
        assertEquals(listOf(model.sha256) + model.parts.map { it.sha256 }, string("sha256"))
        assertEquals(listOf(model.sizeBytes) + model.parts.map { it.sizeBytes }, number("sizeBytes"))
        assertEquals(model.parts.map { it.id }, string("file").drop(1))
        assertEquals(model.licence, string("licence").single())
    }

    @Test
    fun `parts add up to the file and are fetched from the app's own release`() {
        assertEquals(model.sizeBytes, model.parts.sumOf { it.sizeBytes })
        val base = string("baseUrl").single()
        model.parts.forEach { part ->
            assertEquals("$base${part.id}", part.url)
            assertTrue(part.sizeBytes < GITHUB_ASSET_LIMIT)
        }
    }

    @Test
    fun `the licence is not a non-commercial one`() {
        (string("licence") + string("licenceUrl") + listOf(model.licence)).forEach {
            assertFalse("non-commercial licence: $it", NON_COMMERCIAL.containsMatchIn(it))
        }
    }

    private companion object {
        const val GITHUB_ASSET_LIMIT = 2L * 1024 * 1024 * 1024
        val NON_COMMERCIAL = Regex("""(?i)\bBY-NC\b|/by-nc|non-?commercial""")
    }
}
