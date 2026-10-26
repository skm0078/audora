// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/web/WebBooks.kt
// Purpose: Books from the web, only on the listener's tap: a page read from its link (CF-12) and a book downloaded
//          from an OPDS catalogue (CF-15). One job at a time, outliving the screen that started it.

package io.github.skm0078.audora.playback.web

import io.github.skm0078.audora.core.config.WebBooksConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What a running job is getting: a page to read, or a book from a catalogue. */
enum class WebJobKind { PAGE, BOOK }

/** The job running now: [label] is the site (a page) or the book's title; [progress] 0..1, null when unknown. */
data class WebJob(val kind: WebJobKind, val label: String, val progress: Float? = null)

/** How a job ended, kept until the screen has acted on it ([WebBooks.consume]). */
sealed interface WebResult {
    /** A page's article, to be saved and opened like shared text. */
    data class Article(val article: WebArticle) : WebResult

    /** A book file saved in the app's own folder (a catalogue's book, or a link straight to a PDF or EPUB). */
    data class Book(val file: File) : WebResult

    data class Failed(val kind: WebJobKind, val error: WebError) : WebResult
}

/**
 * Nothing here runs unless the listener taps Read or Download: no background job, no prefetching, no account, and
 * nothing about the listener is sent ([WebFetcher]). [newBookFile] gives the place for a downloaded book (the
 * library's own copies folder, so removing the book deletes it); a job cancelled or failed deletes what it wrote.
 * Main-safe.
 */
class WebBooks(
    catalogues: OpdsCatalogues,
    newBookFile: (String) -> File,
    config: WebBooksConfig = WebBooksConfig(),
    fetcher: WebSource = WebFetcher(config),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val files = WebFiles(fetcher, newBookFile, config)
    private val mutableJob = MutableStateFlow<WebJob?>(null)
    private val mutableResult = MutableStateFlow<WebResult?>(null)
    private var running: Job? = null

    /** Catalogue pages, search and the listener's list of catalogues. */
    val browser = WebCatalogues(catalogues, fetcher, config)

    /** The job running now, or null. */
    val job: StateFlow<WebJob?> = mutableJob.asStateFlow()

    /** The last job's result, until [consume]. */
    val result: StateFlow<WebResult?> = mutableResult.asStateFlow()

    /** Fetches [address] and reads its article; a link straight to a book file saves the book instead. */
    fun readPage(address: String): Boolean = start(WebJob(WebJobKind.PAGE, WebAddress.hostOf(address))) {
        files.pageOrBook(address, ::report)
    }

    /** Downloads [download], the book [title] in a catalogue. */
    fun downloadBook(title: String, download: OpdsDownload): Boolean = start(WebJob(WebJobKind.BOOK, title)) {
        files.book(download, WebFiles.safeTitle(title), ::report)
    }

    /** Stops the running job; what it wrote is deleted, and no result is given. */
    fun cancel() {
        running?.cancel()
    }

    fun consume() {
        mutableResult.value = null
    }

    /** False (nothing started) while another job runs: one download at a time keeps the phone and the site calm. */
    private fun start(job: WebJob, work: suspend () -> WebResult): Boolean {
        if (running?.isActive == true) return false
        mutableResult.value = null
        mutableJob.value = job
        running = scope.launch {
            try {
                val result = work()
                log(job, result)
                mutableResult.value = result
            } catch (e: CancellationException) {
                AudoraLog.i(TAG, "Web ${job.kind} cancelled")
                throw e
            } finally {
                mutableJob.value = null
            }
        }
        return true
    }

    private fun report(progress: Float?) {
        mutableJob.value = mutableJob.value?.copy(progress = progress)
    }

    private fun log(job: WebJob, result: WebResult) = when (result) {
        is WebResult.Article -> AudoraLog.i(TAG, "Web page read, chars=${result.article.text.length}")
        is WebResult.Book -> AudoraLog.i(TAG, "Web ${job.kind} saved a book, bytes=${result.file.length()}")
        is WebResult.Failed -> AudoraLog.w(TAG, "Web ${job.kind} failed: ${result.error}")
    }

    private companion object {
        const val TAG = "Audora.WebBooks"
    }
}
