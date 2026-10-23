// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/storage/AudoraDatabase.kt
// Purpose: The app's Room database: library, resume anchors, bookmarks, settings, fixes, highlights, voice notes.

package io.github.skm0078.audora.playback.storage

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

/** The schema version; a backup records it and a restore refuses a newer one (ADR-046). */
internal const val DATABASE_VERSION = 4

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
        HighlightEntity::class,
        VoiceNoteEntity::class,
    ],
    version = DATABASE_VERSION,
    exportSchema = true,
    autoMigrations = [
        // 1 → 2 (P1-06): adds the resume_anchors table. Room generates it from the committed schemas.
        AutoMigration(from = 1, to = 2),
        // 2 → 3 (plan items 29, 4, 1): adds bookmarks, book_settings and pronunciations; existing rows untouched.
        AutoMigration(from = 2, to = 3),
        // 3 → 4 (plan items 30, 32): adds highlights and voice_notes; existing rows untouched. Only tables are
        // added, so Room's generated migration is exact; the device test checks it against the committed schemas.
        AutoMigration(from = 3, to = 4),
    ],
)
internal abstract class AudoraDatabase : RoomDatabase() {
    abstract fun books(): BookDao

    abstract fun resumeAnchors(): ResumeAnchorDao

    abstract fun notes(): NotesDao

    abstract fun marks(): MarksDao
}
