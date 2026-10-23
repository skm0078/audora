// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/acted/ActedVoiceModelTest.kt
// Purpose: The acted Hindi voice's two pinned downloads repeat speech/models.json exactly (ADR-059).

package io.github.skm0078.audora.speech.acted

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActedVoiceModelTest {
    private val catalog = (File("models.json").takeIf { it.isFile } ?: File("speech/models.json")).readText()

    @Test
    fun `both parts repeat their models json pins and files`() {
        for (part in ActedVoiceModel.PARTS) {
            val entry = catalog.substringAfter("\"id\": \"${part.id}\"").substringBefore("\"id\":")
            listOf(
                "\"file\": \"${part.fileName}\"",
                "\"sha256\": \"${part.sha256}\"",
                "\"sizeBytes\": ${part.sizeBytes}",
            ).forEach { assertTrue("models.json lacks $it for ${part.id}", it in entry) }
            part.files.forEach { assertTrue("models.json does not name ${part.id}'s $it", "\"$it\"" in entry) }
            assertEquals("${part.id}-${part.sha256.take(12)}.zip", part.fileName)
            assertTrue(part.url.startsWith("https://github.com/skm0078/audora/releases/download/voices/"))
        }
    }

    @Test
    fun `each part stays under GitHub's release file limit`() {
        ActedVoiceModel.PARTS.forEach { assertTrue(it.id, it.sizeBytes < GITHUB_FILE_LIMIT) }
    }

    private companion object {
        const val GITHUB_FILE_LIMIT = 2L * 1024 * 1024 * 1024
    }
}
