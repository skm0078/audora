// File: app/src/main/kotlin/io/github/skm0078/audora/ui/storage/StorageRow.kt
// Purpose: What the Storage screen lists (UI plan, non-functional: every MB visible and clearable): one row per
//          kind of stored data, its size and what freeing it does.

package io.github.skm0078.audora.ui.storage

import io.github.skm0078.audora.speech.ocr.OcrModelsState
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

enum class StorageKind { VOICES, OCR, PUNCTUATION, PAGE_PICTURES }

/** The rows' total, counting only the measured ones. */
fun storageTotal(rows: List<StorageRow>): Long = rows.sumOf { it.bytes ?: 0L }

/** Every file under [dir], in bytes; 0 for a missing folder. Walks the disk, so call it off the main thread. */
fun directoryBytes(dir: File): Long = dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

/**
 * The screen's rows from what each owner reports: downloaded voices (the built-in one is part of the app), the OCR
 * and punctuation packs when present, and the page pictures cache. Null inputs are still loading, unless their kind
 * is in [failed]: its owner could not report a size.
 */
fun storageRows(
    voices: List<VoiceRow>?,
    ocr: OcrModelsState?,
    punctuation: OcrModelsState?,
    pagePictures: Long?,
    failed: Set<StorageKind> = emptySet(),
): List<StorageRow> = listOf(
    StorageRow(StorageKind.VOICES, voices?.let(::downloadedVoiceBytes), StorageAction.MANAGE_VOICES),
    StorageRow(StorageKind.OCR, ocr?.let(::packBytes), StorageAction.DELETE),
    StorageRow(StorageKind.PUNCTUATION, punctuation?.let(::packBytes), StorageAction.DELETE),
    StorageRow(StorageKind.PAGE_PICTURES, pagePictures, StorageAction.CLEAR),
).map { row -> if (row.bytes == null && row.kind in failed) row.copy(failed = true) else row }

private fun downloadedVoiceBytes(rows: List<VoiceRow>): Long = rows
    .filter { it.status == VoiceStatus.Installed || it.status == VoiceStatus.UpdateAvailable }
    .sumOf { it.sizeBytes }

private fun packBytes(state: OcrModelsState): Long = (state as? OcrModelsState.Ready)?.bytes ?: 0L
