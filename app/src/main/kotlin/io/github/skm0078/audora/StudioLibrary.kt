// File: app/src/main/kotlin/io/github/skm0078/audora/StudioLibrary.kt
// Purpose: The library's studio actions (studio mode, copies, notes export, character guide, point and read, watched
//          folders, backup, quiz) and the sheet each opens.

package io.github.skm0078.audora

import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.lifecycleScope
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.resume.ResumeAnchorStore
import io.github.skm0078.audora.playback.backup.Backups
import io.github.skm0078.audora.playback.documents.WatchedFolders
import io.github.skm0078.audora.playback.epub.BookCopies
import io.github.skm0078.audora.playback.epub.CopyKind
import io.github.skm0078.audora.playback.export.MarkStores
import io.github.skm0078.audora.playback.photo.PhotoBooks
import io.github.skm0078.audora.playback.studio.StudioMode
import io.github.skm0078.audora.ui.guide.CharacterGuideRoute
import io.github.skm0078.audora.ui.library.BackupRoute
import io.github.skm0078.audora.ui.library.BookAction
import io.github.skm0078.audora.ui.library.LibraryAction
import io.github.skm0078.audora.ui.library.WatchedFoldersRoute
import io.github.skm0078.audora.ui.library.restartForRestore
import io.github.skm0078.audora.ui.quiz.QuizRoute

/** What studio's library actions reach: the activity and its stores and services. */
internal class StudioLibraryParts(
    val activity: ComponentActivity,
    val photoBooks: PhotoBooks,
    val watchedFolders: WatchedFolders,
    val backups: Backups,
    val anchors: ResumeAnchorStore,
    val notes: MarkStores,
)

/** A studio sheet opened from the library's menu; one at a time. */
internal enum class StudioSheet { WATCHED_FOLDERS, BACKUP, QUIZ }

/** Studio's library actions and the sheets they open, for one library screen ([rememberStudioLibrary]). */
internal class StudioLibrary(
    private val parts: StudioLibraryParts,
    private val playWithPermission: (() -> Unit) -> Unit,
    private val takePhoto: ActivityResultLauncher<Uri>,
    private val sheet: MutableState<StudioSheet?>,
    private val guideBookId: MutableState<String?>,
) {
    private val context: Context get() = parts.activity.applicationContext

    // Studio mode and the copies report in a notification, so they ask for that permission first.
    fun onBookAction(book: Book, action: BookAction) = when (action) {
        BookAction.STUDIO -> playWithPermission { StudioMode.start(context, book.id) }
        BookAction.ACCESSIBLE_COPY -> playWithPermission { BookCopies.start(context, book.id, CopyKind.ACCESSIBLE) }
        BookAction.TALKING_BOOK -> playWithPermission { BookCopies.start(context, book.id, CopyKind.TALKING_BOOK) }
        BookAction.EXPORT_NOTES -> parts.activity.exportNotes(parts.activity.lifecycleScope, parts.notes, book)
        BookAction.CHARACTER_GUIDE -> guideBookId.value = book.id.value
    }

    fun onLibraryAction(action: LibraryAction) = when (action) {
        LibraryAction.POINT_AND_READ -> parts.activity.pointAndRead(parts.photoBooks, takePhoto)
        LibraryAction.WATCHED_FOLDERS -> sheet.value = StudioSheet.WATCHED_FOLDERS
        LibraryAction.BACKUP -> sheet.value = StudioSheet.BACKUP
        LibraryAction.QUIZ -> sheet.value = StudioSheet.QUIZ
    }

    /** The sheet open now, if any; [books] (null while the list loads) holds the character guide's book. */
    @Composable
    fun Sheets(books: List<Book>?) {
        val close = { sheet.value = null }
        WatchedFoldersRoute(parts.watchedFolders, open = sheet.value == StudioSheet.WATCHED_FOLDERS, onClose = close)
        BackupRoute(parts.backups, sheet.value == StudioSheet.BACKUP, close) { parts.activity.restartForRestore() }
        QuizRoute(parts.notes.highlights, open = sheet.value == StudioSheet.QUIZ, onClose = close)
        val guideBook = books?.firstOrNull { it.id.value == guideBookId.value }
        CharacterGuideRoute(guideBook, parts.anchors) { guideBookId.value = null }
    }
}

/** Saved, so a rotation, theme switch or process death keeps a sheet (or the guide, by its book's id) open. */
@Composable
internal fun rememberStudioLibrary(parts: StudioLibraryParts, playWithPermission: (() -> Unit) -> Unit): StudioLibrary {
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        if (taken) parts.activity.makePhotoBook(parts.photoBooks) else parts.photoBooks.discardCapture()
    }
    val sheet = rememberSaveable { mutableStateOf<StudioSheet?>(null) }
    val guideBookId = rememberSaveable { mutableStateOf<String?>(null) }
    return remember(parts) { StudioLibrary(parts, playWithPermission, takePhoto, sheet, guideBookId) }
}
