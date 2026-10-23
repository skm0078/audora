// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/sherpa/BlendCacheTest.kt
// Purpose: The narrator blend (item 75) goes on the narrator's Kokoro voice only, and its copy is reused, rebuilt
// for a new voice version, refused without space and cleared.

package io.github.skm0078.audora.speech.sherpa

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.result.Outcome
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BlendCacheTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val voiceDir by lazy { temp.newFolder("voices") }
    private val root by lazy { File(temp.root, "blends") }

    private fun kokoro(id: String = "kokoro-en", speakerId: Int = 3, version: String = "aaaaaaaaaaaa1111") =
        spec(id, Lang.EN, speakerId).copy(
            family = SherpaFamily.KOKORO,
            voices = "voices.bin",
            engineLang = "en-us",
            contentVersion = version,
        )

    private fun writeVoices(speakers: Int = 4) {
        File(voiceDir, "voices.bin").writeBytes(ByteArray(speakers * KokoroBlends.SPEAKER_BYTES))
    }

    @Test
    fun `the blend goes on the narrator's Kokoro voice only, with another of its English speakers`() {
        val narrator = kokoro(id = "kokoro-en-heart") // a voice the speaker tables are keyed on
        val quotes = kokoro(id = "kokoro-quotes")
        val piper = spec("piper-en")
        val specs = listOf(narrator, quotes, piper)
        val blend = StyleBlend(withSpeaker = 12, amount = 0.3f)

        val applied = specs.withNarratorBlend(NarratorBlend(narrator.voiceId, blend))

        assertEquals(listOf(blend, null, null), applied.map { it.blend })
        val refused = listOf(
            NarratorBlend(narrator.voiceId, StyleBlend(withSpeaker = 3, amount = 0.3f)), // its own speaker
            NarratorBlend(narrator.voiceId, StyleBlend(withSpeaker = 99, amount = 0.3f)), // not in the model
            NarratorBlend(piper.voiceId, blend), // not Kokoro
        )
        refused.forEach { assertEquals(specs, specs.withNarratorBlend(it)) }
        assertEquals(specs, specs.withNarratorBlend(null))
    }

    @Test
    fun `an imported Kokoro voice gets no blend, since no speaker table is keyed on it`() {
        val imported = kokoro(id = "custom-my-kokoro-1a2b")
        val specs = listOf(imported)

        assertEquals(specs, specs.withNarratorBlend(NarratorBlend(imported.voiceId, StyleBlend(12, 0.3f))))
    }

    @Test
    fun `a copy is reused, rebuilt for a new voice version, and the old copy and temp files go`() {
        writeVoices()
        val cache = BlendCache(root, headroomBytes = 0)
        val spec = kokoro().copy(blend = StyleBlend(1, 0.25f))

        val first = requireNotNull(cache.voicesFor(spec, voiceDir))
        assertEquals("3-1-25-aaaaaaaaaaaa.bin", first.name)
        first.setLastModified(MARK)
        assertEquals(first, cache.voicesFor(spec, voiceDir))
        assertEquals(MARK, first.lastModified())

        File(first.parentFile, "left-by-a-kill.tmp").writeText("x")
        val updated = requireNotNull(cache.voicesFor(spec.copy(contentVersion = "bbbbbbbbbbbb2222"), voiceDir))
        assertEquals(listOf(updated.name), first.parentFile!!.list()!!.toList())
        assertNull(cache.report.neededBytes)
    }

    @Test
    fun `without the space it needs the blend is not applied and says how much it needs`() {
        writeVoices()
        val cache = BlendCache(root, headroomBytes = Long.MAX_VALUE / 2)

        assertNull(cache.voicesFor(kokoro().copy(blend = StyleBlend(1, 0.5f)), voiceDir))
        assertEquals(4L * KokoroBlends.SPEAKER_BYTES + Long.MAX_VALUE / 2, cache.report.neededBytes)
        assertFalse(root.exists() && root.walk().any { it.isFile })
    }

    @Test
    fun `a stale copy is deleted before the space check, so its space counts for the new one`() {
        writeVoices()
        val spec = kokoro().copy(blend = StyleBlend(1, 0.5f))
        val old = requireNotNull(BlendCache(root, headroomBytes = 0).voicesFor(spec, voiceDir))
        val tight = BlendCache(root, headroomBytes = Long.MAX_VALUE / 2)
        assertTrue(tight.holds(spec.id))

        assertNull(tight.voicesFor(spec.copy(contentVersion = "bbbbbbbbbbbb2222"), voiceDir))
        assertFalse(old.exists())
    }

    @Test
    fun `size and clear cover one voice and every voice`() {
        writeVoices()
        val cache = BlendCache(root, headroomBytes = 0)
        cache.voicesFor(kokoro().copy(blend = StyleBlend(1, 0.5f)), voiceDir)
        cache.voicesFor(kokoro(id = "kokoro-b").copy(blend = StyleBlend(2, 0.5f)), voiceDir)
        assertEquals(2L * 4 * KokoroBlends.SPEAKER_BYTES, cache.sizeBytes())

        cache.clear("kokoro-en")
        assertEquals(4L * KokoroBlends.SPEAKER_BYTES, cache.sizeBytes())
        assertTrue(cache.clearAll() is Outcome.Success)
        assertEquals(0L, cache.sizeBytes())
    }

    @Test
    fun `builds of one copy from two threads end with one finished copy`() {
        writeVoices()
        val cache = BlendCache(root, headroomBytes = 0)
        val spec = kokoro().copy(blend = StyleBlend(1, 0.5f))
        val made = arrayOfNulls<File>(2)

        val threads = (0..1).map { i -> Thread { made[i] = BlendCache.locked { cache.voicesFor(spec, voiceDir) } } }
        threads.forEach(Thread::start)
        threads.forEach(Thread::join)

        assertEquals(made[0], made[1])
        assertEquals(listOf(made[0]!!.name), made[0]!!.parentFile!!.list()!!.toList())
    }

    private companion object {
        const val MARK = 1_600_000_000_000L
    }
}
