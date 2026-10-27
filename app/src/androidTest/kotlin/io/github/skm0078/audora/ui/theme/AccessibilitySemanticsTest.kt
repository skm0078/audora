// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/theme/AccessibilitySemanticsTest.kt
// Purpose: The shared TalkBack fixes hold: a named slider says what it sets, and a sheet's handle has a name and
//          closes the sheet. Checked with the same audit the UI tour runs on every screen, which must also still
//          catch the bare Material versions, so a passing tour means something.

package io.github.skm0078.audora.ui.theme

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.skm0078.audora.R
import io.github.skm0078.audora.ui.tour.talkBackProblems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccessibilitySemanticsTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun aNamedSliderIsReadByNameAndValue() {
        rule.setContent {
            AudoraTheme {
                Column {
                    Text(TITLE, Modifier.semantics { heading() })
                    Slider(value = 0.5f, onValueChange = {}, modifier = Modifier.namedSlider(SPEED, VALUE))
                }
            }
        }
        rule.onNodeWithContentDescription(SPEED)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, VALUE))
        assertEquals(emptyList<String>(), problems(needsHeading = true))
    }

    @Test
    fun theAuditStillCatchesABareMaterialSlider() {
        rule.setContent {
            AudoraTheme {
                Column {
                    Text(TITLE, Modifier.semantics { heading() })
                    Slider(value = 0.5f, onValueChange = {}, modifier = Modifier.semantics { stateDescription = VALUE })
                }
            }
        }
        val found = problems(needsHeading = true)
        assertTrue("unnamed, got $found", found.any { it.startsWith("unnamed control") })
    }

    @Test
    fun aSheetWithTheSharedHandleHasNothingUnnamedAndTheHandleCloses() {
        var closed = false
        rule.setContent {
            AudoraTheme {
                AppSheet(onDismissRequest = { closed = true }) {
                    Text(TITLE, Modifier.semantics { heading() })
                }
            }
        }
        rule.waitForIdle()
        assertEquals(emptyList<String>(), problems(needsHeading = true))
        // The handle, not Material's scrim behind the sheet, which has the same name.
        val handle = hasContentDescription(rule.activity.getString(R.string.a11y_sheet_close)) and
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
        rule.onNode(handle).performClick()
        rule.waitForIdle()
        assertTrue("the handle closes the sheet", closed)
    }

    private fun problems(needsHeading: Boolean): List<String> =
        talkBackProblems(rule.onAllNodes(isRoot()).fetchSemanticsNodes(), needsHeading)

    private companion object {
        const val TITLE = "Options"
        const val SPEED = "Speed"
        const val VALUE = "1.25×"
    }
}
