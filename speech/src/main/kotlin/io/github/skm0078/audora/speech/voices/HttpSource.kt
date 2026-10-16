// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/voices/HttpSource.kt
// Purpose: The seam between the download logic (JVM-tested) and the real HTTP client.

package io.github.skm0078.audora.speech.voices

import java.io.Closeable
import java.io.IOException
import java.io.InputStream

/** Opens one GET of a voice zip. The only network Audora ever uses (AGENTS.md: model downloads only). */
fun interface HttpSource {
    /** Requests [url] from byte [offset] on (a `Range` request when [offset] > 0). Redirects are followed. */
    @Throws(IOException::class)
    fun open(url: String, offset: Long): HttpResponse
}

/**
 * @property code the HTTP status: 200 (whole file), 206 (from the requested offset), 416 (offset past the end), …
 * @property contentRange the `Content-Range` header of a 206, e.g. `bytes 1000-1999/2000`.
 */
class HttpResponse(val code: Int, val contentRange: String?, val body: InputStream) : Closeable {
    override fun close() = body.close()

    companion object {
        const val OK = 200
        const val PARTIAL_CONTENT = 206
        const val RANGE_NOT_SATISFIABLE = 416
        private val CONTENT_RANGE = Regex("""bytes (\d+)-\d+/(\d+|\*)""")

        /** The first byte a `Content-Range` header covers, or null if it cannot be read. */
        fun rangeStart(header: String?): Long? =
            header?.let { CONTENT_RANGE.find(it.trim()) }?.groupValues?.get(1)?.toLongOrNull()
    }
}
