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

        // Each version first, so a schema bump (Room exports the new version at every build) fails with the exact
        // line to add: a thread without an Android build can read it from the CI log.
        exported.toSortedMap().forEach { (version, hash) ->
            assertEquals("add $version to \"$hash\" in SchemaHashes", hash, SchemaHashes.of(version))
        }
        assertEquals((1..exported.keys.max()).toSet(), exported.keys)
        assertEquals(exported.keys, SchemaHashes.versions)
    }
}
