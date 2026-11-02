// File: app/src/test/kotlin/io/github/skm0078/audora/ui/reader/VoiceCommandsStatusTest.kt
// Purpose: The reader's voice-command line says "Listening" only while the microphone is really open, and says why
//          listening stopped otherwise, so a listener never talks to a closed microphone unawares.

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.speech.kws.ListenEvents
import io.github.skm0078.audora.speech.kws.ListenFailure
import io.github.skm0078.audora.speech.kws.VoiceCommand
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceCommandsStatusTest {
    private val reports = mutableListOf<VoiceCommandsStatus>()

    @Test
    fun `listening shows only between the microphone opening and closing, and commands still arrive`() = runTest {
        val heard = mutableListOf<VoiceCommand>()
        val opened = mutableListOf<Boolean>()
        val listen = reportingListen({ events ->
            events.onMicrophone(true)
            events.onCommand(VoiceCommand.PLAY)
            events.onMicrophone(false)
            Outcome.Success(Unit)
        }) { reports += it }

        listen(ListenEvents(onMicrophone = { opened += it }) { heard += it })

        assertEquals(listOf(VoiceCommandsStatus(listening = true), VoiceCommandsStatus(listening = false)), reports)
        assertEquals(listOf(VoiceCommand.PLAY), heard)
        assertEquals("the caller's own microphone events still arrive", listOf(true, false), opened)
    }

    @Test
    fun `a listener that cannot start says why`() = runTest {
        val listen = reportingListen({ Outcome.Failure(ListenFailure.NOT_AVAILABLE) }) { reports += it }

        listen(ListenEvents {})

        assertEquals(listOf(VoiceCommandsStatus(failure = ListenFailure.NOT_AVAILABLE)), reports)
    }

    @Test
    fun `every state has its own line`() {
        val lines = listOf(
            VoiceCommandsStatus(),
            VoiceCommandsStatus(listening = true),
        ) + ListenFailure.entries.map { VoiceCommandsStatus(failure = it) }

        assertEquals(R.string.reader_commands_listening, VoiceCommandsStatus(listening = true).text)
        assertEquals(R.string.reader_commands_starting, VoiceCommandsStatus().text)
        val unavailable = VoiceCommandsStatus(failure = ListenFailure.NOT_AVAILABLE)
        assertEquals(R.string.reader_commands_not_available, unavailable.text)
        assertEquals("no two states share a line", lines.size, lines.map { it.text }.toSet().size)
    }
}
