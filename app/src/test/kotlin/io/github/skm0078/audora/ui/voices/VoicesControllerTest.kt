// File: app/src/test/kotlin/io/github/skm0078/audora/ui/voices/VoicesControllerTest.kt
// Purpose: Voices screen rules: the list, choosing a reading voice, confirming deletes, imports, samples and messages.

package io.github.skm0078.audora.ui.voices

import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.speech.voices.VoiceKind
import io.github.skm0078.audora.speech.voices.VoiceRow
import io.github.skm0078.audora.speech.voices.VoiceStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoicesControllerTest {
    private val manager = FakeVoiceManager()

    private fun TestScope.controller() = VoicesController(manager, backgroundScope).also { it.start() }

    @Test
    fun `the list follows the manager`() = runTest(UnconfinedTestDispatcher()) {
        val controller = controller()
        assertEquals(listOf(BUILT_IN, CORI), controller.state.value.rows)

        manager.rows.value = listOf(BUILT_IN)
        assertEquals(listOf(BUILT_IN), controller.state.value.rows)
    }

    @Test
    fun `a list that cannot load says so`() = runTest(UnconfinedTestDispatcher()) {
        manager.failRows = true

        assertTrue(controller().state.value.loadFailed)
    }

    @Test
    fun `choosing the built-in voice means the default, any other voice is saved`() =
        runTest(UnconfinedTestDispatcher()) {
            val controller = controller()

            controller.use(CORI)
            controller.use(BUILT_IN)
            controller.useDefault(Lang.HI)

            assertEquals(listOf(Lang.EN to CORI.id, Lang.EN to null, Lang.HI to null), manager.choices)
        }

    @Test
    fun `a sample loads, plays for its length, and a second tap stops it`() = runTest(UnconfinedTestDispatcher()) {
        val sampler = FakeSampler()
        val controller = VoicesController(manager, backgroundScope, sampler).also { it.start() }

        controller.toggleSample(CORI)
        assertEquals(VoiceSample(CORI.id.name, playing = false), controller.state.value.sample)
        sampler.plays.complete(SAMPLE_MS)
        assertEquals(VoiceSample(CORI.id.name, playing = true), controller.state.value.sample)
        testScheduler.advanceTimeBy(SAMPLE_MS + 1)
        assertNull(controller.state.value.sample)

        controller.toggleSample(CORI)
        controller.toggleSample(CORI)
        assertNull(controller.state.value.sample)
        assertEquals(3, sampler.stops) // each start stops what was before, then the second tap
    }

    @Test
    fun `a sample that cannot play says so`() = runTest(UnconfinedTestDispatcher()) {
        val sampler = FakeSampler().apply { plays.complete(null) }
        val controller = VoicesController(manager, backgroundScope, sampler).also { it.start() }

        controller.toggleSample(BUILT_IN)

        assertNull(controller.state.value.sample)
        assertEquals(VoicesMessage.SampleFailed, controller.state.value.message)
    }

    private class FakeSampler : VoiceSampler {
        val plays = CompletableDeferred<Long?>()
        var stops = 0

        override suspend fun play(row: VoiceRow): Long? = plays.await()

        override fun stop() {
            stops++
        }
    }

    @Test
    fun `download and cancel pass the voice id`() = runTest(UnconfinedTestDispatcher()) {
        val controller = controller()

        controller.download(CORI)
        controller.cancel(CORI)

        assertEquals(listOf("download piper-en-cori", "cancel piper-en-cori"), manager.calls)
    }

    @Test
    fun `a delete waits for confirmation and reports the outcome`() = runTest(UnconfinedTestDispatcher()) {
        val controller = controller()
        controller.confirmDelete()
        assertTrue(manager.calls.isEmpty())

        controller.proposeDelete(CORI)
        assertEquals(CORI, controller.state.value.pendingDelete)
        controller.confirmDelete()

        assertNull(controller.state.value.pendingDelete)
        assertEquals(listOf("delete piper-en-cori"), manager.calls)
        assertEquals(VoicesMessage.Deleted("Cori"), controller.state.value.message)

        manager.deleteResult = Outcome.Failure("busy")
        controller.proposeDelete(CORI)
        controller.confirmDelete()
        assertEquals(VoicesMessage.DeleteFailed("Cori"), controller.state.value.message)

        controller.messageShown()
        assertNull(controller.state.value.message)
    }

    @Test
    fun `an import blocks a second one until it ends`() = runTest(UnconfinedTestDispatcher()) {
        val controller = controller()
        val result = CompletableDeferred<Outcome<String, String>>()
        manager.importResult = result

        controller.import("content://tree/1", Lang.HI, "Mine")
        controller.import("content://tree/2", Lang.HI, "Again")
        assertTrue(controller.state.value.importing)

        result.complete(Outcome.Success("Mine"))

        assertFalse(controller.state.value.importing)
        assertEquals(VoicesMessage.Imported("Mine"), controller.state.value.message)
        assertEquals(listOf("import content://tree/1 HI Mine"), manager.calls)
    }

    @Test
    fun `a failed import shows its reason`() = runTest(UnconfinedTestDispatcher()) {
        val controller = controller()
        manager.importResult = CompletableDeferred(Outcome.Failure("No model file"))

        controller.import("content://tree/1", Lang.EN, "")

        assertEquals(VoicesMessage.ImportFailed("No model file"), controller.state.value.message)
    }

    private class FakeVoiceManager : VoiceManager {
        val rows = MutableStateFlow(listOf(BUILT_IN, CORI))
        var failRows = false
        val calls = mutableListOf<String>()
        val choices = mutableListOf<Pair<Lang, VoiceId?>>()
        var deleteResult: Outcome<Unit, String> = Outcome.Success(Unit)
        var importResult = CompletableDeferred<Outcome<String, String>>(Outcome.Success("x"))

        override fun rows(): Flow<List<VoiceRow>> = if (failRows) flow { error("disk") } else rows

        override fun download(id: String) {
            calls += "download $id"
        }

        override fun cancel(id: String) {
            calls += "cancel $id"
        }

        override suspend fun delete(id: String): Outcome<Unit, String> = deleteResult.also { calls += "delete $id" }

        override fun choose(lang: Lang, voice: VoiceId?) {
            choices += lang to voice
        }

        override suspend fun import(treeUri: String, lang: Lang, label: String): Outcome<String, String> {
            calls += "import $treeUri ${lang.name} $label"
            return importResult.await()
        }
    }

    private companion object {
        const val SAMPLE_MS = 2_000L
        val BUILT_IN = row("kitten-en-nano", "Nano", VoiceKind.BUILT_IN, VoiceStatus.BuiltIn)
        val CORI = row("piper-en-cori", "Cori", VoiceKind.CATALOG, VoiceStatus.Installed)

        fun row(id: String, label: String, kind: VoiceKind, status: VoiceStatus) =
            VoiceRow(VoiceId(EngineId.SHERPA, id), label, Lang.EN, "licence", 0, kind, status, readsLanguage = false)
    }
}
