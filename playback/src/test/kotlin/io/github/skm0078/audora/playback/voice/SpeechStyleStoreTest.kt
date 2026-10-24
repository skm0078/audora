// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/voice/SpeechStyleStoreTest.kt
// Purpose: The speech style: default when missing or unreadable, clamped on set (rate and pitch), saved and read back.

package io.github.skm0078.audora.playback.voice

import io.github.skm0078.audora.core.model.Pace
import io.github.skm0078.audora.core.model.SpeechStyle
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SpeechStyleStoreTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val file by lazy { File(temp.root, "settings/speech-style.properties") }

    @Test
    fun `no file means normal speed and pace`() {
        assertEquals(SpeechStyle(), SpeechStyleStore(file).current)
    }

    @Test
    fun `a style is clamped, applied at once and read back by a new instance`() {
        val store = SpeechStyleStore(file)

        val applied = store.set(SpeechStyle(rate = 1.33f, pace = Pace.RELAXED))

        assertEquals(SpeechStyle(1.35f, Pace.RELAXED), applied)
        assertEquals(applied, store.current)
        assertEquals(applied, SpeechStyleStore(file).current)
    }

    @Test
    fun `a pitch is snapped to its step, kept in range and read back`() {
        val store = SpeechStyleStore(file)

        assertEquals(1.1f, store.set(SpeechStyle(pitch = 1.12f)).pitch)
        assertEquals(1.1f, SpeechStyleStore(file).current.pitch)
        assertEquals(1.25f, store.set(SpeechStyle(pitch = 3f)).pitch)
        assertEquals(0.8f, store.set(SpeechStyle(pitch = 0.1f)).pitch)
    }

    @Test
    fun `a file saved before pitch existed reads as the voice's own pitch`() {
        file.parentFile?.mkdirs()
        file.writeText("rate=1.5\npace=TIGHT\n")

        val read = SpeechStyleStore(file).current

        assertEquals(SpeechStyle.NORMAL_PITCH, read.pitch)
        assertEquals(1.5f, read.rate)
    }

    @Test
    fun `a corrupt file falls back to the default, value by value`() {
        file.parentFile?.mkdirs()
        file.writeText("rate=fast\npace=SIDEWAYS\npitch=high\n")

        assertEquals(SpeechStyle(), SpeechStyleStore(file).current)
    }
}
