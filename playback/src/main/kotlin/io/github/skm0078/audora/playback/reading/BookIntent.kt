// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/reading/BookIntent.kt
// Purpose: The open book's intent, told from the pages read so far and then kept (ADR-056).

package io.github.skm0078.audora.playback.reading

import io.github.skm0078.audora.core.config.DeliveryConfig
import io.github.skm0078.audora.core.config.SpeakabilityConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.ContentIntent
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.pdf.document.IntentClassifier
import io.github.skm0078.audora.pdf.document.IntentSignals

/**
 * Fed each page as it is extracted (before its sentences render, like [QuoteCarry]), so the first page read already
 * sets the book's pace: a book needs no extra page reads to be told, scans and EPUBs included. The intent may change
 * while fewer than `DeliveryConfig.settleWords` words were read, then it is kept for the session, so one chapter of
 * dialogue in a report never turns the rest into a story. A page counts once, however often it is read again.
 *
 * Thread-safe: the session feeds pages on the main thread, the renderer reads [current] on its worker.
 */
class BookIntent(
    private val config: DeliveryConfig = DeliveryConfig(),
    private val readableFrom: Float = SpeakabilityConfig().skipBelow,
    private val onChange: (ContentIntent) -> Unit = {},
) {
    private val classifier = IntentClassifier(config)
    private val lock = Any()
    private val seen = HashSet<Int>()
    private var signals = IntentSignals()
    private var settled = false

    /** The intent told so far; [ContentIntent.GENERAL] (today's reading) until enough words were read. */
    @Volatile
    var current: ContentIntent = ContentIntent.GENERAL
        private set

    /** Whether enough was read to tell the intent; until then [current] is only the plain default. */
    @Volatile
    var told: Boolean = false
        private set

    fun page(units: List<ReadingUnit>) {
        val page = units.firstOrNull()?.page ?: return
        val changed = synchronized(lock) { count(page, units) } ?: return
        AudoraLog.i(TAG, "intent=$current page=$page words=${signals.words} settled=$settled")
        if (changed) onChange(current)
    }

    /** Under [lock]: adds a page not counted yet; whether the intent changed, or null when nothing was told. */
    private fun count(page: Int, units: List<ReadingUnit>): Boolean? {
        if (settled || !seen.add(page)) return null
        signals += IntentSignals.of(units, readableFrom, config)
        settled = signals.words >= config.settleWords
        val now = classifier.classify(signals)
        if (now != null) told = true
        return now?.let { (it != current).also { _ -> current = it } }
    }

    /** A new book opened. */
    fun clear() {
        synchronized(lock) {
            seen.clear()
            signals = IntentSignals()
            settled = false
            told = false
            current = ContentIntent.GENERAL
        }
    }

    private companion object {
        const val TAG = "Playback.Intent"
    }
}
