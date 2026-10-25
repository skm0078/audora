// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/reader/OptionRowsLayoutTest.kt
// Purpose: Option rows with long values keep their titles whole at every font size (a long voice name squeezed
//          "English voice" to one letter per line on the test phone), checked by no word being split across
//          lines, and leave screenshots of each size in CI's "player-screens" artifact.

package io.github.skm0078.audora.ui.reader

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.then
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.skm0078.audora.core.config.SpeechStyleConfig
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.model.VoiceShaping
import io.github.skm0078.audora.speech.voices.VoiceKind
import io.github.skm0078.audora.speech.voices.VoiceRow
import io.github.skm0078.audora.speech.voices.VoiceStatus
import io.github.skm0078.audora.ui.Screens
import io.github.skm0078.audora.ui.theme.AudoraTheme
import io.github.skm0078.audora.ui.theme.Dimens
import io.github.skm0078.audora.ui.theme.LinkOption
import io.github.skm0078.audora.ui.theme.OptionDivider
import io.github.skm0078.audora.ui.theme.OptionGroup
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OptionRowsLayoutTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun longValuesNeverSplitATitleAtAnyFontSize() {
        var fontScale by mutableFloatStateOf(FONT_SCALES.first())
        rule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(TEST_PHONE_SIZE) then
                    DeviceConfigurationOverride.FontScale(fontScale),
            ) {
                AudoraTheme {
                    Surface(color = MaterialTheme.colorScheme.surface) {
                        Column(
                            Modifier.testTag(ROWS).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
                        ) {
                            // The phone's case: quotes and headings in a second voice with a long name.
                            CastVoicesSection(VOICES, CAST, onChange = {})
                            OptionGroup(title = "Reading") {
                                LinkOption("Focus sound", onClick = {}, value = "Rain on a tin roof, far away")
                                OptionDivider()
                                LinkOption("Storage", onClick = null, value = "1.4 GB of 2 GB", help = "Where")
                            }
                            OptionGroup(title = null) { PitchBlock(1.05f, SpeechStyleConfig(), onPitch = {}) }
                        }
                    }
                }
            }
        }
        FONT_SCALES.forEach { scale ->
            rule.runOnIdle { fontScale = scale }
            rule.waitForIdle()
            val rows = rule.onNodeWithTag(ROWS)
            Screens.save("option-rows-font-${(scale * PERCENT).toInt()}", rows.captureToImage().asAndroidBitmap())
            assertNoWordSplit(scale)
        }
    }

    /** Every text on screen breaks only between words (a squeezed title breaks inside them, a letter per line). */
    private fun assertNoWordSplit(scale: Float) {
        val texts = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), true)
            .fetchSemanticsNodes()
        assertTrue("No text found at font scale $scale", texts.isNotEmpty())
        texts.forEach { node ->
            val layouts = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            layouts.forEach { layout ->
                val text = layout.layoutInput.text.text
                (0 until layout.lineCount - 1).map(layout::getLineEnd).filter { it in 1 until text.length }
                    .forEach { end ->
                        val before = text[end - 1]
                        val between = before.isWhitespace() || before in BREAKS || text[end].isWhitespace()
                        assertTrue("\"$text\" is split inside a word at $end, font scale $scale", between)
                    }
            }
        }
    }

    private companion object {
        /** 1080 × 2400 px at about 420 dpi. */
        val TEST_PHONE_SIZE = DpSize(411.dp, 914.dp)
        val FONT_SCALES = listOf(1f, 1.3f, 2f)
        const val PERCENT = 100
        const val ROWS = "rows"

        /** A line may also end after these without splitting a word. */
        const val BREAKS = "-/,"

        fun voice(name: String, label: String, usual: Boolean) = VoiceRow(
            VoiceId(EngineId.SHERPA, name),
            label,
            Lang.EN,
            "Apache-2.0",
            0,
            VoiceKind.CATALOG,
            VoiceStatus.Installed,
            readsLanguage = usual,
        )

        val VOICES = listOf(
            voice("bruno", "English, Bruno (male)", usual = true),
            voice("michael", "English (US), Kokoro Michael, male (premium)", usual = false),
        )
        val CAST = VoiceShaping(
            dialogueVoices = mapOf(Lang.EN to VOICES[1].id),
            headingVoices = mapOf(Lang.EN to VOICES[1].id),
        )
    }
}
