// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/reading/SentenceLanguageTest.kt
// Purpose: Which phone language a book's sentence is read in, the English guard, and when "Skip unreadable text" must
//          not judge a sentence (ADR 2026-10-26).

package io.github.skm0078.audora.playback.reading

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingModes
import io.github.skm0078.audora.pdf.quality.BundledWordModels
import io.github.skm0078.audora.pdf.quality.LanguageModel
import io.github.skm0078.audora.playback.unit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceLanguageTest {
    private val english = LanguageModel(
        "the committee approved new budget after long debate on tuesday".split(' ').associateWith { 1 },
    )
    private val rule = SentenceLanguage(english = { english })

    private fun sentence(text: String, lang: Lang = Lang.EN) = unit(0, 0, lang).copy(text = text)

    @Test
    fun `a book in one of the three languages has no phone language`() {
        listOf(null, "", "en", "hi-IN", "BN").forEach { book ->
            assertNull(book, rule.of(sentence("Le chat dort sur la table."), book))
        }
    }

    @Test
    fun `a French book reads its French sentences in French and its English ones in English`() {
        assertEquals("fr", rule.of(sentence("Le chat dort sur la table."), "fr-FR"))
        assertNull(rule.of(sentence("The committee approved the new budget after a long debate."), "fr"))
        // Too few words to tell: the book's language.
        assertEquals("fr", rule.of(sentence("The budget."), "fr"))
    }

    @Test
    fun `the English guard is for Latin letters only`() {
        // A Hindi sentence of a Marathi book is Marathi; a Hindi page of a French book keeps its own voice.
        assertEquals("mr", rule.of(sentence("हे पुस्तक आहे.", Lang.HI), "mr"))
        assertEquals("ta", rule.of(sentence("The committee approved the new budget after a long debate."), "ta"))
    }

    @Test
    fun `scores from another language's word list are not judged`() {
        val french = sentence("Le chat dort sur la table.").copy(speakScore = 0.4f)
        val marathi = sentence("हे पुस्तक आहे.", Lang.HI).copy(speakScore = 0.4f)
        val tamil = sentence("வணக்கம் உலகம்.").copy(speakScore = 0.4f)

        assertTrue(rule.misjudged(french, "fr"))
        assertTrue(rule.misjudged(marathi, "mr"))
        // Tamil letters are scored by no word list, so a low score is a real one; an English book is as before.
        assertFalse(rule.misjudged(tamil, "ta"))
        assertFalse(rule.misjudged(french, "en"))
        assertFalse(rule.misjudged(french, null))
    }

    @Test
    fun `skip unreadable text leaves a French book's sentences to the voice, an English book's junk still cued`() {
        val low = sentence("Le chat dort sur la table.").copy(speakScore = 0.3f)

        assertFalse(UnitReading(ReadingModes(), language = "fr", sentences = rule).isUnreadable(low))
        assertTrue(UnitReading(ReadingModes(), language = "en", sentences = rule).isUnreadable(low))
        assertTrue(UnitReading(ReadingModes(), sentences = rule).isUnreadable(low))
    }

    @Test
    fun `the bundled English list tells English from other Latin languages`() {
        assertNotNull("the English word list must be on the classpath", BundledWordModels.of(Lang.EN))
        val bundled = SentenceLanguage()
        listOf(
            "The committee approved the new budget after a long debate on Tuesday.",
            "She opened the window and looked at the garden for a while.",
            "Results show a significant improvement in accuracy over the baseline method.",
        ).forEach { assertTrue(it, bundled.readsAsEnglish(it)) }
        listOf(
            "Le comité a approuvé le nouveau budget après un long débat mardi.",
            "Elle ouvrit la fenêtre et regarda le jardin pendant un moment.",
            "El comité aprobó el nuevo presupuesto después de un largo debate el martes.",
            "Sie öffnete das Fenster und sah eine Weile in den Garten.",
            "Ela abriu a janela e olhou para o jardim por um tempo.",
            "Il comitato ha approvato il nuovo bilancio dopo un lungo dibattito martedì.",
            "Dia membuka jendela dan melihat taman untuk sementara waktu.",
            "De commissie keurde de nieuwe begroting goed na een lang debat op dinsdag.",
        ).forEach { assertFalse(it, bundled.readsAsEnglish(it)) }
    }
}
