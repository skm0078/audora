// File: core/src/test/kotlin/io/github/skm0078/audora/core/dictionary/DictionaryTest.kt
// Purpose: Dictionary look-ups by binary search, listed forms, base forms and tapped-word clean-up (plan item 33).

package io.github.skm0078.audora.core.dictionary

import io.github.skm0078.audora.core.config.DictionaryConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryTest {
    private val text = listOf(
        "aardvark\tn|a burrowing mammal|ant bear",
        "box\tn|a container|\tv|put into a box|",
        "café\tn|a small restaurant|coffee shop",
        "go\tv|move or travel|travel,move\tn|a board game|",
        "ice cream\tn|frozen dessert|ice-cream",
        "leaf\tn|the flat green part of a plant|foliage",
        "leave\tv|go away from a place|depart",
        "leaves\t=leaf,leave",
        "mice\t=mouse",
        "mouse\tn|a small rodent|",
        "run\tv|move fast on foot|",
        "study\tv|learn by reading|",
        "went\t=go",
    ).joinToString("\n", postfix = "\n")
    private val dictionary = Dictionary(SortedLines(ByteArraySource(text.toByteArray())))

    @Test
    fun `every line is found by binary search, and absent keys are not`() {
        val lines =
            SortedLines(ByteArraySource(text.toByteArray()), DictionaryConfig(chunkBytes = 8, maxLineBytes = 64))
        text.trim().split("\n").forEach { line -> assertEquals(line, lines.find(line.substringBefore('\t'))) }
        listOf("", "a", "boxes", "zzz", "ice", "caf").forEach { assertNull(it, lines.find(it)) }
    }

    @Test
    fun `table of look-ups`() {
        val cases = listOf(
            "Box" to listOf("box"),
            "boxes" to listOf("box"),
            "“Mouse’s”" to listOf("mouse"),
            "mice" to listOf("mouse"),
            "went" to listOf("go"),
            "leaves" to listOf("leaf", "leave"),
            "studied" to listOf("study"),
            "running" to listOf("run"),
            "Café," to listOf("café"),
            "ice cream" to listOf("ice cream"),
            "xyzzy" to emptyList(),
            "..." to emptyList(),
        )
        cases.forEach { (word, headwords) ->
            assertEquals(word, headwords, dictionary.lookUp(word).map { it.headword })
        }
    }

    @Test
    fun `an entry carries its senses in order with parts of speech and synonyms`() {
        val go = dictionary.lookUp("go").single()

        assertEquals(PartOfSpeech.VERB, go.senses[0].partOfSpeech)
        assertEquals(listOf("travel", "move"), go.senses[0].synonyms)
        assertEquals(DictionarySense(PartOfSpeech.NOUN, "a board game", emptyList()), go.senses[1])
    }

    @Test
    fun `base forms follow morphy's endings, the word itself first`() {
        val forms = EnglishBaseForms()
        val cases = listOf(
            "churches" to "church",
            "studies" to "study",
            "women" to "woman",
            "hoping" to "hope",
            "stopped" to "stop",
            "bigger" to "big",
            "largest" to "large",
        )
        cases.forEach { (word, base) ->
            val found = forms.of(word)
            assertEquals(word, found.first())
            assertTrue("$word -> $base in $found", base in found)
        }
    }

    @Test
    fun `the words of a sentence, each once, without edge punctuation`() {
        assertEquals(
            listOf("The", "cat's", "well-known", "café", "cats"),
            WordKey.wordsOf("The cat's well-known café — the 2 \"cats\""),
        )
    }
}
