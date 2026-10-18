// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/sherpa/BlendFallbackTest.kt
// Purpose: A refused narrator blend falls back to the voice as installed; an accepted one is used.

package io.github.skm0078.audora.speech.sherpa

import io.github.skm0078.audora.core.result.Outcome
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class BlendFallbackTest {
    private val blend = File("blend.bin")
    private val tried = mutableListOf<File?>()
    private var refusals = 0
    private val reasons = mutableListOf<String>()

    private fun load(refuseBlend: Boolean, installedFails: Boolean = false, blended: File? = blend) =
        loadPreferringBlend(
            blended,
            create = { voices: File? ->
                tried += voices
                when {
                    voices != null && refuseBlend -> Outcome.Failure("blend refused")
                    voices == null && installedFails -> Outcome.Failure("model refused")
                    else -> Outcome.Success(voices?.name ?: "installed")
                }
            },
            onRefused = { error ->
                refusals++
                reasons += error
            },
        )

    @Test
    fun `an accepted blend is used, and the installed voice is never loaded`() {
        assertEquals(Outcome.Success("blend.bin"), load(refuseBlend = false))
        assertEquals(listOf<File?>(blend), tried)
        assertEquals(0, refusals)
    }

    @Test
    fun `a refused blend is reported and the voice loads as installed`() {
        assertEquals(Outcome.Success("installed"), load(refuseBlend = true))
        assertEquals(listOf(blend, null), tried)
        assertEquals(1, refusals)
        assertEquals(listOf("blend refused"), reasons)
    }

    @Test
    fun `with no blend the voice loads once, and its own failure is returned as is`() {
        assertEquals(Outcome.Failure("model refused"), load(refuseBlend = false, installedFails = true, blended = null))
        assertEquals(listOf<File?>(null), tried)
        assertEquals(0, refusals)
    }

    @Test
    fun `when the installed voice fails too, that failure is returned`() {
        assertEquals(Outcome.Failure("model refused"), load(refuseBlend = true, installedFails = true))
        assertEquals(1, refusals)
    }
}
