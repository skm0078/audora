// File: core/src/test/kotlin/io/github/skm0078/audora/core/resume/SentenceFingerprintTest.kt
// Purpose: Fingerprints ignore what a better pipeline changes (spacing, hyphens, ligatures) and keep what it doesn't.

package io.github.skm0078.audora.core.resume

import io.github.skm0078.audora.core.config.ResumeConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SentenceFingerprintTest {
    private val fingerprint = SentenceFingerprint()

    @Test
    fun `cosmetic differences keep the fingerprint`() {
        val base = "Call me Ishmael. Some years ago, never mind how long precisely."
        val sameSentence = listOf(
            "Call  me\nIshmael.   Some years ago, never mind how long precisely.",
            "CALL ME ISHMAEL. SOME YEARS AGO, NEVER MIND HOW LONG PRECISELY.",
            "Call me Ish-\nmael. Some years ago — never mind how long precisely!",
            "Call me Ish­mael. Some years ago, never mind how long precisely.",
            "Call me Ishmael.​ Some years ago, never mind how long precisely.",
            "“Call me Ishmael.” Some years ago, never mind how long precisely.",
        )
        for (variant in sameSentence) {
            assertEquals(variant, fingerprint.head(base), fingerprint.head(variant))
            assertEquals(variant, fingerprint.tail(base), fingerprint.tail(variant))
        }
    }

    @Test
    fun `ligatures fold to their letters`() {
        assertEquals(fingerprint.head("The first office"), fingerprint.head("The ﬁrst oﬃce"))
    }

    @Test
    fun `different words change the fingerprint`() {
        assertNotEquals(fingerprint.head("Call me Ishmael."), fingerprint.head("Call me Ahab."))
        assertNotEquals(fingerprint.head("Page 12"), fingerprint.head("Page 13"))
    }

    @Test
    fun `Indic vowel signs count, so different words never collide`() {
        // कि (ka + i-matra) and क differ only by a combining mark.
        assertNotEquals(fingerprint.head("कि"), fingerprint.head("क"))
        // Bengali: কে (ka + e-kar) vs ক.
        assertNotEquals(fingerprint.head("কে"), fingerprint.head("ক"))
        assertEquals(fingerprint.head("सभी मनुष्यों को"), fingerprint.head("सभी  मनुष्यों\nको।"))
    }

    @Test
    fun `head ignores everything after its window and tail everything before`() {
        val short = SentenceFingerprint(ResumeConfig(fingerprintChars = 10))
        assertEquals(short.head("abcdefghij and then one ending"), short.head("abcdefghij but another ending"))
        assertNotEquals(short.tail("abcdefghij and then one ending"), short.tail("abcdefghij but another ending"))
        assertEquals(short.tail("RUNNING HEADER Chapter one ends here"), short.tail("Chapter one ends here"))
    }

    @Test
    fun `text with nothing to hash has no fingerprint`() {
        assertEquals(SentenceFingerprint.NONE, fingerprint.head(""))
        assertEquals(SentenceFingerprint.NONE, fingerprint.head(" — … * "))
        assertEquals(SentenceFingerprint.NONE, fingerprint.tail("!!!"))
    }

    @Test
    fun `the hash is pinned, because saved anchors outlive app versions`() {
        // FNV-1a 64 of "callmeishmael"; changing the algorithm makes every saved anchor approximate.
        assertEquals(PINNED_CALL_ME_ISHMAEL, fingerprint.head("Call me Ishmael."))
    }

    private companion object {
        const val PINNED_CALL_ME_ISHMAEL = 0x05da9ed00a953e42L
    }
}
