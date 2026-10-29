// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/text/ZipNamesTest.kt
// Purpose: Zips whose names are not UTF-8 (Windows' built-in zip, older tools) are read with Latin-1 instead of
//          crashing, an EPUB among them still parses, and a UTF-8 zip is read once, as before.

package io.github.skm0078.audora.playback.text

import io.github.skm0078.audora.core.result.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ZipNamesTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val epubText = mapOf(
        "META-INF/container.xml" to """<?xml version="1.0"?>
            <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles>
            <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
            </rootfiles></container>""",
        "OEBPS/content.opf" to """<?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0"><metadata
            xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Zip Names</dc:title></metadata>
            <manifest><item id="c1" href="chapter.xhtml" media-type="application/xhtml+xml"/></manifest>
            <spine><itemref idref="c1"/></spine></package>""",
        "OEBPS/chapter.xhtml" to "<html><body><p>A book zipped with names that are not Unicode.</p></body></html>",
    )

    /** A zip whose entries are named in [charset]; a charset other than UTF-8 leaves the UTF-8 flag clear. */
    private fun zip(names: Map<String, ByteArray>, charset: Charset): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out, charset).use { zip ->
            names.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun names(zip: ZipInputStream): List<String> = generateSequence { zip.nextEntry }.map { it.name }.toList()

    @Test
    fun `a zip named in a code page is read again with Latin-1 instead of throwing`() {
        val bytes = zip(mapOf("cover.png" to byteArrayOf(1), "images/café.png" to byteArrayOf(2)), Charsets.ISO_8859_1)
        // The bug: Java refuses the name outright.
        val refused = runCatching { ZipInputStream(ByteArrayInputStream(bytes)).use(::names) }.exceptionOrNull()
        assertTrue("Java should refuse it: $refused", refused is IllegalArgumentException)

        var opened = 0
        val read = ZipNames.stream({ opened++; ByteArrayInputStream(bytes) }, ::names)

        assertEquals(listOf("cover.png", "images/café.png"), read)
        assertEquals(2, opened)
    }

    @Test
    fun `a UTF-8 zip is read once with its names as they are`() {
        val bytes = zip(mapOf("পাতা/প্রথম.xhtml" to byteArrayOf(1)), Charsets.UTF_8)
        var opened = 0

        val read = ZipNames.stream({ opened++; ByteArrayInputStream(bytes) }, ::names)

        assertEquals(listOf("পাতা/প্রথম.xhtml"), read)
        assertEquals(1, opened)
    }

    @Test
    fun `an EPUB with one picture named in a code page still parses`() {
        val entries = epubText.mapValues { it.value.toByteArray() } + ("OEBPS/images/café.png" to byteArrayOf(0))
        val bytes = zip(entries, Charsets.ISO_8859_1)

        val text = ZipNames.stream({ ByteArrayInputStream(bytes) }) { zip ->
            generateSequence { zip.nextEntry }.filter { EpubParser.isTextEntry(it.name) }
                .associate { it.name to zip.readBytes() }
        }
        val parsed = EpubParser.parse(text)

        assertTrue("$parsed", parsed is Outcome.Success)
        val chapter = (parsed as Outcome.Success).value.chapters.single()
        assertTrue(chapter.blocks.joinToString { it.text }.contains("not Unicode"))
    }

    @Test
    fun `a zip file named in a code page opens with Latin-1 and finds its pages by name again`() {
        val file = folder.newFile("comic.cbz")
        val pages = mapOf("página1.png" to byteArrayOf(1), "página2.png" to byteArrayOf(2))
        file.writeBytes(zip(pages, Charsets.ISO_8859_1))

        val (charset, names) = ZipNames.file(file) { zip, charset ->
            charset to zip.entries().asSequence().map { it.name }.toList()
        }

        assertEquals(ZipNames.FALLBACK, charset)
        assertEquals(listOf("página1.png", "página2.png"), names)
        java.util.zip.ZipFile(file, charset).use { zip -> assertNotNull(zip.getEntry(names.last())) }
    }

    @Test
    fun `a UTF-8 zip file is read with UTF-8`() {
        val file = folder.newFile("comic.cbz")
        file.writeBytes(zip(mapOf("পাতা১.png" to byteArrayOf(1)), Charsets.UTF_8))

        val charset = ZipNames.file(file) { _, charset -> charset }

        assertEquals(Charsets.UTF_8, charset)
    }

    @Test(expected = ZipException::class)
    fun `a damaged zip file still fails as damaged`() {
        val file = folder.newFile("broken.cbz")
        file.writeBytes(ByteArray(64) { it.toByte() })

        ZipNames.file(file) { zip, _ -> zip.size() }
    }
}
