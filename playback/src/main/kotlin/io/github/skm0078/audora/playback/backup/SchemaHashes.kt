// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/backup/SchemaHashes.kt
// Purpose: Room's identity hash of each database version the app has shipped, to check a restored database by.

package io.github.skm0078.audora.playback.backup

/**
 * Room writes its schema's identity hash into every database (room_master_table) and checks it, after migrating, at
 * every start. A restore compares the staged database's hash with the one this app's schema had at that version, so a
 * foreign or damaged database is refused before it replaces the library, not after. The values repeat
 * playback/schemas/.../<version>.json; a JVM test checks they match. A new schema version adds its hash here.
 */
object SchemaHashes {
    @Suppress("MagicNumber") // Schema versions, named by what they are.
    private val BY_VERSION = mapOf(
        1 to "ebac7fab0df2390df4e5a769185fe3f7",
        2 to "3fa4c1b0eb10585f2cf1f5ed22fece69",
        3 to "b8d414157d2d420dee72a39fe13e82b2",
        4 to "75479c918e5c90c007f54eec1431c6ef",
    )

    /** The hash Room wrote at [version]; null for a version this app never had. */
    fun of(version: Int): String? = BY_VERSION[version]

    /** Every version with a known hash. */
    val versions: Set<Int> get() = BY_VERSION.keys
}
