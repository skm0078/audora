// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/studio/StudioSentenceRendererTest.kt
// Purpose: Playback plays studio audio when its key matches and falls back to the live voice otherwise.

package io.github.skm0078.audora.playback.studio

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.SpeechStyle
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.playback.pcm
import io.github.skm0078.audora.playback.queue.SentenceRenderer
import io.github.skm0078.audora.playback.unit
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class StudioSentenceRendererTest {
    private val root: File = Files.createTempDirectory("studio").toFile()
    private val store = StudioStore(root)
    private val codec = LosslessPcm(256)
    private val key = StudioKey(BookId("book"), StudioSignature(1, 0L, SpeechStyle(), emptyMap(), fixes = ""))
    private var liveCalls = 0
    private val live = SentenceRenderer {
        liveCalls++
        Outcome.Success(pcm(10, fill = 1))
    }

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    private fun studioAudio() {
        store.write(key, StudioPage(0, listOf(StudioClip.of(unit(0, 0), pcm(50, fill = 7), codec))))
    }

    @Test
    fun `plays studio audio for a matching sentence`() {
        studioAudio()
        val rendered = StudioSentenceRenderer(live, store, { key }).render(unit(0, 0))
        assertArrayEquals(pcm(50, fill = 7).samples, (rendered as Outcome.Success).value.samples)
        assertEquals(0, liveCalls)
    }

    @Test
    fun `renders live when the sentence, the signature or the book differ`() {
        studioAudio()
        val other = key.copy(signature = key.signature.copy(style = SpeechStyle(rate = 1.3f)))
        StudioSentenceRenderer(live, store, { key }).render(unit(0, 1))
        StudioSentenceRenderer(live, store, { other }).render(unit(0, 0))
        StudioSentenceRenderer(live, store, { null }).render(unit(0, 0))
        StudioSentenceRenderer(live, store, { key }).render(unit(0, 0).copy(text = "Changed."))
        assertEquals(4, liveCalls)
    }
}
