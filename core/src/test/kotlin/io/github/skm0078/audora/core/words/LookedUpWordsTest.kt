// File: core/src/test/kotlin/io/github/skm0078/audora/core/words/LookedUpWordsTest.kt
// Purpose: Words looked up survive in their file, newest first, once per book; a book keeps its newest few hundred,
//          a removed book's words go with it, and a file in another format is set aside rather than overwritten.

package io.github.skm0078.audora.core.words

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.Lang
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LookedUpWordsTest {
    private val folder = Files.createTempDirectory("looked-up").toFile()
    private val file = File(folder, LookedUpWordStore.FILE_NAME)
    private val store = LookedUpWordStore(file)
    private val udhr = BookId("udhr")
    private val gita = BookId("gita")

    @After
    fun cleanUp() {
        folder.deleteRecursively()
    }

    private fun word(text: String, at: Long, book: BookId = udhr, lang: Lang = Lang.EN) =
        LookedUpWord(book, text, "meaning of $text", 3, "A sentence with $text in it.", lang, at)

    @Test
    fun `a word comes back as saved, in any of the three languages`() {
        val words = listOf(word("compulsory", 2), word("কর্তব্য", 1, gita, Lang.BN), word("कर्तव्य", 0, gita, Lang.HI))

        assertTrue(store.save(words))

        assertEquals(words, LookedUpWordStore(file).load())
    }

    @Test
    fun `tabs and line breaks in a meaning or sentence never break the file`() {
        val messy = word("dignity", 1).copy(meaning = "worth\tof\nrespect", sentence = "Line one\r\nline two")

        assertTrue(store.save(listOf(messy)))

        val back = store.load().single()
        assertEquals("worth of respect", back.meaning)
        assertEquals("Line one line two", back.sentence)
    }

    @Test
    fun `a long sentence is cut to a glance`() {
        val long = word("arbitrary", 1).copy(sentence = "word ".repeat(200))

        val kept = long.trimmed().sentence

        assertTrue(kept.length <= 300)
        assertTrue(kept.endsWith("…"))
    }

    @Test
    fun `a line that does not parse is skipped`() {
        val good = "udhr\tword\tmeaning\t3\tsentence\tEN\t5"
        val badPage = "udhr\tx\tm\t-1\ts\tEN\t5"
        file.writeText("audora-looked-up-words 1\n$good\nbroken\n$badPage\n")

        assertEquals(listOf("word"), store.load().map { it.word })
    }

    @Test
    fun `a file in another format is set aside, not overwritten`() {
        file.writeText("audora-looked-up-words 9\n")
        val aside = File(folder, "aside")
        val store = LookedUpWordStore(file) { it.renameTo(aside) }

        assertEquals(emptyList<LookedUpWord>(), store.load())
        assertTrue(aside.readText().startsWith("audora-looked-up-words 9"))
    }

    @Test
    fun `a word looked up again moves to the top, once`() {
        val words = LookedUpWords(store)
        words.add(word("dignity", 1))
        words.add(word("compulsory", 2))

        words.add(word("Dignity", 3))

        assertEquals(listOf("Dignity", "compulsory"), words.words.value.map { it.word })
        assertEquals(listOf("Dignity", "compulsory"), LookedUpWordStore(file).load().map { it.word })
    }

    @Test
    fun `the same word in two books is kept for each`() {
        val words = LookedUpWords(store)
        words.add(word("duty", 1))
        words.add(word("duty", 2, gita))

        assertEquals(2, words.words.value.size)
    }

    @Test
    fun `a full book drops its own oldest word, never another book's`() {
        val words = LookedUpWords(store, maxPerBook = 2)
        words.add(word("other", 0, gita))
        words.add(word("one", 1))
        words.add(word("two", 2))

        words.add(word("three", 3))

        assertEquals(listOf("three", "two", "other"), words.words.value.map { it.word })
    }

    @Test
    fun `a removed book's words go with it`() {
        val words = LookedUpWords(store)
        words.add(word("duty", 1, gita))
        words.add(word("dignity", 2))

        assertTrue(words.forget(gita))

        assertEquals(listOf("dignity"), LookedUpWordStore(file).load().map { it.word })
    }

    @Test
    fun `a word that cannot be saved is not shown as saved`() {
        file.writeText("something else\n")
        val words = LookedUpWords(LookedUpWordStore(file) { false })

        assertFalse(words.add(word("dignity", 1)))
        assertEquals(emptyList<LookedUpWord>(), words.words.value)
    }
}
