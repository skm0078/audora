// File: app/src/test/kotlin/io/github/skm0078/audora/ui/theme/AppFontTest.kt
// Purpose: Every text style of the theme uses the app font, which has three real weights, each with its Latin,
//          Devanagari and Bengali files in fallback order.

package io.github.skm0078.audora.ui.theme

import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontWeight
import io.github.skm0078.audora.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppFontTest {
    @Test
    fun `every text style of the theme is set in the app font`() {
        val styles = with(AudoraTypography) {
            listOf(
                displayLarge,
                displayMedium,
                displaySmall,
                headlineLarge,
                headlineMedium,
                headlineSmall,
                titleLarge,
                titleMedium,
                titleSmall,
                bodyLarge,
                bodyMedium,
                bodySmall,
                labelLarge,
                labelMedium,
                labelSmall,
            )
        }
        styles.forEachIndexed { index, style -> assertEquals("style $index", AppFont.family, style.fontFamily) }
    }

    @Test
    fun `no style asks for a weight heavier than the heaviest file, so bold is never faked`() {
        val heaviest = AppFont.Weight.entries.maxOf { it.weight.weight }
        with(AudoraTypography) {
            listOf(headlineLarge, titleLarge, titleMedium, labelLarge).forEach { style ->
                assertTrue("${style.fontWeight}", (style.fontWeight ?: FontWeight.Normal).weight <= heaviest)
            }
        }
    }

    @Test
    fun `the family has regular, medium and semibold`() {
        val family = AppFont.family as FontListFontFamily
        val weights = family.fonts.map { it.weight }.toSet()
        assertEquals(setOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold), weights)
    }

    @Test
    fun `each weight joins Latin, then Devanagari, then Bengali`() {
        assertEquals(
            listOf(R.font.anek_latin_regular, R.font.anek_devanagari_regular, R.font.anek_bangla_regular),
            AppFont.Weight.REGULAR.scripts,
        )
        assertEquals(
            listOf(R.font.anek_latin_medium, R.font.anek_devanagari_medium, R.font.anek_bangla_medium),
            AppFont.Weight.MEDIUM.scripts,
        )
        assertEquals(
            listOf(R.font.anek_latin_semibold, R.font.anek_devanagari_semibold, R.font.anek_bangla_semibold),
            AppFont.Weight.SEMIBOLD.scripts,
        )
    }

    @Test
    fun `fonts of the same weight are equal, so Compose loads each once`() {
        val first = AppFont.AnekFont(AppFont.Weight.MEDIUM)
        assertEquals(first, AppFont.AnekFont(AppFont.Weight.MEDIUM))
        assertEquals(first.hashCode(), AppFont.AnekFont(AppFont.Weight.MEDIUM).hashCode())
    }
}
