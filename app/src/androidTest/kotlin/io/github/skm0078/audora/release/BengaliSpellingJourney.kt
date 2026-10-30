// File: app/src/androidTest/kotlin/io/github/skm0078/audora/release/BengaliSpellingJourney.kt
// Purpose: The release journey's Bengali spelling step on the installed app: a Bengali voice that reads letters is
//          handed its words spelled as said (on the phone's own Unicode library), at the Bengali pace.

package io.github.skm0078.audora.release

import io.github.skm0078.audora.core.config.SpeechConfig
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.speech.sherpa.SherpaFamily
import io.github.skm0078.audora.speech.sherpa.SherpaSpeechEngine
import io.github.skm0078.audora.speech.sherpa.SherpaSynthesizer
import io.github.skm0078.audora.speech.sherpa.SherpaVoiceSpec
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * CI's emulator has no Bengali voice (each is a 115 MB download), so the app's engine reads with a stand-in model that
 * writes down what it is handed. The sentence holds a ড় typed as one character, which the phone's NFC (ICU, not the
 * JVM's) must split for the word to be found in the lists; the voice speaks at [SpeechConfig.bengaliPace].
 */
internal object BengaliSpellingJourney {
    /** "সব চোর বড় হল।" with ড় as one character. */
    private const val WRITTEN = "\u09B8\u09AC \u099A\u09CB\u09B0 \u09AC\u09DC \u09B9\u09B2\u0964"

    /** "সব্ চোর্ বড়ো হলো।": "shob chor boro holo". */
    private const val SAID =
        "\u09B8\u09AC\u09CD \u099A\u09CB\u09B0\u09CD \u09AC\u09A1\u09BC\u09CB \u09B9\u09B2\u09CB\u0964"
    private const val SAMPLE_RATE = 22_050
    private val PACE = SpeechConfig().bengaliPace

    fun spelledAsSaid() {
        val model = Recorder()
        val voice = SherpaVoiceSpec(
            id = "journey-bn-letters",
            label = "Bengali letters",
            lang = Lang.BN,
            family = SherpaFamily.VITS,
            model = "model.onnx",
            tokens = "tokens.txt",
            voices = null,
            dataDir = null,
            speakerId = 0,
            licence = "test",
            contentVersion = "journey",
        )
        val engine = SherpaSpeechEngine(listOf(voice), { Outcome.Success(model) }, bengaliPace = PACE)
        try {
            val rendered = runBlocking { engine.render(WRITTEN, voice.voiceId, rate = 1f) }
            assertTrue("the stand-in voice rendered: $rendered", rendered is Outcome.Success)
            assertEquals("what a Bengali letter voice is handed", listOf(SAID), model.handed)
            assertEquals("the rate it speaks at", listOf(PACE), model.rates)
        } finally {
            engine.release()
        }
    }

    /** A model that renders a moment of silence and writes down each text and rate. */
    private class Recorder : SherpaSynthesizer {
        val handed = mutableListOf<String>()
        val rates = mutableListOf<Float>()
        override val sampleRate: Int = SAMPLE_RATE

        override fun generate(text: String, speakerId: Int, rate: Float, style: Int?, keepGoing: () -> Boolean) =
            FloatArray(SAMPLE_RATE / 10).also {
                handed += text
                rates += rate
            }

        override fun release() = Unit
    }
}
