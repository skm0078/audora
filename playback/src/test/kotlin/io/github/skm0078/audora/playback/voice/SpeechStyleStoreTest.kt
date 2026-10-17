// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/voice/SpeechStyleStoreTest.kt
// Purpose: The speech style: default when missing or unreadable, clamped on set, saved and read back.

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
    fun `a corrupt file falls back to the default, value by value`() {
        file.parentFile?.mkdirs()
        file.writeText("rate=fast\npace=SIDEWAYS\n")

        assertEquals(SpeechStyle(), SpeechStyleStore(file).current)
    }
}
