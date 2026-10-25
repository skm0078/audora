// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/sherpa/AssetDirectoryInstallerTest.kt
// Purpose: espeak-ng-data install: first copy, skip when current, refresh on a new version, recover from a dead copy.

package io.github.skm0078.audora.speech.sherpa

import io.github.skm0078.audora.core.result.errorOrNull
import io.github.skm0078.audora.core.result.getOrNull
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AssetDirectoryInstallerTest {
    @get:Rule
    val temp = TemporaryFolder()

    private class FakeAssets(var files: Map<String, String>) : AssetTree {
        var opened = 0

        override fun list(path: String): List<String> = files.keys
            .filter { it.startsWith("$path/") }
            .map { it.removePrefix("$path/").substringBefore('/') }
            .distinct()

        override fun open(path: String): InputStream {
            opened++
            val text = files[path] ?: throw FileNotFoundException(path)
            return ByteArrayInputStream(text.toByteArray())
        }
    }

    private val assets = FakeAssets(
        mapOf(
            "voices/bundled/espeak-ng-data/phontab" to "phonemes",
            "voices/bundled/espeak-ng-data/en_dict" to "english",
            "voices/bundled/espeak-ng-data/voices/!v/f1" to "variant",
        ),
    )

    @Test
    fun `copies the tree on first use`() {
        val target = File(temp.root, "espeak-ng-data")

        val installed = AssetDirectoryInstaller(assets).install("voices/bundled/espeak-ng-data", target, "v1")

        assertEquals(target, installed.getOrNull())
        assertEquals("phonemes", File(target, "phontab").readText())
        assertEquals("variant", File(target, "voices/!v/f1").readText())
        assertFalse(File(temp.root, "espeak-ng-data.partial").exists())
    }

    @Test
    fun `does nothing when the installed version is current`() {
        val target = File(temp.root, "espeak-ng-data")
        val installer = AssetDirectoryInstaller(assets)
        installer.install("voices/bundled/espeak-ng-data", target, "v1")
        val opened = assets.opened

        installer.install("voices/bundled/espeak-ng-data", target, "v1")

        assertEquals(opened, assets.opened)
    }

    @Test
    fun `a new version replaces the old tree, dropping files it no longer has`() {
        val target = File(temp.root, "espeak-ng-data")
        val installer = AssetDirectoryInstaller(assets)
        installer.install("voices/bundled/espeak-ng-data", target, "v1")
        assets.files = mapOf("voices/bundled/espeak-ng-data/phontab" to "phonemes 2")

        installer.install("voices/bundled/espeak-ng-data", target, "v2")

        assertEquals("phonemes 2", File(target, "phontab").readText())
        assertFalse(File(target, "en_dict").exists())
    }

    @Test
    fun `a copy killed halfway is redone, never mistaken for complete`() {
        val target = File(temp.root, "espeak-ng-data")
        File(temp.root, "espeak-ng-data.partial/phontab").apply { parentFile.mkdirs() }.writeText("half")

        AssetDirectoryInstaller(assets).install("voices/bundled/espeak-ng-data", target, "v1")

        assertEquals("phonemes", File(target, "phontab").readText())
        assertFalse(File(temp.root, "espeak-ng-data.partial").exists())
    }

    @Test
    fun `a missing source is a failure and leaves nothing behind`() {
        val target = File(temp.root, "espeak-ng-data")

        val error = AssetDirectoryInstaller(assets).install("voices/bundled/nothing", target, "v1").errorOrNull()

        assertNotNull(error)
        assertTrue(error.orEmpty().contains("voices/bundled/nothing"))
        assertFalse(target.exists())
        assertFalse(File(temp.root, "espeak-ng-data.partial").exists())
    }

    @Test
    fun `two installers copying at once leave one complete folder`() {
        val target = File(temp.root, "espeak-ng-data")
        // Slow opens widen the window in which two unserialised copies would share one staging folder.
        val slow = object : AssetTree by assets {
            override fun open(path: String): InputStream {
                Thread.sleep(SLOW_OPEN_MS)
                return assets.open(path)
            }
        }
        val threads = List(2) {
            Thread { AssetDirectoryInstaller(slow).install("voices/bundled/espeak-ng-data", target, "v1") }
        }
        threads.forEach(Thread::start)
        threads.forEach(Thread::join)

        assertEquals("phonemes", File(target, "phontab").readText())
        assertEquals("english", File(target, "en_dict").readText())
        assertEquals("variant", File(target, "voices/!v/f1").readText())
        assertFalse(File(temp.root, "espeak-ng-data.partial").exists())
    }

    private companion object {
        const val SLOW_OPEN_MS = 20L
    }
}
