// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/voices/ReadingVoicesTest.kt
// Purpose: The reading-voice choices: saved at once, unavailable choices fall back, deleted voices are forgotten.

package io.github.skm0078.audora.speech.voices

import io.github.skm0078.audora.core.model.Lang
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReadingVoicesTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val file by lazy { File(temp.root, "choices.properties") }
    private val voices by lazy { ReadingVoices(VoicePreferences(file)) }
    private val cori = voiceSpec("piper-en-cori").voiceId
    private val hindi = voiceSpec("custom-hi-1", Lang.HI).voiceId

    @Test
    fun `a choice is saved and read back by a new instance`() {
        voices.choose(Lang.EN, cori)

        assertEquals(mapOf(Lang.EN to cori), voices.choices.value)
        assertEquals(mapOf(Lang.EN to cori), ReadingVoices(VoicePreferences(file)).current())
    }

    @Test
    fun `a choice that can no longer speak means the default`() {
        voices.choose(Lang.EN, cori)

        assertEquals(cori, voices.preferred(Lang.EN) { true })
        assertNull(voices.preferred(Lang.EN) { false })
        assertNull(voices.preferred(Lang.HI) { true })
    }

    @Test
    fun `choosing null or forgetting a deleted voice goes back to the default`() {
        voices.choose(Lang.EN, cori)
        voices.choose(Lang.HI, hindi)
        voices.choose(Lang.BN, hindi)

        voices.choose(Lang.EN, null)
        voices.forget(hindi)

        assertEquals(emptyMap<Lang, Any>(), voices.current())
    }
}
