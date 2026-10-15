// File: spikes/voices/src/test/kotlin/io/github/skm0078/audora/spike/voices/bench/BenchPlanTest.kt
// Purpose: Only installed voices are planned, at every thread count, plus one system-voice run per language.

package io.github.skm0078.audora.spike.voices.bench

import io.github.skm0078.audora.spike.voices.catalog.BenchText
import io.github.skm0078.audora.spike.voices.catalog.Catalog
import io.github.skm0078.audora.spike.voices.catalog.ModelFamily
import io.github.skm0078.audora.spike.voices.catalog.TextKind
import io.github.skm0078.audora.spike.voices.catalog.VoiceSpec
import org.junit.Assert.assertEquals
import org.junit.Test

class BenchPlanTest {
    private val catalog = Catalog(
        version = 1,
        modelBaseUrl = "https://example.invalid/",
        texts = listOf(
            BenchText("en-first", "en", TextKind.FIRST, "x"),
            BenchText("hi-first", "hi", TextKind.FIRST, "y"),
        ),
        voices = listOf(
            VoiceSpec("a", "A", "en", ModelFamily.VITS, "arch-a", model = "a.onnx"),
            VoiceSpec("b", "B", "en", ModelFamily.VITS, "arch-b", model = "b.onnx"),
            VoiceSpec("c", "C", "hi", ModelFamily.SUPERTONIC, "arch-c", extraLang = "hi"),
        ),
    )

    @Test
    fun `plans installed voices per thread count and a system run per language`() {
        val plan = BenchPlan.build(catalog, isInstalled = { it != "arch-b" }, BenchConfig(threadCounts = listOf(2, 4)))

        val described = plan.map {
            when (it) {
                is PlannedRun.Neural -> "${it.spec.id}@${it.threads}${if (it.saveSample) "*" else ""}"
                is PlannedRun.System -> "system-${it.lang}"
            }
        }
        assertEquals(listOf("a@2", "a@4*", "system-en", "c@2", "c@4*", "system-hi"), described)
    }

    @Test
    fun `archives are listed once even when voices share them`() {
        val shared = catalog.copy(
            voices =
                catalog.voices + VoiceSpec("d", "D", "hi", ModelFamily.SUPERTONIC, "arch-c", extraLang = "hi"),
        )

        assertEquals(listOf("arch-a", "arch-b", "arch-c"), shared.archivesFor(listOf("en", "hi")))
    }
}
