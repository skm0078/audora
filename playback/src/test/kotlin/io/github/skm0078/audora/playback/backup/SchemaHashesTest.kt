// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/backup/SchemaHashesTest.kt
// Purpose: The restore's known schema hashes match Room's exported schemas, one per version, none missing.

package io.github.skm0078.audora.playback.backup

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class SchemaHashesTest {
    // Gradle runs unit tests in the module's folder.
    private val schemas = File("schemas/io.github.skm0078.audora.playback.storage.AudoraDatabase")

    @Test
    fun `every exported schema's identity hash is known`() {
        val exported = schemas.listFiles().orEmpty().associate { file ->
            file.nameWithoutExtension.toInt() to Regex("\"identityHash\":\\s*\"([0-9a-f]+)\"").find(file.readText())!!
                .groupValues[1]
        }

        // Room exports the current version's schema at every build, so a new version without a hash fails here.
        assertEquals((1..exported.keys.max()).toSet(), exported.keys)
        assertEquals(exported.keys, SchemaHashes.versions)
        exported.forEach { (version, hash) -> assertEquals("version $version", hash, SchemaHashes.of(version)) }
    }
}
