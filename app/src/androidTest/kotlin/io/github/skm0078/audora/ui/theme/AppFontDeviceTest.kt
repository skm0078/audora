// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/theme/AppFontDeviceTest.kt
// Purpose: The theme's text really draws in the app font in English, Hindi and Bengali at every weight, and a
//          screenshot of the type scale in all three for review in CI's "player-screens".

package io.github.skm0078.audora.ui.theme

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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.skm0078.audora.ui.Screens
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppFontDeviceTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    /** Laid-out width of each sample, by "app" or "phone" then the sample. */
    private val widths = mutableMapOf<Pair<String, String>, Int>()

    @Test
    fun theThemeDrawsEveryScriptInTheAppFont() {
        rule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(TEST_PHONE_SIZE)) {
                AudoraTheme {
                    Surface {
                        Column(Modifier.testTag(SHEET).padding(20.dp)) {
                            Scale()
                            SAMPLES.forEach { sample ->
                                Measured("app", sample, MaterialTheme.typography.bodyLarge)
                                Measured("phone", sample, MaterialTheme.typography.bodyLarge.merge(PHONE_SANS))
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        Screens.save("app-font", rule.onNodeWithTag(SHEET).captureToImage().asAndroidBitmap())

        SAMPLES.forEach { sample ->
            // The app font failing to load for a script falls back to the phone's sans and measures like it.
            val phone = widths.getValue("phone" to sample)
            assertNotEquals("'$sample' in the app font", phone, widths.getValue("app" to sample))
        }
    }

    @Composable
    private fun Scale() {
        val type = MaterialTheme.typography
        val styles = listOf(type.headlineSmall, type.titleLarge, type.titleMedium, type.bodyLarge, type.labelLarge)
        styles.forEach { style -> Text(SAMPLES.joinToString(" · "), style = style, softWrap = false) }
    }

    @Composable
    private fun Measured(source: String, sample: String, style: TextStyle) {
        Text(sample, style = style, softWrap = false, onTextLayout = { widths[source to sample] = it.size.width })
    }

    private companion object {
        val TEST_PHONE_SIZE = DpSize(411.dp, 914.dp)
        val PHONE_SANS = TextStyle(fontFamily = FontFamily.SansSerif)
        const val SHEET = "app-font"
        val SAMPLES = listOf("Listen to every book", "हर किताब सुनिए", "প্রতিটি বই শুনুন")
    }
}
