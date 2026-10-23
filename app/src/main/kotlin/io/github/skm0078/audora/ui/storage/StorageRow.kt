// File: app/src/main/kotlin/io/github/skm0078/audora/ui/storage/StorageRow.kt
// Purpose: What the Storage screen lists (UI plan, non-functional: every MB visible and clearable): one row per
//          kind of stored data, its size and what freeing it does.

package io.github.skm0078.audora.ui.storage

import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.speech.voices.VoiceRow
import io.github.skm0078.audora.speech.voices.VoiceStatus
import java.io.File

/** What a row's button does; each owner's own call frees the space. */
enum class StorageAction {
    /** Opens Settings → Voices, where each voice is deleted on its own. */
    MANAGE_VOICES,

    /** Deletes a downloaded model pack; it can be downloaded again. */
    DELETE,

    /** Empties a cache that Audora rebuilds by itself when needed. */
    CLEAR,
}

/**
 * @property kind which data, for its label and its action's target.
 * @property bytes its size on the phone; null while it is still being measured, or if measuring failed.
 * @property failed its owner could not report a size; the row says so instead of "Measuring…" forever.
 */
data class StorageRow(val kind: StorageKind, val bytes: Long?, val action: StorageAction?, val failed: Boolean = false)

/** Which data a row is; the order is the screen's: downloads first, then caches, then the copies books keep. */
enum class StorageKind {
    VOICES,
    OCR,
    PUNCTUATION,
    VOICE_COMMANDS,
    DICTIONARIES,
    READING_TUTOR,
    PAGE_PICTURES,
    OCR_TEXT,
    COVERS,
    VOICE_BLENDS,
    STUDIO_AUDIO,
    SEARCH_INDEXES,
    SHARED_PDFS,
}

/**
 * What freeing each kind does. A shared PDF's copy has none: it is the book itself (a PDF shared from an app that
 * would not let Audora open it again) and goes when its book is removed.
 */
fun actionOf(kind: StorageKind): StorageAction? = when (kind) {
    StorageKind.VOICES -> StorageAction.MANAGE_VOICES

    StorageKind.OCR, StorageKind.PUNCTUATION, StorageKind.VOICE_COMMANDS, StorageKind.DICTIONARIES,
    StorageKind.READING_TUTOR,
    -> StorageAction.DELETE

    StorageKind.PAGE_PICTURES, StorageKind.OCR_TEXT, StorageKind.COVERS, StorageKind.VOICE_BLENDS,
    StorageKind.STUDIO_AUDIO, StorageKind.SEARCH_INDEXES,
    -> StorageAction.CLEAR

    StorageKind.SHARED_PDFS -> null
}

/**
 * How one kind of data is measured and, unless its row is size only, freed. Both are the owner's own calls and
 * switch to a background thread themselves.
 */
class StorageOwner(val measure: suspend () -> Long, val free: (suspend () -> Outcome<Unit, String>)? = null)

/** The rows' total, counting only the measured ones. */
fun storageTotal(rows: List<StorageRow>): Long = rows.sumOf { it.bytes ?: 0L }

/** Every file under [dir], in bytes; 0 for a missing folder. Walks the disk, so call it off the main thread. */
fun directoryBytes(dir: File): Long = dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

/**
 * The screen's rows from what each owner reports: downloaded voices (the built-in one is part of the app) and every
 * other kind's size on disk as measured into [sizes]. A model pack counts what is on disk, so a half-finished
 * download shows and can be deleted too. Null [voices] and a kind missing from [sizes] are still measuring, unless
 * the kind is in [failed]: its owner could not report a size.
 */
fun storageRows(
    voices: List<VoiceRow>?,
    sizes: Map<StorageKind, Long>,
    failed: Set<StorageKind> = emptySet(),
): List<StorageRow> = StorageKind.entries.map { kind ->
    val bytes = if (kind == StorageKind.VOICES) voices?.let(::downloadedVoiceBytes) else sizes[kind]
    StorageRow(kind, bytes, actionOf(kind), failed = bytes == null && kind in failed)
}

private fun downloadedVoiceBytes(rows: List<VoiceRow>): Long = rows
    .filter { it.status == VoiceStatus.Installed || it.status == VoiceStatus.UpdateAvailable }
    .sumOf { it.sizeBytes }
