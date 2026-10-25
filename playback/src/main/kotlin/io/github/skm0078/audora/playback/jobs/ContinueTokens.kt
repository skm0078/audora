// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/jobs/ContinueTokens.kt
// Purpose: One-time tokens proving a long job's "Continue" came from Audora's own notification, not another app.

package io.github.skm0078.audora.playback.jobs

import android.content.Context
import java.util.UUID

/** Where the tokens are kept: app-private SharedPreferences, a map in tests. */
interface TokenStore {
    fun get(key: String): String?

    fun put(key: String, value: String)

    fun remove(key: String)
}

/**
 * MainActivity is exported, so any app can open it with any extras. A "Continue" for a stopped export or copy starts
 * work that writes the book's content to shared storage (Audiobooks, Documents), so it must come from the
 * notification Audora posted: that notification carries a random token Audora kept, and Continue runs only when the
 * token it brings back matches, once.
 */
class ContinueTokens(private val store: TokenStore, private val newToken: () -> String = ::randomToken) {
    /** A fresh token for [job] (such as "export:<book>"), replacing any earlier one. */
    fun issue(job: String): String = newToken().also { store.put(job, it) }

    /** True when [given] is [job]'s current token; the token is then spent. */
    fun redeem(job: String, given: String?): Boolean {
        val kept = store.get(job)
        val matches = given != null && given == kept
        if (matches) store.remove(job)
        return matches
    }

    companion object {
        const val EXTRA_TOKEN = "io.github.skm0078.audora.jobs.CONTINUE_TOKEN"
        private const val PREFS = "continue_tokens"

        fun forContext(context: Context): ContinueTokens {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return ContinueTokens(
                object : TokenStore {
                    override fun get(key: String): String? = prefs.getString(key, null)

                    override fun put(key: String, value: String) = prefs.edit().putString(key, value).apply()

                    override fun remove(key: String) = prefs.edit().remove(key).apply()
                },
            )
        }

        private fun randomToken(): String = UUID.randomUUID().toString() // SecureRandom-backed
    }
}
