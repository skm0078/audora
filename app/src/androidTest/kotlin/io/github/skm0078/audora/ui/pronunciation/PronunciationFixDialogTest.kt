// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/pronunciation/PronunciationFixDialogTest.kt
// Purpose: The shared pronunciation fixes dialog adds what was typed and clears it, and each Delete is a full-size
//          target that TalkBack names after its fix. Screenshot goes to CI's "player-screens" artifact.

package io.github.skm0078.audora.ui.pronunciation

import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.notes.PronunciationRule
import io.github.skm0078.audora.ui.Screens
import io.github.skm0078.audora.ui.theme.AudoraTheme
import io.github.skm0078.audora.ui.theme.Dimens
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PronunciationFixDialogTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)

    private val rules = listOf(
        PronunciationRule(1, null, Lang.EN, "colonel", "kernel", true),
        PronunciationRule(2, null, Lang.EN, "Worcester", "Wuster", true),
    )

    private val added = mutableListOf<FixDraft>()
    private val deleted = mutableListOf<PronunciationRule>()

    private fun show() = rule.setContent {
        AudoraTheme {
            PronunciationFixDialog(
                title = "Fixes",
                rules = rules,
                where = { "English" },
                onAdd = { added += it },
                onDelete = { deleted += it },
                onDismiss = {},
            )
        }
    }

    private val emptyField = SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(""))

    private fun field(label: Int) = rule.onNode(hasSetTextAction() and hasText(text(label)))

    @Test
    fun addSendsWhatWasTypedThenClearsTheFields() {
        show()
        rule.onNodeWithText(text(R.string.fix_add)).assertIsNotEnabled()
        field(R.string.fix_written).performTextInput("Leicester")
        field(R.string.fix_spoken).performTextInput("Lester")
        rule.onNodeWithText(text(R.string.fix_add)).performClick()
        assertEquals(listOf(FixDraft("Leicester", "Lester", wholeWord = true)), added)
        field(R.string.fix_written).assert(emptyField)
    }

    @Test
    fun eachDeleteIsAFullTargetNamedAfterItsFix() {
        show()
        rules.forEach { fix ->
            rule.onNodeWithContentDescription(text(R.string.a11y_fix_delete, fix.written, fix.spoken))
                .performScrollTo()
                .assertHeightIsAtLeast(Dimens.TouchTarget)
        }
        Screens.save("pronunciation-fixes", rule.onNode(isDialog()).captureToImage().asAndroidBitmap())
        rule.onNodeWithContentDescription(text(R.string.a11y_fix_delete, "Worcester", "Wuster")).performClick()
        assertEquals(listOf(rules[1]), deleted)
    }
}
