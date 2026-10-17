// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/ocr/OcrLineRetryTest.kt
// Purpose: Unsure lines are read again with the other models and replaced only by a clearly better read (RI-3).

package io.github.skm0078.audora.pdf.ocr

import io.github.skm0078.audora.core.config.OcrConfig
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.pdf.document.PageImage
import io.github.skm0078.audora.pdf.quality.OcrError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class OcrLineRetryTest {
    /** Reads every image as one word, per model: what it says and how sure it is. */
    private class FakeEngine(private val reads: Map<Lang, Pair<String, Int>>) : OcrEngine {
        val calls = ArrayList<Pair<Set<Lang>, PageImage>>()
        override val cacheTag = "fake"

        override fun installedLangs() = reads.keys

        override fun recognize(image: PageImage, langs: Set<Lang>): Outcome<RecognizedPage, OcrError> {
            calls.add(langs to image)
            val (text, conf) = reads.getValue(langs.single())
            val word = RecognizedWord(text, 1, 1, image.width - 1, image.height - 1, conf)
            return Outcome.Success(RecognizedPage(listOf(RecognizedLine(listOf(word), 0)), image.width, image.height))
        }
    }

    private val image = PageImage(0, WIDTH, HEIGHT, DPI, ByteArray(WIDTH * HEIGHT))

    /** A Hindi page: a confident line, then a line of noise at [noisy] confidence. */
    private fun page(noisy: Int = 20) = RecognizedPage(
        listOf(
            RecognizedLine(listOf(RecognizedWord("मानव", 10, 10, 60, 20, 92)), 0),
            RecognizedLine(listOf(RecognizedWord("#फ08", 10, 40, 60, 50, noisy)), 1, size = NOISY_SIZE),
        ),
        WIDTH,
        HEIGHT,
    )

    @Test
    fun `a noisy line is read with the other models and the clearly better read replaces it`() {
        val engine = FakeEngine(mapOf(Lang.HI to ("x" to 0), Lang.EN to ("Audora" to 91), Lang.BN to ("অ" to 30)))
        val improved = OcrLineRetry(engine, OcrConfig(), "test").improve(page(), image, setOf(Lang.HI))
        assertEquals(listOf("मानव", "Audora"), improved.lines.map { it.words.single().text })
        assertEquals(listOf(1), improved.lines.map { it.paragraph }.drop(1))
        assertEquals(NOISY_SIZE, improved.lines[1].size)
        assertEquals(setOf(setOf(Lang.EN), setOf(Lang.BN)), engine.calls.map { it.first }.toSet())
        // The line alone, padded by half its height, and the words put back where they are on the page.
        val crop = engine.calls.first().second
        assertEquals(50 + 10 to 10 + 10, crop.width to crop.height)
        val word = improved.lines[1].words.single()
        assertEquals(listOf(6, 36, 64, 54), listOf(word.left, word.top, word.right, word.bottom))
    }

    @Test
    fun `a read that is not clearly better, a confident page, or no other model leaves the page as read`() {
        val weak = FakeEngine(mapOf(Lang.HI to ("x" to 0), Lang.EN to ("Audora" to 25)))
        val page = page()
        assertSame(page, OcrLineRetry(weak, OcrConfig(), "test").improve(page, image, setOf(Lang.HI)))

        val strong = FakeEngine(mapOf(Lang.HI to ("x" to 0), Lang.EN to ("Audora" to 91)))
        val clean = page(noisy = 88)
        assertSame(clean, OcrLineRetry(strong, OcrConfig(), "test").improve(clean, image, setOf(Lang.HI)))
        assertEquals(0, strong.calls.size)

        val alone = FakeEngine(mapOf(Lang.HI to ("x" to 0)))
        assertSame(page, OcrLineRetry(alone, OcrConfig(), "test").improve(page, image, setOf(Lang.HI)))
    }

    @Test
    fun `retries stop at the line limit and the time budget, and can be switched off`() {
        val reads = mapOf(Lang.HI to ("x" to 0), Lang.EN to ("Audora" to 91))
        val off = FakeEngine(reads)
        OcrLineRetry(off, OcrConfig(retryLineBelow = 0), "test").improve(page(), image, setOf(Lang.HI))
        assertEquals(0, off.calls.size)

        var now = 0L
        val slow = FakeEngine(reads)
        val retry = OcrLineRetry(slow, OcrConfig(retryBudgetMs = 0), "test", nanoTime = { now++ })
        assertEquals(
            page().lines.map {
                it.words.single().text
            },
            retry.improve(page(), image, setOf(Lang.HI)).lines.map { it.words.single().text },
        )
        assertEquals(0, slow.calls.size)
    }

    private companion object {
        const val WIDTH = 100
        const val HEIGHT = 80
        const val DPI = 300
        const val NOISY_SIZE = 14
    }
}
