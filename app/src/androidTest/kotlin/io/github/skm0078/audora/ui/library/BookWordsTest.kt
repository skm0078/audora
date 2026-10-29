// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/library/BookWordsTest.kt
// Purpose: "Words you looked up": the page lists each word with its page and meaning, and its quiz asks for the
//          meaning, marks the answer and ends on the score; back arrows step back a page at a time.

package io.github.skm0078.audora.ui.library

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.words.LookedUpWord
import io.github.skm0078.audora.ui.theme.AudoraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookWordsTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var page by mutableStateOf(BookMenuPage.WORDS)
    private val words = listOf("dignity", "arbitrary", "elementary", "compulsory").mapIndexed { i, w ->
        LookedUpWord(BookId("udhr"), w, "meaning of $w", i, "A sentence with $w.", Lang.EN, 10L - i)
    }

    private fun text(id: Int, vararg args: Any): String = rule.activity.getString(id, *args)

    private fun show() = rule.setContent {
        AudoraTheme { BookWordsPages(page, words, words) { page = it } }
    }

    @Test
    fun eachWordShowsWithItsPageAndMeaning() {
        show()

        rule.onNodeWithText("dignity").assertExists()
        rule.onNodeWithText("meaning of dignity").assertExists()
        rule.onNodeWithText(text(R.string.book_words_page, 4)).assertExists()
    }

    @Test
    fun theQuizAsksTheMeaningMarksItAndEndsOnTheScore() {
        show()
        val quiz = rule.activity.resources.getQuantityString(R.plurals.book_words_quiz, 4, 4)
        rule.onNodeWithText(quiz).performClick()
        rule.runOnIdle { assertEquals(BookMenuPage.QUIZ, page) }

        repeat(words.size) { i ->
            rule.onNodeWithText(words[i].meaning).performClick()
            rule.onNode(hasText(text(R.string.book_words_quiz_right, words[i].meaning))).assertExists()
            rule.onNodeWithText(text(R.string.quiz_next)).performClick()
        }

        rule.onNodeWithText(text(R.string.book_words_quiz_score, 4, 4)).assertExists()
        rule.onNodeWithText(text(R.string.quiz_done)).performClick()
        rule.runOnIdle { assertEquals(BookMenuPage.WORDS, page) }
    }

    @Test
    fun theBackArrowLeavesTheWordsForTheMenu() {
        show()

        rule.onNodeWithContentDescription(text(R.string.navigate_back)).performClick()

        rule.runOnIdle { assertEquals(BookMenuPage.MENU, page) }
    }
}
