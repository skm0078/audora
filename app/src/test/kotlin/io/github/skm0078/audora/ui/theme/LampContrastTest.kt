// File: app/src/test/kotlin/io/github/skm0078/audora/ui/theme/LampContrastTest.kt
// Purpose: The lamp palette keeps WCAG AA contrast in light, dark and true black: 4.5:1 for text, 3:1 for bars.

package io.github.skm0078.audora.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

private typealias ColorPair = (ColorScheme) -> Pair<Color, Color>

class LampContrastTest {
    private val schemes = mapOf(
        "light" to LampLightColors,
        "dark" to LampDarkColors,
        "true black" to LampDarkColors.trueBlack(),
    )

    /** Text the screens draw, on what they draw it on. */
    private val textPairs: List<Pair<String, ColorPair>> = listOf(
        "onSurface on surface" to { it.onSurface to it.surface },
        "onSurfaceVariant on surface" to { it.onSurfaceVariant to it.surface },
        "onSurfaceVariant on surfaceVariant" to { it.onSurfaceVariant to it.surfaceVariant },
        "onSurfaceVariant on surfaceContainerHighest" to { it.onSurfaceVariant to it.surfaceContainerHighest },
        "onPrimary on primary" to { it.onPrimary to it.primary },
        "spoken word: onPrimaryContainer on primaryContainer" to { it.onPrimaryContainer to it.primaryContainer },
        "replay card: onSecondaryContainer on secondaryContainer" to {
            it.onSecondaryContainer to it.secondaryContainer
        },
        "mini player: inverseOnSurface on inverseSurface" to { it.inverseOnSurface to it.inverseSurface },
        "mini player button: inverseSurface on inversePrimary" to { it.inverseSurface to it.inversePrimary },
        "links and text buttons: primary on surface" to { it.primary to it.surface },
    )

    /** Bars and marks that carry meaning without text (progress, chapter ticks, outlines). */
    private val graphicPairs: List<Pair<String, ColorPair>> = listOf(
        "progress: primary on surfaceVariant" to { it.primary to it.surfaceVariant },
        "chapter ticks: onSurfaceVariant on surfaceVariant" to { it.onSurfaceVariant to it.surfaceVariant },
        "outline on surface" to { it.outline to it.surface },
    )

    @Test
    fun textMeetsAa() = check(textPairs, TEXT_MIN)

    @Test
    fun barsAndMarksMeetAa() = check(graphicPairs, GRAPHIC_MIN)

    private fun check(pairs: List<Pair<String, ColorPair>>, min: Float) {
        schemes.forEach { (scheme, colors) ->
            pairs.forEach { (name, pick) ->
                val (front, back) = pick(colors)
                val ratio = contrast(front, back)
                assertTrue("$scheme, $name: $ratio < $min", ratio >= min)
            }
        }
    }

    private fun contrast(a: Color, b: Color): Float {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + FLARE) / (dark + FLARE)
    }

    private companion object {
        const val TEXT_MIN = 4.5f
        const val GRAPHIC_MIN = 3f

        /** WCAG's allowance for ambient light in the contrast formula. */
        const val FLARE = 0.05f
    }
}
