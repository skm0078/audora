// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/web/WebFetcher.kt
// Purpose: One https request the listener asked for (a page, a catalogue, a book), size-capped and cancellable.

package io.github.skm0078.audora.playback.web

import io.github.skm0078.audora.core.config.WebBooksConfig
import io.github.skm0078.audora.core.result.Outcome
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URISyntaxException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Why a web request gave nothing to read; each has its own message. */
sealed interface WebError {
    /** Not an https address (ListenLock never fetches plain http). */
    data object BadAddress : WebError

    /** No connection, the server did not answer, or the connection broke. */
    data class Unreachable(val detail: String) : WebError

    /** The server answered with an error (404 gone, 403 refused, 5xx broken), or sent us to plain http. */
    data class Refused(val code: Int) : WebError

    /** Larger than [WebBooksConfig.maxPageBytes] or [WebBooksConfig.maxBookBytes]. */
    data object TooBig : WebError

    /** A page with no article text in it (a home page, a log-in wall, a page built by scripts). */
    data object NoArticle : WebError

    /** Not an OPDS catalogue (an ordinary web page, or a feed in a format ListenLock does not read). */
    data object NotACatalogue : WebError

    /** The book's file could not be written on the phone. */
    data class NotSaved(val detail: String) : WebError
}

/** A fetched page: its bytes, the server's content type and the address it came from after redirects. */
class WebPage(val bytes: ByteArray, val contentType: String?, val address: String)

/** Where pages and books come from: [WebFetcher] on the phone, a fake in tests. */
interface WebSource {
    /** The whole body of [address], at most [WebBooksConfig.maxPageBytes]. */
    suspend fun page(address: String, accept: String): Outcome<WebPage, WebError>

    /**
     * Writes the body of [address] into [into], at most [WebBooksConfig.maxBookBytes]; [onProgress] gets the share
     * done (0..1) when the server says the size, or null when it does not. Returns the content type.
     */
    suspend fun download(
        address: String,
        accept: String,
        into: OutputStream,
        onProgress: (Float?) -> Unit,
    ): Outcome<String?, WebError>
}

/**
 * HttpURLConnection, no HTTP library (AGENTS.md). Redirects are followed only https → https: the platform never
 * follows a change of protocol, and such a redirect is reported as [WebError.Refused]. Requests carry the address,
 * the app's name and the accepted types, nothing else: no cookies, no account, nothing about the listener.
 */
class WebFetcher(private val config: WebBooksConfig = WebBooksConfig()) : WebSource {
    override suspend fun page(address: String, accept: String): Outcome<WebPage, WebError> {
        val buffer = ByteArrayOutputStream()
        return when (val result = request(address, accept, config.maxPageBytes, buffer) { }) {
            is Outcome.Success -> {
                val answer = result.value
                Outcome.Success(WebPage(buffer.toByteArray(), answer.type, answer.address))
            }

            is Outcome.Failure -> result
        }
    }

    override suspend fun download(
        address: String,
        accept: String,
        into: OutputStream,
        onProgress: (Float?) -> Unit,
    ): Outcome<String?, WebError> =
        when (val result = request(address, accept, config.maxBookBytes, into, onProgress)) {
            is Outcome.Success -> Outcome.Success(result.value.type)
            is Outcome.Failure -> result
        }

    private class Answer(val type: String?, val address: String)

    private suspend fun request(
        address: String,
        accept: String,
        maxBytes: Long,
        into: OutputStream,
        onProgress: (Float?) -> Unit,
    ): Outcome<Answer, WebError> = when (val opened = open(address)) {
        is Outcome.Failure -> opened

        is Outcome.Success -> {
            val connection = opened.value
            try {
                connection.connectTimeout = config.connectTimeoutMs
                connection.readTimeout = config.readTimeoutMs
                connection.instanceFollowRedirects = true
                connection.useCaches = false
                connection.setRequestProperty("User-Agent", USER_AGENT)
                connection.setRequestProperty("Accept", accept)
                receive(connection, maxBytes, into, onProgress)
            } catch (e: IOException) {
                Outcome.Failure(WebError.Unreachable(e.javaClass.simpleName))
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun open(address: String): Outcome<HttpURLConnection, WebError> {
        val url = try {
            URI(address).takeIf { it.scheme.equals(HTTPS, ignoreCase = true) && it.host != null }?.toURL()
        } catch (ignored: URISyntaxException) {
            null
        } catch (ignored: IllegalArgumentException) {
            null
        }
        val connection = try {
            url?.openConnection() as? HttpURLConnection
        } catch (e: IOException) {
            return Outcome.Failure(WebError.Unreachable(e.javaClass.simpleName))
        }
        return connection?.let { Outcome.Success(it) } ?: Outcome.Failure(WebError.BadAddress)
    }

    private suspend fun receive(
        connection: HttpURLConnection,
        maxBytes: Long,
        into: OutputStream,
        onProgress: (Float?) -> Unit,
    ): Outcome<Answer, WebError> {
        val code = connection.responseCode
        val length = connection.contentLengthLong.takeIf { it > 0 }
        return when {
            code !in SUCCESS -> Outcome.Failure(WebError.Refused(code))

            length != null && length > maxBytes -> Outcome.Failure(WebError.TooBig)

            connection.inputStream.use { body -> copy(body, into, maxBytes, length, onProgress) } == null ->
                Outcome.Failure(WebError.TooBig)

            else -> Outcome.Success(Answer(connection.contentType, connection.url.toString()))
        }
    }

    /** Bytes copied, or null once past [maxBytes]; stops at once when the coroutine is cancelled. */
    private suspend fun copy(
        from: InputStream,
        to: OutputStream,
        maxBytes: Long,
        length: Long?,
        onProgress: (Float?) -> Unit,
    ): Long? {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var copied = 0L
        var reported = -1
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = from.read(buffer)
            if (read < 0) return copied
            copied += read
            if (copied > maxBytes) return null
            to.write(buffer, 0, read)
            val percent = length?.let { (copied * PERCENT / it).toInt() } ?: 0
            if (percent != reported) {
                reported = percent
                onProgress(length?.let { (copied.toFloat() / it).coerceAtMost(1f) })
            }
        }
    }

    companion object {
        private const val HTTPS = "https"
        private val SUCCESS = 200..299
        private const val PERCENT = 100L

        /**
         * Says what is asking, in the form sites expect (many refuse a request with no browser-like name), and
         * nothing about the phone or the listener.
         */
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android) ListenLock"
    }
}
