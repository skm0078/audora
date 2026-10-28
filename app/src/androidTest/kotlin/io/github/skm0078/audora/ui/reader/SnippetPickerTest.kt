// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/reader/SnippetPickerTest.kt
// Purpose: The page snippet's box on screen (ADR 2026-10-28-page-snippet): a drag inside moves it, a drag on a
//          corner resizes it within a third of the page, a drag outside does nothing; the bar's buttons stay whole at
//          double text size; the cut picture is the box's part of the page. Screenshots go to CI's "player-screens".

package io.github.skm0078.audora.ui.reader

import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.config.ShareCardConfig
import io.github.skm0078.audora.core.share.SnippetBox
import io.github.skm0078.audora.ui.Screens
import io.github.skm0078.audora.ui.share.PageSnippets
import io.github.skm0078.audora.ui.theme.AudoraTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SnippetPickerTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var box by mutableStateOf(START)

    private fun show() {
        rule.setContent {
            AudoraTheme {
                Box(Modifier.size(PAGE_WIDTH, PAGE_HEIGHT).testTag(PAGE)) { SnippetPicker(box) { box = it } }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun aDragInsideMovesTheBoxAndOneOutsideDoesNothing() {
        show()
        Screens.save("page-snippet-box", rule.onRoot().captureToImage().asAndroidBitmap())
        rule.onNodeWithTag(PAGE).performTouchInput {
            swipe(percentOffset(0.5f, 0.45f), percentOffset(0.5f, 0.65f), SWIPE_MS)
        }
        rule.waitForIdle()
        assertTrue("moved down: $box", box.top > START.top + MIN_MOVE)
        assertEquals(START.width, box.width, EPSILON)
        assertEquals(START.height, box.height, EPSILON)

        val moved = box
        rule.onNodeWithTag(PAGE).performTouchInput {
            swipe(percentOffset(0.5f, 0.05f), percentOffset(0.2f, 0.05f), SWIPE_MS)
        }
        rule.waitForIdle()
        assertEquals("a drag outside the box", moved, box)
    }

    @Test
    fun aCornerResizesWithinAThirdOfThePage() {
        show()
        rule.onNodeWithTag(PAGE).performTouchInput {
            // From the bottom right handle to the page's bottom right corner: past a third, so the box gives way.
            swipe(percentOffset(START.right, START.bottom), percentOffset(0.99f, 0.99f), SWIPE_MS)
        }
        rule.waitForIdle()
        assertTrue("grew: $box", box.area > START.area)
        assertTrue("within a third: $box", box.area <= ShareCardConfig().snippetMaxShare + EPSILON)
        assertEquals(START.left, box.left, EPSILON)
        assertEquals(START.top, box.top, EPSILON)
    }

    @Test
    fun talkBackMovesAndResizesTheBoxAStepAtATime() {
        show()
        val step = ShareCardConfig().snippetNudgeStep
        runAction(R.string.a11y_snippet_down)
        assertEquals(START.top + step, box.top, EPSILON)
        assertEquals(START.height, box.height, EPSILON)
        runAction(R.string.a11y_snippet_bigger)
        assertEquals(START.width + step, box.width, EPSILON)
        val covers = context.getString(R.string.a11y_snippet_state, (box.area * PERCENT).roundToInt())
        val state = rule.onNodeWithContentDescription(context.getString(R.string.reader_snippet_box))
            .fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        assertEquals(covers, state)
    }

    /** Runs the box's TalkBack action named [label], as TalkBack's actions menu would. */
    private fun runAction(label: Int) {
        val name = context.getString(label)
        val node = rule.onNodeWithContentDescription(context.getString(R.string.reader_snippet_box))
            .fetchSemanticsNode()
        val action = node.config[SemanticsActions.CustomActions].single { it.label == name }
        rule.runOnUiThread { action.action() }
        rule.waitForIdle()
    }

    @Test
    fun theBarKeepsItsButtonsAtDoubleTextSize() {
        var shared = 0
        var cancelled = 0
        rule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) {
                AudoraTheme { Column { SnippetBar(onCancel = { cancelled++ }, onShare = { shared++ }) } }
            }
        }
        Screens.save("page-snippet-bar-2x", rule.onRoot().captureToImage().asAndroidBitmap())
        rule.onNodeWithText(context.getString(R.string.share_card_send)).performClick()
        rule.onNodeWithText(context.getString(R.string.password_cancel)).performClick()
        assertEquals(1, shared)
        assertEquals(1, cancelled)
    }

    @Test
    fun theCutPictureIsTheBoxsPartOfThePage() {
        // Left half red, right half blue: a box over the right half cuts a blue picture of its size.
        val page = Bitmap.createBitmap(PAGE_PX, PAGE_PX * 3 / 2, Bitmap.Config.ARGB_8888)
        for (x in 0 until page.width) {
            for (y in 0 until page.height) page.setPixel(x, y, if (x < page.width / 2) Color.RED else Color.BLUE)
        }
        assertTrue(PageSnippets.hold(page, SnippetBox(0.5f, 0.2f, 1f, 0.6f)))
        val cut = requireNotNull(PageSnippets.current())
        assertEquals(page.width / 2, cut.width)
        assertNear((page.height * 0.4f).toInt(), cut.height, 1)
        assertEquals(Color.BLUE, cut.getPixel(0, 0))
        PageSnippets.release()
    }

    private fun assertNear(expected: Int, actual: Int, tolerance: Int) =
        assertTrue("expected $expected ± $tolerance, was $actual", abs(expected - actual) <= tolerance)

    private companion object {
        const val PAGE = "page"
        val PAGE_WIDTH = 360.dp
        val PAGE_HEIGHT = 540.dp
        val START = SnippetBox(0.1f, 0.3f, 0.6f, 0.5f)
        const val SWIPE_MS = 400L
        const val MIN_MOVE = 0.1f
        const val EPSILON = 1e-3f
        const val PAGE_PX = 200
        const val PERCENT = 100
    }
}
