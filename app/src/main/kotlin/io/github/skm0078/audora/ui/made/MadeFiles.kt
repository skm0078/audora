// File: app/src/main/kotlin/io/github/skm0078/audora/ui/made/MadeFiles.kt
// Purpose: The sets of files made from books, as the Made screen and a book's details show them (ADR-068): which are
//          still on the phone, and Open, Share and Delete for them.

package io.github.skm0078.audora.ui.made

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.BookOutput
import io.github.skm0078.audora.core.library.OutputSet
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.playback.outputs.OutputFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** One set as listed: [present] are its files still on the phone; the rest were deleted or moved outside the app. */
data class MadeSet(val set: OutputSet, val present: List<BookOutput>) {
    val missing: Int get() = set.files.size - present.size
    val allMissing: Boolean get() = present.isEmpty()
}

/**
 * [outputs] grouped into sets, newest first, each checked against shared storage once per change (one small query
 * per file, off the main thread); null while the first check runs.
 */
@Composable
internal fun rememberMadeSets(outputs: Flow<List<BookOutput>>): List<MadeSet>? {
    val context = LocalContext.current.applicationContext
    val files = remember { OutputFiles(context) }
    val all by outputs.collectAsStateWithLifecycle(null)
    val sets by produceState<List<MadeSet>?>(null, all) {
        val current = all ?: return@produceState
        value = withContext(Dispatchers.IO) {
            OutputSet.of(current).map { set -> MadeSet(set, set.files.filter { files.state(it.location).present }) }
        }
    }
    return sets
}

/** Share and Open hand the files to another app, with read access to just these files. */
internal object MadeIntents {
    private const val TAG = "Library.Made"
    private const val ANY_TYPE = "*/*"

    fun share(context: Context, files: List<BookOutput>) {
        if (files.isEmpty()) return
        val uris = files.map { Uri.parse(it.location) }
        val type = files.map { it.mimeType }.distinct().singleOrNull() ?: ANY_TYPE
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        send.setType(type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).clipData = clipOf(uris)
        launch(context, Intent.createChooser(send, context.getString(R.string.made_share)))
    }

    fun open(context: Context, file: BookOutput) {
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(Uri.parse(file.location), file.mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        launch(context, Intent.createChooser(view, context.getString(R.string.made_open)))
    }

    /** A chooser's grant covers only its data, so every shared file goes in its clip too. */
    private fun clipOf(uris: List<Uri>): ClipData = ClipData.newRawUri(null, uris[0]).apply {
        uris.drop(1).forEach { addItem(ClipData.Item(it)) }
    }

    private fun launch(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            AudoraLog.w(TAG, "No app for a made file: ${e.javaClass.simpleName}")
            Toast.makeText(context, R.string.made_no_app, Toast.LENGTH_LONG).show()
        }
    }
}
