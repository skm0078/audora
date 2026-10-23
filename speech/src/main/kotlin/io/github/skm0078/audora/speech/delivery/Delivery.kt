// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/delivery/Delivery.kt
// Purpose: How one sentence is read: speed, pauses and loudness, from the book's intent and its own feeling (ADR-056).

package io.github.skm0078.audora.speech.delivery

import io.github.skm0078.audora.core.config.DeliveryConfig
import io.github.skm0078.audora.core.model.ContentIntent
import io.github.skm0078.audora.core.model.Feeling
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.UnitKind

/**
 * One sentence's delivery, on top of the listener's speed and pace: multipliers of the voice's [rate], the silence
 * after the sentence ([pauseScale]) and inside it ([clauseScale]), and [gainDb] of loudness. [PLAIN] changes nothing.
 */
data class Delivery(
    val rate: Float = 1f,
    val pauseScale: Float = 1f,
    val clauseScale: Float = 1f,
    val gainDb: Float = 0f,
    val feeling: Feeling = Feeling.NEUTRAL,
) {
    companion object {
        val PLAIN = Delivery()
    }
}

/**
 * Plans each sentence's [Delivery] from the book's [ContentIntent] (its profile in [DeliveryConfig]) and, where the
 * intent allows it, the sentence's own [Feeling] ([SentenceFeelings]) scaled by the profile's strength. Only prose is
 * coloured: headings, tables, captions, footnotes and equations keep the profile's pace with no feeling. Pure.
 */
class DeliveryPlanner(private val config: DeliveryConfig = DeliveryConfig()) {
    /** [quoted]: the sentence holds quoted speech, or starts inside a quote an earlier sentence opened. */
    fun plan(unit: ReadingUnit, intent: ContentIntent, quoted: Boolean): Delivery {
        val profile = config.profile(intent)
        val prose = unit.kind == UnitKind.BODY || unit.kind == UnitKind.LIST_ITEM || unit.kind == UnitKind.ASIDE
        val feeling = if (prose && intent.feelings && profile.feelingScale > 0f) {
            SentenceFeelings.of(unit.text)
        } else {
            Feeling.NEUTRAL
        }
        val effect = config.effect(feeling)
        val strength = profile.feelingScale
        val baseRate = if (quoted && prose) profile.dialogueRate else profile.rate
        val headingPause = if (unit.kind == UnitKind.HEADING) profile.headingPauseScale else 1f
        return Delivery(
            rate = (baseRate * scaled(effect.rate, strength)).coerceAtLeast(config.minRate),
            pauseScale = profile.pauseScale * headingPause * scaled(effect.pauseScale, strength),
            clauseScale = profile.clauseScale,
            gainDb = (effect.gainDb * strength).coerceAtMost(config.maxGainDb),
            feeling = feeling,
        )
    }

    /** A multiplier [factor] at [strength]: 0 is 1 (no change), 1 is [factor], 2 doubles its distance from 1. */
    private fun scaled(factor: Float, strength: Float): Float = 1f + (factor - 1f) * strength
}
