// File: core/src/test/kotlin/io/github/skm0078/audora/core/dictionary/IndicDictionaryTest.kt
// Purpose: Hindi and Bengali look-ups: Wiktionary's form lines, Unicode forms of a word and the script routing.

package io.github.skm0078.audora.core.dictionary

import io.github.skm0078.audora.core.config.DictionaryConfig
import io.github.skm0078.audora.core.model.Lang
import org.junit.Assert.assertEquals
import org.junit.Test

class IndicDictionaryTest {
    // As scripts/build_dictionary.py writes them: sorted by code point, NFC keys (क़ stays क + nukta).
    private val text = listOf(
        "और\tc|and|\tx|more|",
        "कुत्ता\tn|dog|श्वान",
        "कुत्ते\tn|dogs (a plural use)|\t=कुत्ता",
        "क़िताब\tn|book|पुस्तक",
        "बई\t=बही",
        "বই\tn|book|",
    ).sorted().joinToString("\n", postfix = "\n")
    private val dictionary = Dictionary(SortedLines(ByteArraySource(text.toByteArray())), BaseForms.AsWritten)

    @Test
    fun `a form with senses of its own also shows the word it is a form of`() {
        assertEquals(listOf("कुत्ते", "कुत्ता"), dictionary.lookUp("कुत्ते,").map { it.headword })
    }

    @Test
    fun `a precomposed nukta letter and a word in joiners or danda are found`() {
        assertEquals(listOf("क़िताब"), dictionary.lookUp("क़िताब").map { it.headword })
        assertEquals(listOf("বই"), dictionary.lookUp("ব‌ই।").map { it.headword })
    }

    @Test
    fun `ligatures and soft hyphens from a PDF are keyed like the plain word`() {
        assertEquals("fix", WordKey.of("\uFB01x", DictionaryConfig().maxWordChars))
        assertEquals("reading", WordKey.of("read\u00ADing,", DictionaryConfig().maxWordChars))
    }

    @Test
    fun `a word ending in a vowel sign keeps it, and a redirect to nothing finds nothing`() {
        assertEquals(listOf("कुत्ता"), dictionary.lookUp("कुत्ता").map { it.headword })
        assertEquals(emptyList<DictionaryEntry>(), dictionary.lookUp("बई"))
    }

    @Test
    fun `parts of speech beyond the four read, unknown ones as other`() {
        val senses = dictionary.lookUp("और").single().senses.map { it.partOfSpeech }
        assertEquals(listOf(PartOfSpeech.CONJUNCTION, PartOfSpeech.OTHER), senses)
        assertEquals(PartOfSpeech.OTHER, PartOfSpeech.of("?"))
    }

    @Test
    fun `the script of a word picks its dictionary`() {
        val cases =
            listOf("किताब" to Lang.HI, "বই" to Lang.BN, "book" to Lang.EN, "“किताब”" to Lang.HI, "42" to Lang.EN)
        cases.forEach { (word, lang) -> assertEquals(word, lang, DictionaryLanguage.of(word)) }
    }

    @Test
    fun `long Wiktionary lines are read within the line limit`() {
        val line = "क\t" + List(200) { "n|sense $it|" }.joinToString("\t")
        val lines = SortedLines(ByteArraySource("$line\n".toByteArray()), DictionaryConfig())
        assertEquals(200, Dictionary(lines, BaseForms.AsWritten).lookUp("क").single().senses.size)
    }
}
