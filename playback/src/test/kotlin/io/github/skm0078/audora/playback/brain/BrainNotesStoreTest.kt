// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/brain/BrainNotesStoreTest.kt
// Purpose: The story brain's notes survive a restart per book, are dropped for another model or prompt, and a
//          half-written line never breaks a book's notes (ADR story-brain).

package io.github.skm0078.audora.playback.brain

import io.github.skm0078.audora.core.model.Feeling
import io.github.skm0078.audora.speech.sherpa.SpeakerGender
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BrainNotesStoreTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val dir by lazy { File(temp.root, "notes") }
    private val tom = StoredNote(3, 1, "“Where?”".hashCode(), "Tom", SpeakerGender.MALE, Feeling.ANGRY)
    private val mira = StoredNote(3, 4, "“Here.”".hashCode(), "Mira", SpeakerGender.FEMALE, null)

    @Test
    fun `notes and read pages come back for the same book only`() {
        val store = BrainNotesStore(dir, "m/1")
        store.append("book-a", 3, listOf(tom, mira))
        store.append("book-a", 4, emptyList())
        val loaded = BrainNotesStore(dir, "m/1").load("book-a")
        assertEquals(listOf(tom, mira), loaded.notes)
        assertEquals(setOf(3, 4), loaded.pages)
        assertEquals(BookNotes(emptyList(), emptySet()), store.load("book-b"))
    }

    @Test
    fun `notes from another model or prompt are dropped`() {
        BrainNotesStore(dir, "m/1").append("book-a", 3, listOf(tom))
        assertEquals(BookNotes(emptyList(), emptySet()), BrainNotesStore(dir, "m/2").load("book-a"))
        assertTrue(dir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `a page counts as read only once its line is written, and a broken line is skipped`() {
        val store = BrainNotesStore(dir, "m/1")
        store.append("book-a", 3, listOf(tom))
        val file = dir.listFiles()!!.single()
        file.appendText("3\t4\t99\tMi")
        val loaded = store.load("book-a")
        assertEquals(listOf(tom), loaded.notes)
        assertEquals(setOf(3), loaded.pages)
    }

    @Test
    fun `the file is named by a hash, never by the book, and a name cannot break a line`() {
        val store = BrainNotesStore(dir, "m/1")
        store.append("My Secret Diary.pdf", 0, listOf(tom.copy(speaker = "Tom\tthe\nelder")))
        val file = dir.listFiles()!!.single()
        assertFalse(file.name.contains("Diary"))
        assertEquals("Tom the elder", store.load("My Secret Diary.pdf").notes.single().speaker)
        store.clear()
        assertFalse(dir.exists())
    }
}
