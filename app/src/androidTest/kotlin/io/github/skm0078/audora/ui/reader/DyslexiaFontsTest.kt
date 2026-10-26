// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/reader/DyslexiaFontsTest.kt
// Purpose: The bundled fonts load and really draw English text (CF-5), Hindi and Bengali still draw in every font
//          choice, and a screenshot of each choice in English, Hindi and Bengali for review in CI's "player-screens".

package io.github.skm0078.audora.ui.reader

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.skm0078.audora.ui.Screens
import io.github.skm0078.audora.ui.theme.AudoraTheme
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DyslexiaFontsTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    /** Each font's laid-out width of each sample line, by font then sample. */
    private val widths = mutableMapOf<Pair<ReadingFont, String>, Float>()

    @Test
    fun everyFontDrawsEnglishInItsOwnShapeAndKeepsHindiAndBengali() {
        rule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(TEST_PHONE_SIZE)) {
                AudoraTheme {
                    Surface {
                        Column(Modifier.testTag(SHEET).padding(16.dp)) {
                            ReadingFont.entries.forEach { font -> Samples(font) }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        Screens.save("text-view-fonts", rule.onNodeWithTag(SHEET).captureToImage().asAndroidBitmap())

        val serif = widths.getValue(ReadingFont.SERIF to ENGLISH)
        listOf(ReadingFont.ATKINSON_HYPERLEGIBLE, ReadingFont.OPEN_DYSLEXIC).forEach { font ->
            // A bundled font that failed to load would fall back to a phone face and measure like it.
            assertNotEquals("$font draws English in its own letters", serif, widths.getValue(font to ENGLISH))
        }
        ReadingFont.entries.forEach { font ->
            listOf(HINDI, BENGALI).forEach { sample ->
                assertTrue("$font draws '$sample'", widths.getValue(font to sample) > 0f)
            }
        }
    }

    @Composable
    private fun Samples(font: ReadingFont) {
        val style = TextStyle(fontFamily = ReadingFonts.familyOf(font))
        Text(font.name, style = MaterialTheme.typography.labelMedium)
        listOf(ENGLISH, HINDI, BENGALI).forEach { sample ->
            Text(
                sample,
                style = MaterialTheme.typography.bodyLarge.merge(style),
                softWrap = false,
                onTextLayout = { layout: TextLayoutResult -> widths[font to sample] = layout.size.width.toFloat() },
            )
        }
        Text(BOLD_SAMPLE, style = MaterialTheme.typography.bodyLarge.merge(style).copy(fontWeight = FontWeight.Bold))
    }

    private companion object {
        val TEST_PHONE_SIZE = DpSize(411.dp, 914.dp)
        const val SHEET = "fonts"
        const val ENGLISH = "Il1 bdpq: the quick brown fox"
        const val HINDI = "सभी मनुष्य स्वतंत्र हैं"
        const val BENGALI = "সকল মানুষ স্বাধীন"
        const val BOLD_SAMPLE = "Bold word starts"
    }
}
