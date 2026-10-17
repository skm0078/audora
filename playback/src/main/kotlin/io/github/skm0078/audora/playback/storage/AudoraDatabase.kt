// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/storage/AudoraDatabase.kt
// Purpose: The app's Room database: the library, resume anchors, bookmarks, book settings, pronunciation fixes.

package io.github.skm0078.audora.playback.storage

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Schema changes need a version bump and an explicit migration, never a destructive fallback: losing the
 * library and every reading position on an update is worse than a failed build. Schemas are in `schemas/`.
 */
@Database(
    entities = [
        BookEntity::class,
        ResumeAnchorEntity::class,
        BookmarkEntity::class,
        BookSettingsEntity::class,
        PronunciationEntity::class,
    ],
    version = 3,
    exportSchema = true,
    autoMigrations = [
        // 1 → 2 (P1-06): adds the resume_anchors table. Room generates it from the committed schemas.
        AutoMigration(from = 1, to = 2),
    ],
)
internal abstract class AudoraDatabase : RoomDatabase() {
    abstract fun books(): BookDao

    abstract fun resumeAnchors(): ResumeAnchorDao

    abstract fun notes(): NotesDao
}
