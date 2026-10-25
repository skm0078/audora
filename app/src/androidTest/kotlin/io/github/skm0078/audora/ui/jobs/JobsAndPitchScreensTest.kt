// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/jobs/JobsAndPitchScreensTest.kt
// Purpose: The Jobs screen and the pitch control (ADR-067) on a phone-sized screen: what each shows, that a change
//          applies at once, and a screenshot of each for review in CI's "player-screens" artifact.

package io.github.skm0078.audora.ui.jobs

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.config.SpeechStyleConfig
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.playback.jobs.LongJob
import io.github.skm0078.audora.playback.jobs.LongJobKind
import io.github.skm0078.audora.speech.download.DownloadJob
import io.github.skm0078.audora.speech.download.DownloadKind
import io.github.skm0078.audora.ui.reader.PitchBlock
import io.github.skm0078.audora.ui.theme.AudoraTheme
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JobsAndPitchScreensTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theJobsScreenNamesEachJobAndDownloadAndCancelsTheOneTapped() {
        val cancelled = mutableListOf<DownloadJob>()
        rule.setContent {
            Phone {
                JobsScreen(
                    jobs = listOf(AUDIOBOOK),
                    downloads = listOf(VOICE, DICTIONARY_WAITING),
                    voiceNames = mapOf("en-bruno" to "Bruno"),
                    onCancelDownload = { cancelled += it },
                    onOpenMade = {},
                    onBack = {},
                    modifier = Modifier.testTag(SCREEN),
                )
            }
        }
        rule.onNodeWithText(AUDIOBOOK.title).assertExists()
        rule.onNodeWithText(context.getString(R.string.jobs_download_voice, "Bruno")).assertExists()
        rule.onNodeWithText(context.getString(R.string.jobs_download_waiting)).assertExists()
        save("jobs-screen", rule.onNodeWithTag(SCREEN).captureToImage().asAndroidBitmap())

        val voice = context.getString(R.string.jobs_download_voice, "Bruno")
        val cancel = context.getString(
            R.string.storage_action_on,
            context.getString(R.string.jobs_download_cancel),
            voice,
        )
        rule.onNodeWithContentDescription(cancel).performClick()
        assertEquals(listOf(VOICE), cancelled)
    }

    @Test
    fun theJobsScreenSaysWhatShowsThereWhenNothingRuns() {
        rule.setContent {
            Phone {
                JobsScreen(emptyList(), emptyList(), emptyMap(), onCancelDownload = {}, onOpenMade = {}, onBack = {})
            }
        }
        rule.onNodeWithText(context.getString(R.string.jobs_empty)).assertExists()
    }

    @Test
    fun aPitchChangeShowsAtOnceAndGoesBackToTheVoicesOwnInOneTap() {
        var pitch by mutableFloatStateOf(1f)
        val config = SpeechStyleConfig()
        rule.setContent {
            Phone { PitchBlock(pitch, config, onPitch = { pitch = it }, modifier = Modifier.testTag(SCREEN)) }
        }
        rule.onNodeWithText(context.getString(R.string.speech_pitch_own)).assertExists()

        rule.onNodeWithContentDescription(context.getString(R.string.speech_pitch_up)).performClick()
        rule.waitForIdle()
        assertEquals(1.05f, pitch)
        rule.onNodeWithText(context.getString(R.string.speech_pitch_higher, 5)).assertExists()
        save("pitch-higher", rule.onNodeWithTag(SCREEN).captureToImage().asAndroidBitmap())

        rule.onNodeWithText(context.getString(R.string.speech_pitch_reset)).performClick()
        rule.waitForIdle()
        assertEquals(1f, pitch)
        rule.onNodeWithText(context.getString(R.string.speech_pitch_own)).assertExists()

        rule.runOnIdle { pitch = config.minPitch }
        rule.onNodeWithContentDescription(context.getString(R.string.speech_pitch_down)).assertIsNotEnabled()
        rule.onNodeWithContentDescription(context.getString(R.string.speech_pitch_up)).assertIsEnabled()
        rule.onNodeWithText(context.getString(R.string.speech_pitch_lower, 20)).assertExists()
    }

    @Composable
    private fun Phone(content: @Composable () -> Unit) {
        DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(MOTO_EDGE_50_FUSION)) {
            AudoraTheme { content() }
        }
    }

    /** Into Downloads/ListenLock-screens, which outlives the test app's uninstall; CI pulls it into an artifact. */
    private fun save(name: String, bitmap: Bitmap) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "$name.png")
            put(MediaStore.Downloads.MIME_TYPE, "image/png")
            put(MediaStore.Downloads.RELATIVE_PATH, SCREENS_FOLDER)
        }
        val resolver = context.contentResolver
        val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)) { "No $name" }
        requireNotNull(resolver.openOutputStream(uri)) { "Cannot write $name" }.use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)) { "PNG of $name failed" }
        }
    }

    private companion object {
        val MOTO_EDGE_50_FUSION = DpSize(411.dp, 914.dp)
        const val PNG_QUALITY = 100
        const val SCREENS_FOLDER = "Download/ListenLock-screens"
        const val SCREEN = "screen"

        val AUDIOBOOK = LongJob(
            kind = LongJobKind.AUDIOBOOK,
            bookId = BookId("jobs-book"),
            title = "Saving “Gitanjali” as an audiobook",
            detail = "Chapter 3 of 12",
            percent = 25,
        )
        val VOICE = DownloadJob(UUID.randomUUID(), DownloadKind.VOICE, "en-bruno", true, 34_000_000L, 120_000_000L)
        val DICTIONARY_WAITING = DownloadJob(UUID.randomUUID(), DownloadKind.DICTIONARY, "bn", running = false)
    }
}
