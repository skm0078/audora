// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/punct/RunOnPunctuationTest.kt
// Purpose: The run-on guard (RI-11): which sentences reach the model and how its marks are merged.

package io.github.skm0078.audora.speech.punct

import io.github.skm0078.audora.core.config.PunctuationConfig
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.result.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunOnPunctuationTest {
    private val config = PunctuationConfig(minRunWords = 5)
    private val runOn = "the river rose in june the farmers left their fields"

    private fun guard(answer: (String) -> Outcome<String, String>?): Pair<RunOnPunctuation, MutableList<String>> {
        val asked = mutableListOf<String>()
        val punctuator = answer("")?.let {
            Punctuator { text ->
                asked += text
                answer(text)!!
            }
        }
        return RunOnPunctuation(config) { punctuator } to asked
    }

    @Test
    fun `a run-on gets the model's marks, stops softened, and keeps its own words`() {
        val (guard, asked) = guard { Outcome.Success("The river rose in June. The farmers left their fields.") }

        assertEquals("the river rose in june, the farmers left their fields,", guard.apply(runOn, Lang.EN, "t"))
        assertEquals(listOf(runOn), asked)
    }

    @Test
    fun `which texts count as run-ons`() {
        val (guard, _) = guard { Outcome.Success(it) }
        val cases = listOf(
            runOn to Lang.EN to true,
            "the river rose in june. the farmers left" to Lang.EN to false,
            "the river rose in june?" to Lang.EN to false,
            "नदी बढ़ी किसान खेत छोड़ गए।" to Lang.HI to false,
            runOn to Lang.HI to false,
            "too short to ask" to Lang.EN to false,
            "1 - 2 - , - ; - :" to Lang.EN to false,
        )
        cases.forEach { (input, expected) ->
            assertEquals("isRunOn(${input.first})", expected, guard.isRunOn(input.first, input.second))
        }
    }

    @Test
    fun `punctuated or short text never reaches the model`() {
        val (guard, asked) = guard { Outcome.Success("x") }

        assertEquals("Short one.", guard.apply("Short one.", Lang.EN, "t"))
        assertEquals("one two three four five.", guard.apply("one two three four five.", Lang.EN, "t"))
        assertTrue(asked.isEmpty())
    }

    @Test
    fun `no model installed passes the text through`() {
        val (guard, _) = guard { null }

        assertEquals(runOn, guard.apply(runOn, Lang.EN, "t"))
    }

    @Test
    fun `a failure or changed words keep the original`() {
        listOf(
            Outcome.Failure("native error"),
            Outcome.Success("The river rose in June. The farmers left the fields."),
            Outcome.Success("The river rose in June. The farmers left their fields today."),
            Outcome.Success("The river rose in June the farmers left their-fields."),
        ).forEach { answer ->
            val (guard, _) = guard { answer }
            assertEquals("answer $answer", runOn, guard.apply(runOn, Lang.EN, "t"))
        }
    }

    @Test
    fun `merge takes one allowed mark and never doubles an existing one`() {
        val guard = RunOnPunctuation(config) { null }

        assertEquals("NASA, said: go?", guard.merge("NASA said: go", "nasa, said:; go?"))
        assertEquals("wait—then", guard.merge("wait—then", "wait—then"))
        assertEquals("one, two,", guard.merge("one two", "one,. two!"))
        assertNull(guard.merge("one two", "one"))
        assertNull(guard.merge("a b", "a。 b"))
    }
}
