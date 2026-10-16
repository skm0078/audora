// File: playback/src/androidTest/kotlin/io/github/skm0078/audora/playback/voice/MixedScriptVoicesTest.kt
// Purpose: P3-01 done-when on the device: the bilingual English/Hindi PDF switches voices correctly, through the
//   real PDF pipeline and the playback renderer, with recording fake voices; Hindi numbers as words (P3-02).

package io.github.skm0078.audora.playback.voice

import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.getOrNull
import io.github.skm0078.audora.pdf.document.PdfBook
import io.github.skm0078.audora.pdf.pdfium.PdfiumEngine
import io.github.skm0078.audora.pdf.units.ReadingUnitBuilder
import io.github.skm0078.audora.speech.RenderResult
import io.github.skm0078.audora.speech.SpeechEngine
import io.github.skm0078.audora.speech.SpeechError
import io.github.skm0078.audora.speech.VoiceInfo
import io.github.skm0078.audora.speech.audio.PcmAudio
import io.github.skm0078.audora.speech.route.VoiceSource
import java.io.File
import java.util.Collections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MixedScriptVoicesTest {
    private class Spoken(val voice: String, val text: String)

    /** One sample per character at a different rate per voice, so the joined clip's rate is checked too. */
    private class RecordingVoices :
        VoiceSource,
        SpeechEngine {
        val spoken: MutableList<Spoken> = Collections.synchronizedList(ArrayList())
        override val id = EngineId.SYSTEM

        override suspend fun voiceFor(lang: Lang): Outcome<VoiceId, SpeechError> =
            Outcome.Success(VoiceId(EngineId.SYSTEM, lang.name))

        override fun engine(id: EngineId): Outcome<SpeechEngine, SpeechError> = Outcome.Success(this)

        override fun supports(lang: Lang) = true

        override suspend fun voices(): Outcome<List<VoiceInfo>, SpeechError> = Outcome.Success(emptyList())

        override suspend fun render(text: String, voice: VoiceId): RenderResult {
            spoken.add(Spoken(voice.name, text))
            val rate = if (voice.name == Lang.EN.name) EN_RATE else HI_RATE
            return Outcome.Success(PcmAudio(ShortArray(text.length), rate))
        }

        override fun release() = Unit
    }

    @Test
    fun bilingualBookSwitchesVoicesCorrectly() {
        val units = bookUnits()
        val voices = RecordingVoices()
        // Without normalizers, so the parts add up to the sentence as printed; numbers are checked below.
        val renderer = EngineRenderer(voices, normalizers = emptyMap())
        var mixedSentences = 0

        for (unit in units) {
            val before = voices.spoken.size
            val audio = (renderer.render(unit) as Outcome.Success).value
            val parts = voices.spoken.subList(before, voices.spoken.size).toList()
            assertEquals(unit.text.squeezed(), parts.joinToString("") { it.text }.squeezed())
            if (parts.map { it.voice }.distinct().size > 1) {
                mixedSentences++
                assertEquals("p${unit.page}u${unit.index}", EN_RATE, audio.sampleRate)
            }
        }

        val english = voices.spoken.filter { it.voice == Lang.EN.name }
        val hindi = voices.spoken.filter { it.voice == Lang.HI.name }
        // An English voice never gets Devanagari; the Hindi voice never gets three English words in a row.
        english.forEach { assertTrue("English voice got Hindi: ${it.text.take(TRACE)}", it.text.none(::isDevanagari)) }
        hindi.forEach {
            assertTrue("Hindi voice got English: ${it.text.take(TRACE)}", !ENGLISH_RUN.containsMatchIn(it.text))
        }
        // Alternating paragraphs, and English clauses that run on into Hindi sentences.
        val switches = voices.spoken.zipWithNext().count { (a, b) -> a.voice != b.voice }
        assertTrue("only $switches voice switches", switches >= MIN_SWITCHES)
        assertTrue("only $mixedSentences sentences switched voice", mixedSentences >= MIN_MIXED_SENTENCES)
        assertTrue(english.joinToString(" ") { it.text }.contains("Universal Declaration of Human Rights"))
        assertTrue(hindi.joinToString(" ") { it.text }.contains("मानव अधिकारों की सार्वभौम घोषणा"))
        assertTrue(english.joinToString(" ") { it.text }.contains("All human beings are born free"))
    }

    @Test
    fun hindiPartsAreReadWithNumbersAsHindiWords() {
        val voices = RecordingVoices()
        val renderer = EngineRenderer(voices)
        val units = bookUnits()
        units.forEach { renderer.render(it) }

        val hindi = voices.spoken.filter { it.voice == Lang.HI.name }
        hindi.forEach { assertTrue("Hindi voice got digits: ${it.text.take(TRACE)}", it.text.none(Char::isDigit)) }
        // "अनुच्छेद १." heads each article in the Hindi text.
        assertTrue(hindi.any { it.text.contains("अनुच्छेद एक") })
    }

    private fun bookUnits(): List<ReadingUnit> {
        val book = PdfBook.open(PdfiumEngine({ descriptor() }), logContext = "test=bilingual").getOrNull()
        requireNotNull(book) { "bilingual PDF did not open" }
        val builder = ReadingUnitBuilder()
        return book.use { opened ->
            (0 until opened.pageCount).flatMap { page ->
                requireNotNull(opened.readingUnits(page, builder).getOrNull()) { "page $page did not read" }
            }
        }
    }

    /** Assets may be compressed and PDFium needs a seekable descriptor, so the PDF is copied to a file first. */
    private fun descriptor(): ParcelFileDescriptor {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.cacheDir, "test-pdfs/$PDF")
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            instrumentation.context.assets.open("pdfs/$PDF").use { input -> file.outputStream().use(input::copyTo) }
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun String.squeezed() = filterNot(Char::isWhitespace)

    private fun isDevanagari(c: Char) = Character.UnicodeScript.of(c.code) == Character.UnicodeScript.DEVANAGARI

    private companion object {
        const val PDF = "enhi-udhr-bilingual.pdf"
        const val EN_RATE = 22_050
        const val HI_RATE = 16_000

        const val TRACE = 40

        // On the desktop dump of this PDF (pdf/golden) the pipeline gives 186 sentences, 4 of them mixed (preamble
        // clauses, "सामान्य सभा ... Article 1"), and 129 voice switches. A little slack for PDFium on the device.
        const val MIN_MIXED_SENTENCES = 3
        const val MIN_SWITCHES = 100
        val ENGLISH_RUN = Regex("[A-Za-z]+[^\\p{L}]+[A-Za-z]+[^\\p{L}]+[A-Za-z]+")
    }
}
