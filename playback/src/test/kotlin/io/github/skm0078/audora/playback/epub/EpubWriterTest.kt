// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/epub/EpubWriterTest.kt
// Purpose: The EPUB container, well-formed XML everywhere, accessibility metadata and media overlays.

package io.github.skm0078.audora.playback.epub

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.UnitKind
import io.github.skm0078.audora.playback.export.AudioChapter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EpubWriterTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val meta = EpubMeta("urn:uuid:00000000-0000-0000-0000-000000000001", Instant.EPOCH, "Text & audio")
    private val doc = DocumentBuilder { "Pages" }.build(
        "Ram & <Sita>",
        listOf(AudioChapter(1, 0, 0), AudioChapter(2, 1, 1)),
        mapOf(
            0 to listOf(
                sentence(0, 0, "Chapter one", UnitKind.HEADING, PARAGRAPH_END),
                sentence(0, 1, "राम ने कहा \"चलो\"।").copy(lang = Lang.HI),
                tableUnit(0, 2, null, "Name", "Age"),
                tableUnit(0, 3, 0, "Ram", "34"),
                note(0, 4, "1 A note.", 1),
            ),
            1 to listOf(sentence(1, 0, "Second chapter.\u0001")),
        ),
    )!!

    @Test
    fun `the mimetype comes first, stored, and every XML file is well-formed`() {
        val entries = unzip(write())

        assertEquals("mimetype", entries.first().name)
        assertEquals(ZipEntry.STORED, entries.first().method)
        assertEquals("application/epub+zip", entries.first().text)
        val xml = entries.filter { it.name.substringAfterLast('.') in setOf("xml", "opf", "xhtml", "smil") }
        assertEquals(5, xml.size)
        xml.forEach { parse(it.text) }
    }

    @Test
    fun `chapters carry ids, languages, page breaks, table headers and notes`() {
        val chapter = unzip(write()).single { it.name == "OEBPS/chapter-1.xhtml" }.text

        assertTrue(chapter.contains("<h1><span epub:type=\"pagebreak\" role=\"doc-pagebreak\" id=\"page1\""))
        assertTrue(chapter.contains("<span id=\"s0-1\" lang=\"hi\" xml:lang=\"hi\">राम ने कहा &quot;चलो&quot;।</span>"))
        assertTrue(chapter.contains("<tr id=\"s0-2\"><th scope=\"col\">Name</th><th scope=\"col\">Age</th></tr>"))
        assertTrue(chapter.contains("<aside epub:type=\"footnote\" role=\"doc-footnote\" id=\"n-s0-4\">"))
        val nav = unzip(write()).single { it.name == "OEBPS/nav.xhtml" }.text
        assertTrue(nav.contains("<a href=\"chapter-2.xhtml#page2\">2</a>"))
    }

    @Test
    fun `a talking book declares its overlays and stores the audio as it is`() {
        val bytes = ByteArray(1_000) { it.toByte() }
        val file = temp.newFile("01.m4a").apply { writeBytes(bytes) }
        val audio =
            ChapterAudio(1, file, 1_000, listOf(SentenceClip("s0-0", 0, 1_500), SentenceClip("s0-1", 1_500, 3_000)))
        val entries = unzip(write(mapOf(1 to audio)))

        val opf = entries.single { it.name == "OEBPS/content.opf" }.text
        assertTrue(opf.contains("media-overlay=\"mo1\""))
        assertTrue(opf.contains("<meta property=\"media:duration\" refines=\"#mo1\">0:00:03.000</meta>"))
        assertTrue(opf.contains("<meta property=\"schema:accessibilityFeature\">synchronizedAudioText</meta>"))
        assertFalse(opf.contains("media-overlay=\"mo2\""))
        val smil = entries.single { it.name == "OEBPS/chapter-1.smil" }.text
        assertTrue(
            smil.contains(
                "<text src=\"chapter-1.xhtml#s0-1\"/><audio src=\"audio/chapter-1.m4a\" " +
                    "clipBegin=\"0:00:01.500\" clipEnd=\"0:00:03.000\"/>",
            ),
        )
        val stored = entries.single { it.name == "OEBPS/audio/chapter-1.m4a" }
        assertEquals(ZipEntry.STORED, stored.method)
        assertArrayEquals(bytes, stored.bytes)
    }

    @Test
    fun `clock values are hours, minutes, seconds and milliseconds`() {
        assertEquals("1:01:01.240", Smil.clock(3_661_240L * 22_050 / 1_000, 22_050))
    }

    private fun write(audio: Map<Int, ChapterAudio> = emptyMap()): ByteArray =
        ByteArrayOutputStream().also { EpubWriter().write(it, doc, meta, audio) }.toByteArray()

    private class Entry(val name: String, val method: Int, val bytes: ByteArray) {
        val text: String get() = bytes.toString(Charsets.UTF_8)
    }

    private fun unzip(bytes: ByteArray): List<Entry> = ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        generateSequence { zip.nextEntry }.map { Entry(it.name, it.method, zip.readBytes()) }.toList()
    }

    private fun parse(xml: String) {
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
            .parse(ByteArrayInputStream(xml.toByteArray()))
    }
}
