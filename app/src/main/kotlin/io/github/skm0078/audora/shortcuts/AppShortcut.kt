// File: app/src/main/kotlin/io/github/skm0078/audora/shortcuts/AppShortcut.kt
// Purpose: The app icon's long-press shortcuts: Resume the book read last, Add a book, Read copied text.

package io.github.skm0078.audora.shortcuts

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import io.github.skm0078.audora.MainActivity
import io.github.skm0078.audora.R
import io.github.skm0078.audora.SharedText
import io.github.skm0078.audora.addPicked
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.playback.photo.PhotoBooks
import io.github.skm0078.audora.readClipboard
import io.github.skm0078.audora.ui.kids.LocalKidsMode
import io.github.skm0078.audora.ui.library.BOOK_MIME_TYPES
import io.github.skm0078.audora.widget.ResumeWidgetBook
import io.github.skm0078.audora.widget.ResumeWidgetProvider
import kotlinx.coroutines.flow.first

/** A long-press shortcut that opens the app to do one thing. Resume is the widget's own Continue intent. */
enum class AppShortcut(val action: String) {
    ADD_BOOK("io.github.skm0078.audora.action.ADD_BOOK"),
    READ_CLIPBOARD("io.github.skm0078.audora.action.READ_CLIPBOARD"),
    ;

    companion object {
        /** The shortcut [intent] came from, or null for any other launch. */
        fun of(intent: Intent?): AppShortcut? = entries.firstOrNull { it.action == intent?.action }
    }
}

/**
 * The shortcuts the launcher shows, in the app's language ([context] is the activity, which carries it): Resume
 * [book] when one was read, then Add a book and Read copied text. Dynamic rather than declared in XML, so their
 * labels follow the language picked in Settings, not only the phone's.
 */
fun publishAppShortcuts(context: Context, book: ResumeWidgetBook?) {
    val manager = context.getSystemService(ShortcutManager::class.java) ?: return
    val resume = book?.let {
        shortcut(context, RESUME_ID, R.string.shortcut_resume, R.string.shortcut_resume_long, R.drawable.ic_play)
            .setIntent(ResumeWidgetProvider.continueIntent(context, it.bookId))
            .build()
    }
    val others = AppShortcut.entries.map { asked ->
        val (label, long, icon) = when (asked) {
            AppShortcut.ADD_BOOK -> Triple(
                R.string.shortcut_add_book,
                R.string.shortcut_add_book_long,
                R.drawable.ic_add,
            )

            AppShortcut.READ_CLIPBOARD ->
                Triple(R.string.shortcut_clipboard, R.string.shortcut_clipboard_long, R.drawable.ic_format_quote)
        }
        shortcut(context, asked.name, label, long, icon)
            .setIntent(Intent(context, MainActivity::class.java).setAction(asked.action))
            .build()
    }
    // Refused (false) when rate limited; throws while the phone is still locked after a restart. The old ones stay.
    val updated = try {
        manager.setDynamicShortcuts(listOfNotNull(resume) + others)
    } catch (e: IllegalStateException) {
        AudoraLog.w(TAG, "Shortcuts not updated: ${e.javaClass.simpleName}")
        return
    }
    if (!updated) AudoraLog.w(TAG, "Shortcuts not updated: rate limited")
}

private fun shortcut(
    context: Context,
    id: String,
    @StringRes label: Int,
    @StringRes long: Int,
    @DrawableRes icon: Int,
): ShortcutInfo.Builder = ShortcutInfo.Builder(context, id)
    .setShortLabel(context.getString(label))
    .setLongLabel(context.getString(long))
    .setIcon(Icon.createWithResource(context, icon))

/**
 * Does what a long-press shortcut asked, once ([onTaken] clears it): Add a book opens the file picker, Read copied
 * text reads the clipboard. Not in kids mode, which adds no books.
 */
@Composable
internal fun AppShortcutEffect(
    shortcut: AppShortcut?,
    onTaken: () -> Unit,
    photoBooks: PhotoBooks,
    add: (String) -> Unit,
    onText: (SharedText) -> Unit,
) {
    val context = LocalContext.current
    val window = LocalWindowInfo.current
    val kids = LocalKidsMode.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { context.addPicked(it, photoBooks, add) }
    }
    LaunchedEffect(shortcut) {
        val asked = shortcut ?: return@LaunchedEffect
        onTaken()
        if (kids) return@LaunchedEffect
        AudoraLog.i(TAG, "Shortcut ${asked.name}")
        when (asked) {
            AppShortcut.ADD_BOOK -> picker.launch(BOOK_MIME_TYPES)

            AppShortcut.READ_CLIPBOARD -> {
                // Android gives the clipboard only to the app in focus, which a launch from the launcher is not yet.
                snapshotFlow { window.isWindowFocused }.first { it }
                context.readClipboard(onText)
            }
        }
    }
}

private const val TAG = "Audora.Shortcuts"
private const val RESUME_ID = "RESUME"
