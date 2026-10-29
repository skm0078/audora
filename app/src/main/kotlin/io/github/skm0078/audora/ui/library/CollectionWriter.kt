// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/CollectionWriter.kt
// Purpose: Writes to the listener's collections from the screens (ADR-069); one that fails is logged without the
//          collection's name and shown to the listener in a toast.

package io.github.skm0078.audora.ui.library

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.BookCollection
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.CollectionStore
import io.github.skm0078.audora.core.library.ShelfLook
import io.github.skm0078.audora.core.library.ShelfLooks
import io.github.skm0078.audora.core.library.StorageError
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.di.LibraryEntryPoint
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Collection writes from the screens. [store] is looked up on the first write, so a screen that only shows
 * collections (a preview, a test) never needs the app's graph. Names are the listener's own and never logged.
 */
internal class CollectionWriter(
    private val store: () -> CollectionStore,
    private val scope: CoroutineScope,
    private val onFailed: () -> Unit,
    private val looks: () -> ShelfLooks? = { null },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** A new collection named [name] looking like [look], with [add] in it when given (made from a book's details). */
    fun create(name: String, add: BookId? = null, look: ShelfLook = ShelfLook.DEFAULT) = write {
        val collection = BookCollection(UUID.randomUUID().toString(), name, clock())
        val made = create(collection)
        if (made is Outcome.Success) saveLook(collection.id, look)
        if (made is Outcome.Success && add != null) setMember(add, collection.id, member = true, clock()) else made
    }

    fun rename(id: String, name: String) = write { rename(id, name) }

    /** A new name (when it changed) and look for collection [id], from the Collections sheet's edit page. */
    fun edit(id: String, name: String, nameBefore: String, look: ShelfLook) = write {
        saveLook(id, look)
        if (name != nameBefore) rename(id, name) else Outcome.Success(Unit)
    }

    /** The collection and its look go; its books stay. */
    fun delete(id: String) = write {
        delete(id).also { if (it is Outcome.Success) saveLook(id, ShelfLook.DEFAULT) }
    }

    /** Off the main thread: the looks file is small, but still a file. A failure is the store's to log. */
    private suspend fun saveLook(id: String, look: ShelfLook) {
        val saved = looks()?.let { withContext(Dispatchers.IO) { it.set(id, look) } } ?: true
        if (!saved) onFailed()
    }

    fun setMember(book: BookId, id: String, member: Boolean) = write { setMember(book, id, member, clock()) }

    private fun write(block: suspend CollectionStore.() -> Outcome<Unit, StorageError>) {
        scope.launch {
            val done = store().block()
            if (done is Outcome.Failure) {
                AudoraLog.w(TAG, "Collection not saved: ${done.error.detail}")
                onFailed()
            }
        }
    }

    private companion object {
        const val TAG = "Audora.Collections"
    }
}

@Composable
internal fun rememberCollectionWriter(): CollectionWriter {
    val app = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    return remember(scope) {
        val entry = { LibraryEntryPoint.of(app) }
        CollectionWriter(
            { entry().collections() },
            scope,
            onFailed = { Toast.makeText(app, R.string.collections_failed, Toast.LENGTH_SHORT).show() },
            looks = { entry().shelfLooks() },
        )
    }
}

/** Every collection's colour and emoji, loaded off the main thread on first use; empty until then. */
@Composable
internal fun rememberShelfLooks(): Map<String, ShelfLook> {
    val app = LocalContext.current.applicationContext
    val looks = remember(app) { LibraryEntryPoint.of(app).shelfLooks() }
    LaunchedEffect(looks) { withContext(Dispatchers.IO) { looks.load() } }
    val shown by looks.looks.collectAsState()
    return shown
}
