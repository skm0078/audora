// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/storage/AudoraDatabase.kt
// Purpose: The app's Room database. P1-06 adds resume anchors here, next to the books they belong to.

package io.github.skm0078.audora.playback.storage

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Schema changes need a version bump and an explicit migration, never a destructive fallback: losing the
 * library and every reading position on an update is worse than a failed build. Schemas are in `schemas/`.
 */
@Database(entities = [BookEntity::class], version = 1, exportSchema = true)
internal abstract class AudoraDatabase : RoomDatabase() {
    abstract fun books(): BookDao
}
