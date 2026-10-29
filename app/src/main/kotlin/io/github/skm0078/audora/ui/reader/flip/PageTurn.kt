// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/flip/PageTurn.kt
// Purpose: How a page turns, in the page view and the text view alike (ADR 2026-10-29-page-curl): a curl, a slide or
//          no animation, how quickly, and how deep the curl's shadow is. Part of the reading look.

package io.github.skm0078.audora.ui.reader.flip

import io.github.skm0078.audora.core.config.PageFlipConfig

/** The turn's animation. Saved by name, so the order here can change. */
enum class TurnStyle { CURL, SLIDE, NONE }

/** How long a turn by hand or by an arrow takes, as a factor on the style's own time. */
enum class TurnSpeed(val factor: Float) {
    QUICK(QUICK_FACTOR),
    NORMAL(1f),
    SLOW(SLOW_FACTOR),
}

/** How dark the curl's shadows are at the fold, from 0 (none) to 1 (black). */
enum class TurnShadow(val strength: Float) {
    LIGHT(LIGHT_SHADOW),
    NORMAL(NORMAL_SHADOW),
    STRONG(STRONG_SHADOW),
}

/**
 * @property style curl by default: a turn that looks like paper is the e-reader feel listeners asked for.
 * @property speed applies to turns by hand and by the arrows; a turn made by reading aloud keeps its own short time.
 * @property shadow the curl's shading; a slide has none.
 */
data class PageTurn(
    val style: TurnStyle = TurnStyle.CURL,
    val speed: TurnSpeed = TurnSpeed.NORMAL,
    val shadow: TurnShadow = TurnShadow.NORMAL,
) {
    /** A whole turn by hand, in milliseconds; 0 for no animation. */
    fun handMs(config: PageFlipConfig): Int = when (style) {
        TurnStyle.CURL -> (config.curlMs * speed.factor).toInt()
        TurnStyle.SLIDE -> (config.slideMs * speed.factor).toInt()
        TurnStyle.NONE -> 0
    }

    /** A turn made by reading aloud, in milliseconds; 0 for no animation. */
    fun readingMs(config: PageFlipConfig): Int = when (style) {
        TurnStyle.CURL -> config.readingTurnMs
        TurnStyle.SLIDE -> config.slideMs
        TurnStyle.NONE -> 0
    }
}

private const val QUICK_FACTOR = 0.6f
private const val SLOW_FACTOR = 1.6f
private const val LIGHT_SHADOW = 0.14f
private const val NORMAL_SHADOW = 0.26f
private const val STRONG_SHADOW = 0.42f
