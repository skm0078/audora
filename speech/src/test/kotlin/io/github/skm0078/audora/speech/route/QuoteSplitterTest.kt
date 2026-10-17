// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/route/QuoteSplitterTest.kt
// Purpose: Narration and quoted speech are told apart one sentence at a time (plan item 7).

package io.github.skm0078.audora.speech.route

import org.junit.Assert.assertEquals
import org.junit.Test

class QuoteSplitterTest {
    private val splitter = QuoteSplitter()

    private fun n(text: String) = QuotePart(text, quoted = false)

    private fun q(text: String) = QuotePart(text, quoted = true)

    @Test
    fun `table of sentences and their parts`() {
        val cases = listOf(
            "“Come here at once,” she said." to listOf(q("Come here at once,"), n("she said.")),
            "He said, \"we leave at dawn tomorrow.\"" to listOf(n("He said,"), q("we leave at dawn tomorrow.")),
            "«Je ne sais pas, moi», dit-il." to listOf(q("Je ne sais pas, moi"), n(", dit-il.")),
            "Er sagte: „Wir gehen jetzt los.“ Dann ging er." to
                listOf(n("Er sagte:"), q("Wir gehen jetzt los."), n("Dann ging er.")),
            "उसने कहा, “मैं कल सुबह आऊँगा।”" to listOf(n("उसने कहा,"), q("मैं कल सुबह आऊँगा।")),
            "“Wait for me,” he said, “I am almost ready.”" to
                listOf(q("Wait for me,"), n("he said,"), q("I am almost ready.")),
        )
        cases.forEach { (text, parts) -> assertEquals(text, parts, splitter.split(text)) }
    }

    @Test
    fun `a speech that runs on past the sentence is quoted to its end`() {
        assertEquals(
            listOf(n("She turned and said,"), q("Listen to me carefully.")),
            splitter.split("She turned and said, “Listen to me carefully."),
        )
    }

    @Test
    fun `the last sentence of a speech is quoted up to its closing mark`() {
        assertEquals(
            listOf(q("And that is all I know."), n("He sat down.")),
            splitter.split("And that is all I know.” He sat down."),
        )
    }

    @Test
    fun `no quote, a short quote and single quotes leave the sentence as written`() {
        listOf(
            "Plain narration with no speech at all.",
            "The so-called “experts” were wrong.",
            "It wasn't 'real' speech, said the narrator's friend.",
        ).forEach { text -> assertEquals(text, listOf(n(text)), splitter.split(text)) }
    }

    @Test
    fun `a short quote beside real speech stays narration with its marks`() {
        assertEquals(
            listOf(n("The “experts” said"), q("this cannot possibly work.")),
            splitter.split("The “experts” said “this cannot possibly work.”"),
        )
    }

    @Test
    fun `a sentence that starts inside a quote is quoted to its closing mark, or whole`() {
        assertEquals(listOf(q("I will not go back there.")), splitter.split("I will not go back there.", true))
        assertEquals(
            listOf(q("Not ever again."), n("She shut the door.")),
            splitter.split("Not ever again.” She shut the door.", startsInQuote = true),
        )
        assertEquals(
            listOf(q("Not ever again."), n("She shut the door.")),
            splitter.split("Not ever again.\" She shut the door.", startsInQuote = true),
        )
    }

    @Test
    fun `whether a quote is still open at the end`() {
        assertEquals(true, splitter.endsInQuote("She said, “Listen to me."))
        assertEquals(false, splitter.endsInQuote("“Listen,” she said."))
        assertEquals(true, splitter.endsInQuote("And another thing.", startsInQuote = true))
        assertEquals(false, splitter.endsInQuote("That is all.” He left.", startsInQuote = true))
        assertEquals(false, splitter.endsInQuote("Plain narration."))
    }
}
