// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/sherpa/FakeSynthesizer.kt
// Purpose: Test double for sherpa-onnx: scripted output, call log, and a gate to hold a render mid-flight.

package io.github.skm0078.audora.speech.sherpa

import io.github.skm0078.audora.core.model.Lang
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class FakeSynthesizer(
    override val sampleRate: Int = 22_050,
    private val output: FloatArray = floatArrayOf(0.5f, -0.5f),
) : SherpaSynthesizer {
    val calls = mutableListOf<Pair<String, Int>>()
    var released = false
        private set

    /** When set, generate() counts down [entered] and then polls keepGoing until [gate] opens or it says stop. */
    var gate: CountDownLatch? = null
    val entered = CountDownLatch(1)
    var stoppedEarly = false
        private set
    var failure: RuntimeException? = null

    override fun generate(text: String, speakerId: Int, keepGoing: () -> Boolean): FloatArray {
        check(!released) { "generate after release" }
        calls += text to speakerId
        failure?.let { throw it }
        val wait = gate
        if (wait != null) {
            entered.countDown()
            while (!wait.await(POLL_MS, TimeUnit.MILLISECONDS)) {
                if (!keepGoing()) {
                    stoppedEarly = true
                    break
                }
            }
        }
        return output
    }

    override fun release() {
        released = true
    }

    private companion object {
        const val POLL_MS = 5L
    }
}

fun spec(id: String, lang: Lang = Lang.EN, speakerId: Int = 0) = SherpaVoiceSpec(
    id = id,
    label = id,
    lang = lang,
    family = SherpaFamily.VITS,
    model = "$id.onnx",
    tokens = "tokens.txt",
    voices = null,
    dataDir = "espeak-ng-data",
    speakerId = speakerId,
    licence = "test",
    contentVersion = "v1",
)
