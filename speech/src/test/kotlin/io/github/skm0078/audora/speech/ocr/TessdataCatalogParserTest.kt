// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/ocr/TessdataCatalogParserTest.kt
// Purpose: The APK's OCR model list parses, and any broken entry fails the whole list.

package io.github.skm0078.audora.speech.ocr

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.getOrNull
import java.io.File
import java.util.Properties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TessdataCatalogParserTest {
    private fun shipped(): Properties = Properties().apply {
        File("src/main/assets/${TessdataCatalog.ASSET_PATH}").reader(Charsets.UTF_8).use { load(it) }
    }

    @Test
    fun `the shipped catalog lists English, Hindi and Bengali from a pinned commit`() {
        val catalog = requireNotNull(TessdataCatalogParser.parse(shipped()).getOrNull())
        assertEquals(listOf(Lang.EN, Lang.HI, Lang.BN), catalog.models.map { it.lang })
        assertEquals(listOf("eng", "hin", "ben").map { "$it.traineddata" }, catalog.models.map { it.fileName })
        for (model in catalog.models) {
            assertTrue(model.url, model.url.matches(Regex("https://[^ ]+/[0-9a-f]{40}/${model.fileName}")))
            assertEquals("ocr-${model.lang.bcp47Tag}", model.id)
        }
        assertEquals(catalog.models.sumOf { it.sizeBytes }, catalog.totalBytes)
        assertEquals(Lang.HI, catalog.find(Lang.HI)?.lang)
    }

    @Test
    fun `a broken entry fails the whole catalog`() {
        val breakages: List<Pair<String, String?>> = listOf(
            "revision" to null,
            "baseUrl" to "http://insecure.test/",
            "baseUrl" to "https://no-trailing-slash.test",
            "langs" to "",
            "langs" to "en,xx",
            "langs" to "en,en",
            "hi.file" to "../hin.traineddata",
            "hi.sha256" to "abc",
            "bn.sizeBytes" to "0",
            "bn.sizeBytes" to null,
        )
        for ((key, value) in breakages) {
            val properties = shipped().apply { if (value == null) remove(key) else setProperty(key, value) }
            val parsed = TessdataCatalogParser.parse(properties)
            assertTrue("$key=$value parsed as $parsed", parsed is Outcome.Failure)
        }
    }
}
