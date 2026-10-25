// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/reader/PlayerLayoutTest.kt
// Purpose: The player fills a phone's width at every font size (it huddled at the left on the test phone),
//          and leaves a screenshot of each size for review in CI's "player-screens" artifact.

package io.github.skm0078.audora.ui.reader

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.config.ReaderConfig
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.pdf.render.ViewPoint
import io.github.skm0078.audora.ui.Screens
import io.github.skm0078.audora.ui.theme.AudoraTheme
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerLayoutTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theControlsSpanThePhoneAtEveryFontSize() {
        var fontScale by mutableFloatStateOf(FONT_SCALES.first())
        rule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(TEST_PHONE_SIZE) then
                    DeviceConfigurationOverride.FontScale(fontScale),
            ) {
                AudoraTheme {
                    PlayerScreen(playingState(), ReaderConfig(), NoActions, onShowPage = {}, Modifier.testTag(PLAYER))
                }
            }
        }
        FONT_SCALES.forEach { scale ->
            rule.runOnIdle { fontScale = scale }
            rule.waitForIdle()
            val player = rule.onNodeWithTag(PLAYER)
            Screens.save("player-font-${(scale * PERCENT).toInt()}", player.captureToImage().asAndroidBitmap())
            // The player's own bounds: the emulator's screen need not have the test phone's shape around it.
            val root = player.fetchSemanticsNode().boundsInRoot
            val speed = boundsOf(context.getString(R.string.reader_speed, speedLabel(1f)), byText = true)
            val sleep = boundsOf(context.getString(R.string.reader_sleep_timer))
            val play = boundsOf(context.getString(R.string.reader_play))
            // In shares of the width: ForcedSize changes the density inside, so the device's dp would not match.
            val edge = root.width * EDGE_SHARE
            val at = "at font scale $scale: speed=$speed play=$play sleep=$sleep player=$root"
            assertTrue("The speed button leaves the left edge $at", speed.left - root.left <= edge)
            assertTrue("The sleep timer leaves the right edge $at", root.right - sleep.right <= edge)
            val offCentre = abs(play.center.x - root.center.x)
            assertTrue("Play is not in the middle $at", offCentre <= root.width * CENTRE_SHARE)
        }
    }

    /** The node's bounds, by content description, or by its text for a text button. */
    private fun boundsOf(label: String, byText: Boolean = false): Rect = rule.onRoot().fetchSemanticsNode()
        .let { root ->
            val found = generateSequence(listOf(root)) { level -> level.flatMap { it.children }.ifEmpty { null } }
                .flatten()
                .firstOrNull { node ->
                    val config = node.config
                    if (byText) {
                        config.getOrElse(SemanticsProperties.Text) { emptyList() }.any { it.text == label }
                    } else {
                        config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }.contains(label)
                    }
                }
            requireNotNull(found) { "No node labelled \"$label\" on the player" }.boundsInRoot
        }

    private object NoActions : ReaderActions {
        override fun onBack() = Unit
        override fun onPreviousPage() = Unit
        override fun onNextPage() = Unit
        override fun onGoToPage(page: Int) = Unit
        override fun onShowReadingPosition() = Unit
        override fun onSentenceTapped(unit: ReadingUnit) = Unit
        override fun onPageTapped(point: ViewPoint) = Unit
        override fun onViewChanged(view: ReaderView) = Unit
        override fun onPageWidth(widthPx: Int) = Unit
        override fun onPlayPause() = Unit
        override fun onSkipBack() = Unit
        override fun onSkipForward() = Unit
        override fun onSpeed(speed: Float) = Unit
        override fun onSleepTimer(setting: SleepTimerSetting) = Unit
        override fun onPasswordSubmitted(password: String) = Unit
        override fun onPasswordDismissed() = Unit
        override fun onAskForPassword() = Unit
        override fun onRetry() = Unit
    }

    private companion object {
        /** 1080 × 2400 px at about 420 dpi. */
        val TEST_PHONE_SIZE = DpSize(411.dp, 914.dp)
        val FONT_SCALES = listOf(1f, 1.3f, 2f)

        /** The panel's padding plus a button's inner space, about 32 dp of 411: more and the row has huddled. */
        const val EDGE_SHARE = 0.08f

        /** Play may sit a little off the middle (the side buttons differ in width), never a whole button's width. */
        const val CENTRE_SHARE = 0.1f
        const val PERCENT = 100
        const val PLAYER = "player"

        val BOOK = BookId("layout-book")

        fun playingState(): ReaderState {
            val sentences = listOf(
                "Call me Ishmael.",
                "Some years ago, never mind how long precisely, having little or no money in my purse, and " +
                    "nothing particular to interest me on shore, I thought I would sail about a little and see " +
                    "the watery part of the world.",
                "It is a way I have of driving off the spleen and regulating the circulation.",
            )
            var start = 0
            val units = sentences.mapIndexed { index, text ->
                ReadingUnit(0, index, start, start + text.length, text, Lang.EN, 0, null).also {
                    start += text.length + 1
                }
            }
            return ReaderState(
                bookId = BOOK,
                title = "Moby-Dick; or, The Whale",
                document = DocumentState.Open(pageCount = 120),
                content = PageContent.Ready(units),
                view = ReaderView.TEXT,
                timeLeft = TimeLeftEstimate(bookMs = 9_000_000, chapterMs = 600_000),
                playback = PlaybackSnapshot(
                    connection = PlaybackSnapshot.Connection.CONNECTED,
                    position = ReadingPosition(BOOK, 0, units[1].charStart),
                    canSkipBack = true,
                    canSkipForward = true,
                ),
            )
        }
    }
}
