// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/CollectionWriter.kt
// Purpose: Writes to the listener's collections from the screens (ADR-069); one that fails is logged without the
//          collection's name and shown to the listener in a toast.

package io.github.skm0078.audora.ui.library

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.BookCollection
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.CollectionStore
import io.github.skm0078.audora.core.library.StorageError
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.di.LibraryEntryPoint
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Collection writes from the screens. [store] is looked up on the first write, so a screen that only shows
 * collections (a preview, a test) never needs the app's graph. Names are the listener's own and never logged.
 */
internal class CollectionWriter(
    private val store: () -> CollectionStore,
    private val scope: CoroutineScope,
    private val onFailed: () -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** A new collection named [name], with [add] in it when given (made from a book's details). */
    fun create(name: String, add: BookId? = null) = write {
        val collection = BookCollection(UUID.randomUUID().toString(), name, clock())
        val made = create(collection)
        if (made is Outcome.Success && add != null) setMember(add, collection.id, member = true, clock()) else made
    }

    fun rename(id: String, name: String) = write { rename(id, name) }

    fun delete(id: String) = write { delete(id) }

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
        CollectionWriter({ LibraryEntryPoint.of(app).collections() }, scope, onFailed = {
            Toast.makeText(app, R.string.collections_failed, Toast.LENGTH_SHORT).show()
        })
    }
}
