// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/voices/UrlConnectionHttpSource.kt
// Purpose: HttpSource over the platform's HttpURLConnection (no HTTP library: AGENTS.md, no new dependency).

package io.github.skm0078.audora.speech.voices

import io.github.skm0078.audora.core.config.VoiceDownloadConfig
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI

/**
 * GitHub release downloads redirect (https → https) to a CDN that honours `Range`; HttpURLConnection follows
 * same-protocol redirects itself. `Accept-Encoding: identity` keeps byte offsets those of the file, not of a gzip
 * stream that Android's client would otherwise ask for and inflate silently.
 */
class UrlConnectionHttpSource(private val config: VoiceDownloadConfig, private val userAgent: String) : HttpSource {
    override fun open(url: String, offset: Long): HttpResponse {
        val connection = URI(url).toURL().openConnection() as? HttpURLConnection
            ?: throw IOException("not an http(s) url: $url")
        connection.connectTimeout = config.connectTimeoutMs
        connection.readTimeout = config.readTimeoutMs
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", userAgent)
        connection.setRequestProperty("Accept-Encoding", "identity")
        if (offset > 0) connection.setRequestProperty("Range", "bytes=$offset-")
        return try {
            val code = connection.responseCode
            val body = if (code in SUCCESS) connection.inputStream else connection.errorStream ?: EMPTY
            HttpResponse(code, connection.getHeaderField("Content-Range"), Disconnecting(body, connection))
        } catch (e: IOException) {
            connection.disconnect()
            throw e
        }
    }

    /** Closing the body also releases the connection. */
    private class Disconnecting(body: InputStream, private val connection: HttpURLConnection) :
        FilterInputStream(body) {
        override fun close() {
            try {
                super.close()
            } finally {
                connection.disconnect()
            }
        }
    }

    private companion object {
        val SUCCESS = 200..299
        val EMPTY get() = ByteArrayInputStream(ByteArray(0))
    }
}
