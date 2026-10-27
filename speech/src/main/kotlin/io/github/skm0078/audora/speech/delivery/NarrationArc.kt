// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/delivery/NarrationArc.kt
// Purpose: The narrator's natural rise and fall across a paragraph, on top of each sentence's delivery (Q-3).

package io.github.skm0078.audora.speech.delivery

import io.github.skm0078.audora.core.config.NarrationConfig
import io.github.skm0078.audora.core.model.ParagraphPlace
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.UnitKind
import kotlin.math.pow

/**
 * Shapes a narrated sentence's [Delivery] by its place in the paragraph (ADR 2026-10-26-natural-narration). A person
 * reading aloud starts a paragraph a little higher and settles lower by its end, slows for the first sentence of a
 * section and for a paragraph's last, and takes a breath at the commas of a long sentence; and no two sentences come
 * out quite alike. The voices read every sentence on its own with the same tune, which is what made the narration
 * flat (Q-3), legacy-font Hindi and Bengali included.
 *
 * Only narration is shaped: prose that is not quoted speech (a character's line keeps its own delivery), never a
 * heading, table, caption or footnote. A sentence with no known place gets only its own small variation. Pure: the
 * variation comes from the sentence's text, so a sentence sounds the same each time it is read.
 *
 * The voices cannot stress one word inside a sentence (no engine takes emphasis marks), so a key word's stress is not
 * attempted: splitting the sentence to raise one word would cost the words around it their intonation.
 */
class NarrationArc(private val config: NarrationConfig = NarrationConfig()) {
    fun shape(delivery: Delivery, unit: ReadingUnit, place: ParagraphPlace?, quoted: Boolean): Delivery {
        if (unit.kind !in NARRATION || quoted) return delivery
        val semitones = arcSemitones(place) + jitter(unit.text, SEMITONE_SALT) * config.jitterSemitones
        val rate = placeRate(place) * (1f + jitter(unit.text, RATE_SALT) * config.jitterRate)
        val long = WORD.findAll(unit.text).count() >= config.longSentenceWords
        return delivery.copy(
            rate = delivery.rate * rate,
            pitch = delivery.pitch * SEMITONE.pow(semitones),
            clauseScale = delivery.clauseScale * if (long) config.longClauseScale else 1f,
        )
    }

    /** From the paragraph's start pitch down to its end pitch, in even steps; a lone sentence takes the middle. */
    private fun arcSemitones(place: ParagraphPlace?): Float = when {
        place == null -> 0f
        place.size == 1 -> (config.startSemitones + config.endSemitones) / 2
        else -> config.startSemitones + (config.endSemitones - config.startSemitones) * place.index / (place.size - 1)
    }

    private fun placeRate(place: ParagraphPlace?): Float = when {
        place == null -> 1f
        place.first && place.afterHeading -> config.sectionStartRate
        place.first -> config.paragraphStartRate
        place.last -> config.paragraphEndRate
        else -> 1f
    }

    /** A steady value in -1..1 from [text], different for each [salt]: the sentence's own small variation. */
    private fun jitter(text: String, salt: Int): Float {
        var h = text.hashCode() * HASH_MULTIPLIER + salt
        h = h xor (h ushr HASH_SHIFT)
        return (h and HASH_MASK) / HASH_MASK.toFloat() * 2f - 1f
    }

    private companion object {
        val NARRATION = setOf(UnitKind.BODY, UnitKind.LIST_ITEM, UnitKind.ASIDE)
        val WORD = Regex("[\\p{L}\\p{M}\\p{Nd}]+")

        /** One equal-tempered semitone as a frequency ratio. */
        val SEMITONE = 2f.pow(1f / 12)
        const val SEMITONE_SALT = 0x51ED
        const val RATE_SALT = 0x7A7E
        const val HASH_MULTIPLIER = 31
        const val HASH_SHIFT = 16
        const val HASH_MASK = 0xFFFF
    }
}
