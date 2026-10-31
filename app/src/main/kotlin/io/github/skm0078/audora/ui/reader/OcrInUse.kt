// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/OcrInUse.kt
// Purpose: Knows when the reader's open book is really reading a page with text recognition (OCR), so the slow-page
//          notice only names OCR then (ADR 2026-10-31-honest-page-wait).

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.pdf.ocr.OcrMargins
import io.github.skm0078.audora.pdf.ocr.OcrText
import io.github.skm0078.audora.pdf.quality.OcrError
import io.github.skm0078.audora.pdf.quality.PageOcr
import io.github.skm0078.audora.pdf.quality.PageOcrFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One per open book in the reader. Every OCR call the book makes goes through [tracking]'s wrapper, which marks
 * [recognizing] for as long as the call lasts, including the time it waits for its turn behind another screen's OCR
 * (the player reading ahead): either way the page the listener waits for is waiting on text recognition.
 *
 * Reads from the OCR cache ([PageOcr.cachedOnly]) are not marked: they never recognise anything, and search and the
 * pages drawn ahead use them all the time.
 *
 * Thread-safe: OCR runs on the book's IO thread while the screen reads [recognizing].
 */
class OcrInUse(private val clock: () -> Long = System::nanoTime) {
    private val running = MutableStateFlow(false)
    private var calls = 0
    private var spentNanos = 0L

    /** True while at least one OCR call for this book is running or waiting for its turn. */
    val recognizing: StateFlow<Boolean> = running.asStateFlow()

    /** The time spent in OCR calls so far, in milliseconds: the slow-page log subtracts it from a page's wait. */
    val spentMs: Long
        get() = synchronized(this) { spentNanos } / NANOS_PER_MS

    /** [factory]'s OCR with every recognising call marked. */
    fun tracking(factory: PageOcrFactory): PageOcrFactory = PageOcrFactory { pages -> Tracked(factory.create(pages)) }

    /** Runs [block] marked as recognising. Counted, not a flag: two calls may overlap (a page and a saved place). */
    fun <T> during(block: () -> T): T {
        val started = clock()
        synchronized(this) {
            calls++
            running.value = true
        }
        try {
            return block()
        } finally {
            synchronized(this) {
                calls--
                spentNanos += clock() - started
                running.value = calls > 0
            }
        }
    }

    private inner class Tracked(private val inner: PageOcr) : PageOcr {
        override fun recognize(pageIndex: Int, langs: Set<Lang>): Outcome<OcrText, OcrError> =
            during { inner.recognize(pageIndex, langs) }

        override fun detectLangs(pageIndex: Int): Outcome<Set<Lang>, OcrError> = during { inner.detectLangs(pageIndex) }

        override fun margins(
            pageIndex: Int,
            langs: Set<Lang>,
            quarterTurns: Int,
            share: Float,
        ): Outcome<OcrMargins, OcrError> = during { inner.margins(pageIndex, langs, quarterTurns, share) }

        override fun cachedOnly(): PageOcr? = inner.cachedOnly()
    }

    private companion object {
        const val NANOS_PER_MS = 1_000_000L
    }
}
