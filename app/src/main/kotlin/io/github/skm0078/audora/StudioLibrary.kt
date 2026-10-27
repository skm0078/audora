// File: app/src/main/kotlin/io/github/skm0078/audora/StudioLibrary.kt
// Purpose: The library's studio actions (studio mode, copies, notes export, character guide, point and read,
//          pictures, clipboard, web pages and catalogues, watched folders, backup, quiz, listening stats) and the sheet
//          each opens.

package io.github.skm0078.audora

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.lifecycleScope
import io.github.skm0078.audora.core.config.PhotoConfig
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.StorageError
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.SpeechStyle
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.resume.ResumeAnchor
import io.github.skm0078.audora.core.resume.ResumeAnchorStore
import io.github.skm0078.audora.core.stats.ListeningLog
import io.github.skm0078.audora.di.LibraryEntryPoint
import io.github.skm0078.audora.pdf.PIPELINE_VERSION
import io.github.skm0078.audora.playback.backup.Backups
import io.github.skm0078.audora.playback.documents.WatchedFolders
import io.github.skm0078.audora.playback.epub.BookCopies
import io.github.skm0078.audora.playback.epub.CopyKind
import io.github.skm0078.audora.playback.export.MarkStores
import io.github.skm0078.audora.playback.jobs.LongJobKind
import io.github.skm0078.audora.playback.outputs.MadeFile
import io.github.skm0078.audora.playback.outputs.MadeFiles
import io.github.skm0078.audora.playback.photo.PhotoBooks
import io.github.skm0078.audora.playback.studio.SavedStudioFile
import io.github.skm0078.audora.playback.studio.StudioAudioFile
import io.github.skm0078.audora.playback.studio.StudioMode
import io.github.skm0078.audora.playback.studio.studioDuration
import io.github.skm0078.audora.ui.guide.CharacterGuideRoute
import io.github.skm0078.audora.ui.jobs.startLongJob
import io.github.skm0078.audora.ui.library.BackupRoute
import io.github.skm0078.audora.ui.library.BookAction
import io.github.skm0078.audora.ui.library.FolderSyncRoute
import io.github.skm0078.audora.ui.library.FolderSyncUi
import io.github.skm0078.audora.ui.library.LibraryAction
import io.github.skm0078.audora.ui.library.WatchedFoldersRoute
import io.github.skm0078.audora.ui.library.restartForRestore
import io.github.skm0078.audora.ui.quiz.QuizRoute
import io.github.skm0078.audora.ui.stats.ListeningStatsRoute
import io.github.skm0078.audora.ui.studio.StudioAudioActions
import io.github.skm0078.audora.ui.studio.StudioAudioRoute
import io.github.skm0078.audora.ui.web.WebBooksUi
import io.github.skm0078.audora.ui.web.WebSheet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What studio's library actions reach: the activity and its stores and services. */
@Suppress("LongParameterList") // A holder of the activity's parts, each used by one or two actions.
internal class StudioLibraryParts(
    val activity: ComponentActivity,
    val photoBooks: PhotoBooks,
    val watchedFolders: WatchedFolders,
    val backups: Backups,
    val anchors: ResumeAnchorStore,
    val notes: MarkStores,
    val listening: ListeningLog,
    val onText: (SharedText) -> Unit,
)

/** A studio sheet opened from the library's menu; one at a time. */
internal enum class StudioSheet { WATCHED_FOLDERS, BACKUP, QUIZ, LISTENING_STATS }

/** The dialogs open on the library, saved across rotation: a studio sheet, and the book a dialog is for. */
internal class StudioLibraryState(
    val sheet: MutableState<StudioSheet?>,
    val guideBookId: MutableState<String?>,
    val studioBookId: MutableState<String?>,
    val savingBookId: MutableState<String?>,
)

/** Studio's library actions and the sheets they open, for one library screen ([rememberStudioLibrary]). */
internal class StudioLibrary(
    private val parts: StudioLibraryParts,
    private val playWithPermission: (() -> Unit) -> Unit,
    private val onListen: (Book) -> Unit,
    private val takePhoto: ActivityResultLauncher<Uri>,
    private val pickPictures: ActivityResultLauncher<PickVisualMediaRequest>,
    private val state: StudioLibraryState,
) {
    private val sheet get() = state.sheet
    private val guideBookId get() = state.guideBookId
    private val context: Context get() = parts.activity.applicationContext

    // Studio mode and the copies report on the library's card and in a notification, so they ask for that
    // permission first; one already running of the same kind is said at once instead.
    fun onBookAction(book: Book, action: BookAction) = when (action) {
        // One place for everything about the book's studio audio (ADR-063): progress, what is ready, where, actions.
        BookAction.STUDIO -> openStudio(book.id)

        BookAction.ACCESSIBLE_COPY -> longJob(LongJobKind.COPY) {
            BookCopies.start(context, book.id, CopyKind.ACCESSIBLE)
        }

        BookAction.TALKING_BOOK -> longJob(LongJobKind.COPY) {
            BookCopies.start(context, book.id, CopyKind.TALKING_BOOK)
        }

        BookAction.EXPORT_NOTES -> parts.activity.exportNotes(parts.activity.lifecycleScope, parts.notes, book)

        BookAction.CHARACTER_GUIDE -> guideBookId.value = book.id.value

        BookAction.MARK_FINISHED, BookAction.MARK_NOT_STARTED -> markProgress(book, action)
    }

    /**
     * Finished puts the place at the start of the last page; Not started forgets it. The save time is kept, so the
     * book does not jump to the top as "Continue reading". A book the player still holds saves its own place again
     * once it plays on, which is the listener's latest choice.
     */
    private fun markProgress(book: Book, action: BookAction) {
        val anchors = parts.anchors
        val finished = action == BookAction.MARK_FINISHED
        parts.activity.lifecycleScope.launch {
            val pageCount = book.pageCount
            val result = when {
                !finished -> anchors.clear(book.id)

                pageCount == null -> Outcome.Failure(StorageError("page count not known yet"))

                else -> {
                    val savedAt = anchors.get(book.id)?.savedAtMillis ?: 0L
                    anchors.put(ResumeAnchor(book.id, pageCount - 1, 0, 0L, 0L, PIPELINE_VERSION, savedAt))
                }
            }
            val message = when {
                result is Outcome.Failure -> {
                    AudoraLog.w(TAG, "Progress not marked, book=${book.id.logLabel}: ${result.error.detail}")
                    R.string.library_mark_failed
                }

                finished -> R.string.library_marked_finished

                else -> R.string.library_marked_not_started
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun longJob(kind: LongJobKind, start: () -> Unit) =
        parts.activity.startLongJob(kind) { playWithPermission(start) }

    /** The studio dialog for [id], from the book's menu or the studio card's Details. */
    fun openStudio(id: BookId) {
        state.studioBookId.value = id.value
    }

    private val studioActions = object : StudioAudioActions {
        override fun start(book: Book) = longJob(LongJobKind.STUDIO) { StudioMode.start(context, book.id) }

        override fun listen(book: Book) {
            close()
            onListen(book)
        }

        override fun saveAsFile(book: Book) = saveStudioFile(book)

        override fun close() {
            state.studioBookId.value = null
        }
    }

    /**
     * Encoding hours of audio takes minutes: on the app's scope, so closing the dialog or turning the phone neither
     * loses the result nor lets a second save start, with a toast when it starts and when it ends saying where the
     * file is.
     */
    private fun saveStudioFile(book: Book) {
        // Only the app context and the book are captured: the save can outlive this screen.
        val appContext = context
        val started = startStudioSave(
            saving = state.savingBookId,
            scope = (parts.activity.application as AudoraApplication).appScope,
            bookId = book.id.value,
            // An untitled book gets the export's fallback folder name, as an audiobook export does.
            save = { withContext(Dispatchers.IO) { StudioAudioFile.save(appContext, book.id, book.title) } },
        ) { saved ->
            if (saved is Outcome.Success) recordStudioFile(appContext, book, saved.value)
            val message = when (saved) {
                is Outcome.Success -> appContext.getString(
                    R.string.studio_file_saved,
                    saved.value.name,
                    appContext.studioDuration(saved.value.durationMs),
                    saved.value.folder,
                )

                is Outcome.Failure -> saved.error
            }
            Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
        }
        if (started) Toast.makeText(appContext, R.string.studio_file_saving, Toast.LENGTH_LONG).show()
    }

    fun onLibraryAction(action: LibraryAction) = when (action) {
        LibraryAction.POINT_AND_READ -> parts.activity.pointAndRead(parts.photoBooks, takePhoto)
        LibraryAction.READ_PICTURES -> pickPictures.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
        LibraryAction.READ_CLIPBOARD -> parts.activity.readClipboard(parts.onText)
        LibraryAction.READ_WEB_PAGE -> WebBooksUi.sheet = WebSheet.ADDRESS
        LibraryAction.BOOK_CATALOGUES -> WebBooksUi.sheet = WebSheet.CATALOGUES
        LibraryAction.WATCHED_FOLDERS -> sheet.value = StudioSheet.WATCHED_FOLDERS
        LibraryAction.SYNC_FOLDER -> FolderSyncUi.open = true
        LibraryAction.BACKUP -> sheet.value = StudioSheet.BACKUP
        LibraryAction.QUIZ -> sheet.value = StudioSheet.QUIZ
        LibraryAction.LISTENING_STATS -> sheet.value = StudioSheet.LISTENING_STATS
        LibraryAction.TRY_SAMPLE -> parts.onText(sampleBook(parts.activity))
    }

    /** The sheet open now, if any; [books] (null while the list loads) holds the character guide's book. */
    @Composable
    fun Sheets(books: List<Book>?) {
        val close = { sheet.value = null }
        WatchedFoldersRoute(parts.watchedFolders, open = sheet.value == StudioSheet.WATCHED_FOLDERS, onClose = close)
        FolderSyncRoute()
        BackupRoute(parts.backups, sheet.value == StudioSheet.BACKUP, close) { parts.activity.restartForRestore() }
        QuizRoute(parts.notes.highlights, open = sheet.value == StudioSheet.QUIZ, onClose = close)
        val stats = sheet.value == StudioSheet.LISTENING_STATS
        ListeningStatsRoute(parts.listening, books, parts.anchors, open = stats, onClose = close)
        val guideBook = books?.firstOrNull { it.id.value == guideBookId.value }
        CharacterGuideRoute(guideBook, parts.anchors) { guideBookId.value = null }
        val studioBook = books?.firstOrNull { it.id.value == state.studioBookId.value }
        StudioAudioRoute(studioBook, saving = state.savingBookId.value == studioBook?.id?.value, studioActions)
    }
}

/** Saved, so a rotation, theme switch or process death keeps a sheet (or the guide, by its book's id) open. */
@Composable
internal fun rememberStudioLibrary(
    parts: StudioLibraryParts,
    playWithPermission: (() -> Unit) -> Unit,
    listen: (Book) -> Unit,
): StudioLibrary {
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        if (taken) parts.activity.makePhotoBook(parts.photoBooks) else parts.photoBooks.discardCapture()
    }
    // The photo picker needs no media permission; picked in order, they become the book's pages in that order.
    val pickPictures = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(PhotoConfig().maxImagesPerBook),
    ) { pictures ->
        if (pictures.isNotEmpty()) parts.activity.makePicturesBook(parts.photoBooks, pictures)
    }
    val state = StudioLibraryState(
        sheet = rememberSaveable { mutableStateOf<StudioSheet?>(null) },
        guideBookId = rememberSaveable { mutableStateOf<String?>(null) },
        studioBookId = rememberSaveable { mutableStateOf<String?>(null) },
        // Process-wide, not saved: the save runs on the app's scope, so a new screen still sees it running.
        savingBookId = studioSaving,
    )
    return remember(parts) { StudioLibrary(parts, playWithPermission, listen, takePhoto, pickPictures, state) }
}

/**
 * In the library's "Made from this book" (ADR-068); a file MediaStore gave no address for is not listed. Top level, so
 * a save outliving its screen holds the app's context only, never the closed activity.
 */
private suspend fun recordStudioFile(context: Context, book: Book, saved: SavedStudioFile) {
    val location = saved.location ?: return
    val file = MadeFile(saved.name, location, MadeFiles.AUDIO_MIME, durationMs = saved.durationMs)
    val style = SpeechStyle(pitch = saved.pitch)
    MadeFiles(LibraryEntryPoint.of(context).outputs()).studioAudio(book.id, file, style)
}

/** The book whose studio file is being saved, for every library screen of this process. */
private val studioSaving = mutableStateOf<String?>(null)

/**
 * Starts [save] on [scope] unless a save is already running ([saving] holds its book), then hands its result to
 * [finish]. [saving] is cleared when both are done, or if they fail. False when refused.
 */
internal fun <R> startStudioSave(
    saving: MutableState<String?>,
    scope: CoroutineScope,
    bookId: String,
    save: suspend () -> R,
    finish: suspend (R) -> Unit,
): Boolean {
    if (saving.value != null) return false
    saving.value = bookId
    scope.launch {
        try {
            finish(save())
        } finally {
            saving.value = null
        }
    }
    return true
}

private const val TAG = "Audora.Library"
