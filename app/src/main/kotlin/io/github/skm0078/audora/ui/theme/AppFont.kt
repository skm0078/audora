// File: app/src/main/kotlin/io/github/skm0078/audora/ui/theme/AppFont.kt
// Purpose: The app's own type, Anek, in English, Hindi and Bengali: one family whose letters change with the script.

package io.github.skm0078.audora.ui.theme

import android.content.Context
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontStyle
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.logging.AudoraLog
import java.io.IOException
import android.graphics.fonts.FontFamily as PlatformFontFamily
import androidx.compose.ui.text.font.FontStyle as ComposeFontStyle

/**
 * Anek (Ek Type, SIL Open Font License 1.1) is one design drawn for Latin, Devanagari and Bengali, so a Hindi title
 * next to an English one looks like the same voice. The three scripts ship as separate files, trimmed to their own
 * letters (about 0.74 MB in the APK for three weights), and are joined into one typeface per weight with Android's
 * custom fallback (API 29+): Latin first, then Devanagari, then Bengali, then the phone's own fonts for anything else
 * (Tamil, emoji, symbols). Compose's own font list cannot do this: it picks a file by weight, not by script.
 *
 * Book text keeps its own fonts (the reader's [io.github.skm0078.audora.ui.reader.ReadingFonts]); this is the app's
 * UI type only. Weights are 400, 500 and 600; a style asking for bold gets 600, the heaviest drawn, never a fake.
 */
object AppFont {
    /** One font resource per script for each weight, in fallback order. */
    internal enum class Weight(val weight: FontWeight, val scripts: List<Int>) {
        REGULAR(
            FontWeight.Normal,
            listOf(R.font.anek_latin_regular, R.font.anek_devanagari_regular, R.font.anek_bangla_regular),
        ),
        MEDIUM(
            FontWeight.Medium,
            listOf(R.font.anek_latin_medium, R.font.anek_devanagari_medium, R.font.anek_bangla_medium),
        ),
        SEMIBOLD(
            FontWeight.SemiBold,
            listOf(R.font.anek_latin_semibold, R.font.anek_devanagari_semibold, R.font.anek_bangla_semibold),
        ),
    }

    /** The fonts are static files: no variable axes to set. */
    private val NO_AXES = FontVariation.Settings()

    /** Bold and heavier resolve to semibold: Compose picks the closest weight in the family instead of faking one. */
    val family: FontFamily = FontFamily(Weight.entries.map(::AnekFont))

    private const val TAG = "AppFont"

    /** One weight, loaded once per process by Compose's font cache; a missing file falls back to the phone's sans. */
    internal class AnekFont(val drawn: Weight) : AndroidFont(FontLoadingStrategy.Blocking, Loader, NO_AXES) {
        override val weight: FontWeight = drawn.weight
        override val style: ComposeFontStyle = ComposeFontStyle.Normal

        override fun equals(other: Any?): Boolean = other is AnekFont && other.drawn == drawn

        override fun hashCode(): Int = drawn.hashCode()

        override fun toString(): String = "AnekFont(${drawn.name})"
    }

    private object Loader : AndroidFont.TypefaceLoader {
        override fun loadBlocking(context: Context, font: AndroidFont): Typeface? =
            (font as? AnekFont)?.let { typeface(context, it.drawn) }

        override suspend fun awaitLoad(context: Context, font: AndroidFont): Typeface? = loadBlocking(context, font)
    }

    internal fun typeface(context: Context, drawn: Weight): Typeface? = try {
        val families = drawn.scripts.map { id ->
            PlatformFontFamily.Builder(Font.Builder(context.resources, id).build()).build()
        }
        Typeface.CustomFallbackBuilder(families.first())
            .apply { families.drop(1).forEach { addCustomFallback(it) } }
            .setSystemFallback("sans-serif")
            .setStyle(FontStyle(drawn.weight.weight, FontStyle.FONT_SLANT_UPRIGHT))
            .build()
    } catch (e: IOException) {
        // Compose then uses the phone's sans for this weight: plainer, never blank.
        AudoraLog.w(TAG, "Could not load the app font ${drawn.name}", e)
        null
    }
}
