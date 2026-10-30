// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/share/CardMarkLinkTest.kt
// Purpose: The site's host printed under the ListenLock mark fits on one line, inside the picture, on every card
//          shape (ADR 2026-10-30-company-domain raised the card link limit to 28 characters for it).

package io.github.skm0078.audora.ui.share

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.skm0078.audora.core.config.SiteConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardMarkLinkTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theHostFitsOnOneLineOnEveryShape() {
        var shape by mutableStateOf(CardShape.entries.first())
        rule.setContent {
            ShareCardFrame(shape, rememberCardRecorder(), Color.Black, Modifier.size(FRAME)) {}
        }
        CardShape.entries.forEach { next ->
            shape = next
            rule.waitForIdle()
            val node = rule.onNodeWithText(SiteConfig.HOST)
            val layouts = mutableListOf<TextLayoutResult>()
            node.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            val layout = layouts.single()
            // The semantics layout is rebuilt at the mark's full available width, so its overflow flag is always set
            // for a short line; one line at that width plus bounds inside the picture is the real "fits" check.
            assertEquals("$next: lines", 1, layout.lineCount)
            val card = rule.onRoot().fetchSemanticsNode().boundsInRoot
            val link = node.fetchSemanticsNode().boundsInRoot
            assertTrue("$next: the host runs past the picture ($link in $card)", link.left >= card.left)
            assertTrue("$next: the host runs past the picture ($link in $card)", link.right <= card.right)
        }
    }

    private companion object {
        // The card is measured at its full picture size and only drawn scaled, so any frame size proves the same.
        val FRAME = 360.dp
    }
}
