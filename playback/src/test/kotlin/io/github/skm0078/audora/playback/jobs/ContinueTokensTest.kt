// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/jobs/ContinueTokensTest.kt
// Purpose: A long job's Continue runs only with the token Audora's own notification carried, and only once.

package io.github.skm0078.audora.playback.jobs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinueTokensTest {
    private val kept = HashMap<String, String>()
    private var next = 0
    private val tokens = ContinueTokens(
        object : TokenStore {
            override fun get(key: String): String? = kept[key]

            override fun put(key: String, value: String) {
                kept[key] = value
            }

            override fun remove(key: String) {
                kept.remove(key)
            }
        },
        newToken = { "token-${next++}" },
    )

    @Test
    fun theNotificationsTokenContinuesOnce() {
        val token = tokens.issue("export:book")

        assertTrue(tokens.redeem("export:book", token))
        assertFalse("a token is spent once used", tokens.redeem("export:book", token))
    }

    @Test
    fun anotherAppWithOnlyTheBookIdContinuesNothing() {
        tokens.issue("export:book")

        assertFalse(tokens.redeem("export:book", null))
        assertFalse(tokens.redeem("export:book", "guess"))
    }

    @Test
    fun aTokenBelongsToItsOwnJobAndANewerOneReplacesIt() {
        val old = tokens.issue("export:book")
        val copy = tokens.issue("copy:book")
        val newer = tokens.issue("export:book")

        assertFalse(tokens.redeem("export:book", copy))
        assertFalse(tokens.redeem("export:book", old))
        assertTrue(tokens.redeem("export:book", newer))
    }
}
