// File: core/src/test/kotlin/io/github/skm0078/audora/core/library/ShelfLookStoreTest.kt
// Purpose: A collection's colour and emoji survive in their file; defaults are not written, and a file in another
//          format is set aside rather than overwritten.

package io.github.skm0078.audora.core.library

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShelfLookStoreTest {
    private val folder = Files.createTempDirectory("shelf-looks").toFile()
    private val file = File(folder, ShelfLookStore.FILE_NAME)
    private val store = ShelfLookStore(file)

    @After
    fun cleanUp() {
        folder.deleteRecursively()
    }

    @Test
    fun `a colour and an emoji come back as saved`() {
        val looks = mapOf(
            "c1" to ShelfLook(ShelfColour.TEAL, ShelfEmoji.MOON),
            "c2" to ShelfLook(colour = ShelfColour.PLUM),
            "c3" to ShelfLook(emoji = ShelfEmoji.LAMP),
        )

        assertTrue(store.save(looks))

        assertEquals(looks, ShelfLookStore(file).load())
    }

    @Test
    fun `default looks are left out, and none at all deletes the file`() {
        assertTrue(store.save(mapOf("c1" to ShelfLook.DEFAULT, "c2" to ShelfLook(ShelfColour.CLAY))))
        assertEquals(mapOf("c2" to ShelfLook(ShelfColour.CLAY)), store.load())

        assertTrue(store.save(mapOf("c2" to ShelfLook.DEFAULT)))
        assertFalse(file.exists())
        assertEquals(emptyMap<String, ShelfLook>(), store.load())
    }

    @Test
    fun `an unknown colour or emoji name reads as the default for that field`() {
        file.writeText("audora-shelf-looks 1\nc1\tGOLD\tMOON\nc2\tFOREST\tROCKET\n\tTEAL\t\nbroken line\n")

        assertEquals(
            mapOf("c1" to ShelfLook(emoji = ShelfEmoji.MOON), "c2" to ShelfLook(ShelfColour.FOREST)),
            store.load(),
        )
    }

    @Test
    fun `a file in another format is set aside, not overwritten`() {
        file.writeText("audora-shelf-looks 9\nc1\tTEAL\tMOON\n")
        val movedAside = mutableListOf<File>()
        val store = ShelfLookStore(file) { aside ->
            movedAside += aside
            aside.renameTo(File(folder, "aside"))
        }

        assertEquals(emptyMap<String, ShelfLook>(), store.load())
        assertEquals(listOf(file), movedAside)
        assertTrue(File(folder, "aside").readText().startsWith("audora-shelf-looks 9"))
    }

    @Test
    fun `a foreign file that will not move is never overwritten`() {
        file.writeText("something else\n")
        val stuck = ShelfLookStore(file) { false }

        assertFalse(stuck.save(mapOf("c1" to ShelfLook(ShelfColour.INDIGO))))
        assertEquals("something else\n", file.readText())
    }

    @Test
    fun `every emoji is one fixed glyph`() {
        assertEquals(listOf("📚", "🌙", "🪔", "❤️", "🎓"), ShelfEmoji.entries.map { it.glyph })
    }
}
