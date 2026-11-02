// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/kws/CommandPhrasesTest.kt
// Purpose: "ListenLock, <command>" is found in the recogniser's text however it spells the name, and never without
//          the name, so the book's own "pause" or "next" through the speaker never acts.

package io.github.skm0078.audora.speech.kws

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandPhrasesTest {
    private fun commands(text: String) = CommandPhrases.find(text).map { it.command }

    @Test
    fun `every command is found after the name, joined or as two words`() {
        VoiceCommand.entries.forEach { command ->
            command.words.forEach { word ->
                assertEquals("$command '$word'", listOf(command), commands("ListenLock, $word."))
                assertEquals("$command '$word'", listOf(command), commands("listen lock $word"))
            }
        }
    }

    @Test
    fun `the name as a strong accent or a noisy room writes it still counts`() {
        assertEquals(listOf(VoiceCommand.PLAY), commands("Listen log play"))
        assertEquals(listOf(VoiceCommand.PAUSE), commands("lesson lock pause"))
        assertEquals(listOf(VoiceCommand.NEXT), commands("listen look next"))
        assertEquals(listOf(VoiceCommand.PAUSE), commands("Listen Lock paws"))
    }

    @Test
    fun `a command word without the name never acts`() {
        assertEquals(emptyList<VoiceCommand>(), commands("Stop the play, said the director, and go back."))
        assertEquals(emptyList<VoiceCommand>(), commands("Listen, the lock on the door. Pause."))
        assertEquals(emptyList<VoiceCommand>(), commands("listen lock"))
    }

    @Test
    fun `one word may come between the name and the command, no more`() {
        assertEquals(listOf(VoiceCommand.PAUSE), commands("ListenLock please pause"))
        assertEquals(emptyList<VoiceCommand>(), commands("ListenLock would you please pause"))
    }

    @Test
    fun `go back is one command, not back after go`() {
        assertEquals(listOf(VoiceCommand.BACK), commands("ListenLock go back"))
    }

    @Test
    fun `several commands in one result come in the order said, each ending where its words end`() {
        val found = CommandPhrases.find("ListenLock pause. Listen lock, next")
        assertEquals(listOf(VoiceCommand.PAUSE, VoiceCommand.NEXT), found.map { it.command })
        assertEquals(listOf(1, 4), found.map { it.endWord })
    }

    @Test
    fun `punctuation and case never matter`() {
        assertEquals(listOf("listenlock", "slower"), CommandPhrases.wordsOf("ListenLock... SLOWER!"))
    }
}
