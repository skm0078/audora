// File: core/src/test/kotlin/io/github/skm0078/audora/core/quiz/QuizProgressStoreTest.kt
// Purpose: Quiz progress round-trips, and a foreign file is set aside or, if it will not move, never overwritten.

package io.github.skm0078.audora.core.quiz

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizProgressStoreTest {
    private val dir = Files.createTempDirectory("quiz").toFile()
    private val file = File(dir, QuizProgressStore.FILE_NAME)

    @After
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun `progress round-trips and drops cards of deleted highlights`() {
        val store = QuizProgressStore(file)
        assertTrue(store.save(mapOf(1L to CardProgress(2, 100L), 2L to CardProgress(0, 5L)), known = setOf(1L)))

        assertEquals(mapOf(1L to CardProgress(2, 100L)), QuizProgressStore(file).load())
    }

    @Test
    fun `a foreign file is set aside and the next save starts fresh`() {
        file.writeText("another app's format\n")
        val store = QuizProgressStore(file)

        assertEquals(emptyMap<Long, CardProgress>(), store.load())
        assertTrue(store.save(mapOf(3L to CardProgress(1, 7L)), known = setOf(3L)))

        val aside = dir.listFiles().orEmpty().filter { it != file }
        assertEquals(1, aside.size)
        assertEquals("another app's format\n", aside.single().readText())
    }

    @Test
    fun `a foreign file that will not move is never overwritten`() {
        file.writeText("another app's format\n")
        val store = QuizProgressStore(file, moveAside = { false })

        assertEquals(emptyMap<Long, CardProgress>(), store.load())
        assertFalse(store.save(mapOf(3L to CardProgress(1, 7L)), known = setOf(3L)))
        assertEquals("another app's format\n", file.readText())
    }
}
