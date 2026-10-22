// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/ocr/OcrRepairTest.kt
// Purpose: OCR near-misses are corrected to the one common known word, and everything uncertain is left alone (RI-3).

package io.github.skm0078.audora.pdf.ocr

import io.github.skm0078.audora.core.config.OcrRepairConfig
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.pdf.quality.LanguageModel
import io.github.skm0078.audora.pdf.quality.WordModels
import io.github.skm0078.audora.pdf.render.ViewRect
import org.junit.Assert.assertEquals
import org.junit.Test

class OcrRepairTest {
    private val english = LanguageModel(
        mapOf(
            "the" to 73, "modern" to 50, "government" to 52, "freedom" to 48, "rights" to 50, "human" to 52,
            "cat" to 45, "bat" to 45, "hat" to 46, "nation" to 49, "notion" to 40, "and" to 72, "dignity" to 42,
        ),
    )
    private val hindi = LanguageModel(
        mapOf("सरकार" to 50, "अधिकार" to 50, "मानव" to 48, "और" to 70, "सद्भावना" to 35, "एवं" to 60, "विशेषतः" to 40),
    )
    private val bengali = LanguageModel(mapOf("নিরাপত্তা" to 45, "অধিকার" to 50))
    private val models = WordModels { lang -> mapOf(Lang.EN to english, Lang.HI to hindi, Lang.BN to bengali)[lang] }
    private val repair = OcrRepair(OcrRepairConfig(), models)

    private fun word(text: String, confidence: Int? = UNSURE, left: Float = 0f) =
        OcrWord(text, ViewRect(left, 0f, left + WIDTH, HEIGHT), confidence)

    private fun repaired(vararg words: OcrWord): List<String> =
        repair.repair(OcrText(listOf(OcrLine(words.toList(), 0)))).lines.single().words.map { it.text }

    @Test
    fun `near-misses become the known word, as printed`() {
        listOf(
            "rnodern" to "modern",
            "govemment" to "government",
            "goverment" to "government",
            "freedorn" to "freedom",
            "rigths" to "rights",
            "hte" to "the",
            "Hurnan" to "Human",
            "HURNAN" to "HUMAN",
            "(goverment)," to "(government),",
            "dignlty" to "dignity",
            "सद्धावना" to "सद्भावना", // ध read for भ
            "णवं" to "एवं", // ण read for ए
            "विशेषत:" to "विशेषतः", // a colon read for the visarga
            "নিরাপত্ত!" to "নিরাপত্তা", // "!" read for the vowel sign aa
        ).forEach { (read, printed) -> assertEquals(read, listOf(printed), repaired(word(read))) }
    }

    @Test
    fun `known, confident, short, numeric, ambiguous and unlisted words are kept`() {
        listOf(
            word("nation"), // known
            word("notlon", confidence = 95), // Tesseract was sure
            word("ct"), // short
            word("2O21"), // digits
            word("aat"), // cat, bat and hat equally likely
            word("Zanzibar"), // nothing near it
            word("rnodern", confidence = null), // no confidence to go on
            word("सरकर"), // Hindi changes only through the confusions: a free edit is not tried
        ).forEach { w -> assertEquals(w.text, listOf(w.text), repaired(w)) }
    }

    @Test
    fun `a word OCR split in two is rejoined, with both boxes`() {
        val line = OcrText(listOf(OcrLine(listOf(word("the"), word("govern", left = 1f), word("ment.", left = 2f)), 0)))
        val words = repair.repair(line).lines.single().words
        assertEquals(listOf("the", "government."), words.map { it.text })
        assertEquals(ViewRect(1f, 0f, 2f + WIDTH, HEIGHT), words[1].box)
    }

    @Test
    fun `a word broken across lines is left for the page text to join`() {
        assertEquals(listOf("the", "goverm-"), repaired(word("the"), word("goverm-")))
    }

    @Test
    fun `switched off, the text is as read`() {
        val off = OcrRepair(OcrRepairConfig(enabled = false), models)
        val ocr = OcrText(listOf(OcrLine(listOf(word("rnodern")), 0)))
        assertEquals("rnodern", off.repair(ocr).lines.single().words.single().text)
    }

    private companion object {
        const val UNSURE = 60
        const val WIDTH = 0.5f
        const val HEIGHT = 0.1f
    }
}
