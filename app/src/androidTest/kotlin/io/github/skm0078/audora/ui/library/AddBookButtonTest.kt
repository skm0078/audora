// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/library/AddBookButtonTest.kt
// Purpose: At double text size Add book shrinks to its icon while the books scroll down, keeps its TalkBack name, and
//          opens again when they scroll back up.

package io.github.skm0078.audora.ui.library

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skm0078.audora.R
import io.github.skm0078.audora.ui.theme.AudoraTheme
import io.github.skm0078.audora.ui.theme.Dimens
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddBookButtonTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val add get() = context.getString(R.string.library_add_pdf)

    @Test
    fun foldsWhileReadingDownAndOpensGoingBackUp() {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, LARGE_TEXT)) {
                AudoraTheme {
                    val folding = remember { AddBookFolding() }
                    Box(Modifier.fillMaxSize().nestedScroll(folding)) {
                        LazyColumn(Modifier.fillMaxSize().testTag(LIST)) {
                            items(ROWS) { Text("Book $it", Modifier.padding(Dimens.SpaceL)) }
                        }
                        Box(Modifier.align(Alignment.BottomEnd).padding(Dimens.SpaceL)) {
                            AddBookButton(folding.expanded) {}
                        }
                    }
                }
            }
        }
        val open = width()
        rule.onNodeWithTag(LIST).performTouchInput { swipeUp() }
        rule.waitForIdle()
        val folded = width()
        assertTrue("folded at $folded px, open at $open px", folded < open)
        rule.onNodeWithTag(LIST).performTouchInput { swipeDown() }
        rule.waitForIdle()
        assertTrue("open again at ${width()} px, first $open px", width() == open)
    }

    /** Found by its name, so this also checks that folding keeps what TalkBack reads. */
    private fun width(): Float = rule.onNodeWithContentDescription(add).fetchSemanticsNode().boundsInRoot.width

    private companion object {
        const val LIST = "books"
        const val ROWS = 60
        const val LARGE_TEXT = 2f
    }
}
