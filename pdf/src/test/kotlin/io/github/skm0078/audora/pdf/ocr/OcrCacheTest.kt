// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/ocr/OcrCacheTest.kt
// Purpose: The OCR text cache: round trip, keys that must not collide, safe file names and the size limit.

package io.github.skm0078.audora.pdf.ocr

import io.github.skm0078.audora.core.model.Lang
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrCacheTest {
    private val dir: File = Files.createTempDirectory("ocr-cache").toFile()

    @After
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun key(page: Int = 1, langs: Set<Lang> = setOf(Lang.HI), dpi: Int = 300, tag: String = "t1") =
        OcrKey("book1", page, langs, dpi, tag)

    @Test
    fun `text comes back for the same key only`() {
        val cache = OcrCache(dir, maxBytes = 1_000_000)
        cache.put(key(), "मनुष्य\n\nदूसरा")
        assertEquals("मनुष्य\n\nदूसरा", cache.get(key()))
        assertEquals("मनुष्य\n\nदूसरा", cache.get(key(langs = linkedSetOf(Lang.HI))))
        assertNull(cache.get(key(page = 2)))
        assertNull(cache.get(key(langs = setOf(Lang.HI, Lang.EN))))
        assertNull(cache.get(key(dpi = 100)))
        assertNull(cache.get(key(tag = "t2")))
    }

    @Test
    fun `language order does not change the key and odd characters stay out of the file name`() {
        val a = OcrKey("../x y", 0, linkedSetOf(Lang.EN, Lang.BN), 300, "best/e12c")
        val b = OcrKey("../x y", 0, linkedSetOf(Lang.BN, Lang.EN), 300, "best/e12c")
        assertEquals(a.fileName(), b.fileName())
        assertTrue(a.fileName(), a.fileName().matches(Regex("[A-Za-z0-9._+-]+")))
    }

    @Test
    fun `the least recently used pages go once the folder is over its limit`() {
        val page = "x".repeat(100)
        val cache = OcrCache(dir, maxBytes = 250)
        cache.put(key(page = 1), page)
        cache.put(key(page = 2), page)
        // Page 1 was read last, so page 2 is the oldest when page 3 pushes the folder over 250 bytes.
        File(dir, key(page = 2).fileName()).setLastModified(OLD)
        File(dir, key(page = 1).fileName()).setLastModified(OLD + 1_000)
        cache.put(key(page = 3), page)
        assertNull(cache.get(key(page = 2)))
        assertEquals(page, cache.get(key(page = 1)))
        assertEquals(page, cache.get(key(page = 3)))
    }

    @Test
    fun `a cache folder that cannot be written is not an error`() {
        val blocked = File(dir, "file").apply { writeText("not a folder") }
        val cache = OcrCache(blocked, maxBytes = 1_000)
        cache.put(key(), "text")
        assertNull(cache.get(key()))
    }

    private companion object {
        const val OLD = 1_000_000_000_000L
    }
}
