// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/delivery/FootnoteVoice.kt
// Purpose: Reads a footnote a little quicker and softer than the text (Q-4), as a person reads an aside.

package io.github.skm0078.audora.speech.delivery

import io.github.skm0078.audora.core.config.FootnoteVoiceConfig
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.UnitKind

/**
 * Applies to footnotes whatever the "Read to suit the book" switch says: it marks where the page's text stops and its
 * notes start, which a listener can't see. Every other unit keeps its delivery as it is. Pure.
 */
class FootnoteVoice(private val config: FootnoteVoiceConfig = FootnoteVoiceConfig()) {
    fun shape(unit: ReadingUnit, delivery: Delivery): Delivery = if (unit.kind != UnitKind.FOOTNOTE) {
        delivery
    } else {
        delivery.copy(rate = delivery.rate * config.rate, gainDb = delivery.gainDb + config.gainDb)
    }
}
