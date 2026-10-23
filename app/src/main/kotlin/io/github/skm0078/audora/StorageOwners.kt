// File: app/src/main/kotlin/io/github/skm0078/audora/StorageOwners.kt
// Purpose: Settings → Storage's owners: each kind of stored data's size and free call, from the owner that keeps it.

package io.github.skm0078.audora

import android.content.Context
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.playback.documents.SharedPdfCopies
import io.github.skm0078.audora.playback.ocr.OcrTextCacheFiles
import io.github.skm0078.audora.playback.search.SearchIndexes
import io.github.skm0078.audora.playback.studio.StudioMode
import io.github.skm0078.audora.speech.SpeechEngines
import io.github.skm0078.audora.speech.dictionary.Dictionaries
import io.github.skm0078.audora.speech.kws.VoiceCommandModels
import io.github.skm0078.audora.speech.kws.sizeOnDisk
import io.github.skm0078.audora.speech.listen.ListenModels
import io.github.skm0078.audora.speech.listen.deleteAllModels
import io.github.skm0078.audora.speech.listen.storedBytes
import io.github.skm0078.audora.speech.ocr.OcrModels
import io.github.skm0078.audora.speech.ocr.sizeOnDisk
import io.github.skm0078.audora.speech.punct.PunctuationModels
import io.github.skm0078.audora.speech.punct.sizeOnDisk
import io.github.skm0078.audora.ui.library.BookCovers
import io.github.skm0078.audora.ui.storage.StorageKind
import io.github.skm0078.audora.ui.storage.StorageOwner
import io.github.skm0078.audora.ui.storage.directoryBytes
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Settings → Storage (UI plan): each kind's size and free call, from the owner that keeps it. */
internal fun storageOwners(
    context: Context,
    covers: BookCovers,
    sharedCopies: SharedPdfCopies,
    searchIndexes: SearchIndexes,
): Map<StorageKind, StorageOwner> {
    val ocr = OcrModels.get(context)
    val punctuation = PunctuationModels.get(context)
    val voiceCommands = VoiceCommandModels.get(context)
    val pictures = File(context.cacheDir, THUMBNAILS_DIR)
    return mapOf(
        StorageKind.OCR to StorageOwner({ ocr.sizeOnDisk() }, ocr::delete),
        StorageKind.PUNCTUATION to StorageOwner({ punctuation.sizeOnDisk() }, punctuation::delete),
        StorageKind.VOICE_COMMANDS to StorageOwner({ voiceCommands.sizeOnDisk() }, voiceCommands::delete),
        StorageKind.PAGE_PICTURES to StorageOwner(
            measure = { withContext(Dispatchers.IO) { directoryBytes(pictures) } },
            free = {
                val cleared = withContext(Dispatchers.IO) { pictures.deleteRecursively() }
                if (cleared) Outcome.Success(Unit) else Outcome.Failure("files left behind")
            },
        ),
        StorageKind.OCR_TEXT to
            StorageOwner({ OcrTextCacheFiles.sizeBytes(context) }, { OcrTextCacheFiles.clear(context) }),
        StorageKind.COVERS to StorageOwner(covers::sizeBytes, covers::clear),
        StorageKind.VOICE_BLENDS to StorageOwner(
            measure = { SpeechEngines.blendCacheBytes(context) },
            free = { SpeechEngines.clearBlendCache(context) },
        ),
        StorageKind.SHARED_PDFS to StorageOwner(sharedCopies::sizeBytes),
    ) + studioStorageOwners(context, searchIndexes)
}

/** Settings → Storage rows for studio's data: look-up dictionaries, reading tutor models, studio audio, search. */
private fun studioStorageOwners(context: Context, searchIndexes: SearchIndexes): Map<StorageKind, StorageOwner> {
    val dictionaries = Dictionaries.get(context)
    val listen = ListenModels.get(context)
    return mapOf(
        StorageKind.DICTIONARIES to StorageOwner(dictionaries::storedBytes) {
            Lang.entries.map { dictionaries.delete(it) }.firstOrNull { it is Outcome.Failure } ?: Outcome.Success(Unit)
        },
        // The three deletes run side by side: each may wait for a cancelled download to stop.
        StorageKind.READING_TUTOR to StorageOwner({ listen.storedBytes() }) {
            if (listen.deleteAllModels()) Outcome.Success(Unit) else Outcome.Failure("not every model deleted")
        },
        StorageKind.STUDIO_AUDIO to StorageOwner(
            measure = { withContext(Dispatchers.IO) { StudioMode.audioBytes(context) } },
            free = {
                val cleared = withContext(Dispatchers.IO) { StudioMode.clearAll(context) }
                if (cleared) Outcome.Success(Unit) else Outcome.Failure("studio mode is running")
            },
        ),
        StorageKind.SEARCH_INDEXES to StorageOwner(
            measure = { withContext(Dispatchers.IO) { searchIndexes.totalBytes() } },
            free = {
                withContext(Dispatchers.IO) { searchIndexes.clearAll() }
                Outcome.Success(Unit)
            },
        ),
    )
}
