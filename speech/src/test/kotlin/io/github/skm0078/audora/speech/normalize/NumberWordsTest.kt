// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/normalize/NumberWordsTest.kt
// Purpose: Table-driven cases for spelling cardinals, ordinals, years, digit runs and plurals.

package io.github.skm0078.audora.speech.normalize

import org.junit.Assert.assertEquals
import org.junit.Test

class NumberWordsTest {
    @Test
    fun `cardinals in the international system`() {
        val cases = listOf(
            0L to "zero",
            7L to "seven",
            13L to "thirteen",
            40L to "forty",
            42L to "forty-two",
            100L to "one hundred",
            101L to "one hundred one",
            999L to "nine hundred ninety-nine",
            1_000L to "one thousand",
            150_000L to "one hundred fifty thousand",
            1_234_567L to "one million two hundred thirty-four thousand five hundred sixty-seven",
            2_000_000_000L to "two billion",
            999_999_999_999_999L to
                "nine hundred ninety-nine trillion nine hundred ninety-nine billion nine hundred ninety-nine million " +
                "nine hundred ninety-nine thousand nine hundred ninety-nine",
        )
        for ((value, expected) in cases) assertEquals("$value", expected, NumberWords.cardinal(value))
    }

    @Test
    fun `cardinals in the Indian system`() {
        val cases = listOf(
            99_999L to "ninety-nine thousand nine hundred ninety-nine",
            100_000L to "one lakh",
            150_000L to "one lakh fifty thousand",
            1_234_567L to "twelve lakh thirty-four thousand five hundred sixty-seven",
            10_000_000L to "one crore",
            250_000_000L to "twenty-five crore",
            10_000_000_000L to "one thousand crore",
        )
        for ((value, expected) in cases) {
            assertEquals("$value", expected, NumberWords.cardinal(value, NumberSystem.INDIAN))
        }
    }

    @Test
    fun `ordinals`() {
        val cases = listOf(
            1L to "first",
            2L to "second",
            3L to "third",
            4L to "fourth",
            5L to "fifth",
            8L to "eighth",
            9L to "ninth",
            12L to "twelfth",
            20L to "twentieth",
            21L to "twenty-first",
            100L to "one hundredth",
            103L to "one hundred third",
        )
        for ((value, expected) in cases) assertEquals("$value", expected, NumberWords.ordinal(value))
    }

    @Test
    fun `years`() {
        val cases = listOf(
            1066 to "ten sixty-six",
            1900 to "nineteen hundred",
            1905 to "nineteen oh five",
            1947 to "nineteen forty-seven",
            2000 to "two thousand",
            2007 to "two thousand seven",
            2010 to "twenty ten",
            2024 to "twenty twenty-four",
            999 to "nine hundred ninety-nine",
        )
        for ((value, expected) in cases) assertEquals("$value", expected, NumberWords.year(value))
    }

    @Test
    fun `digit runs and plurals`() {
        assertEquals("zero zero seven", NumberWords.digits("007"))
        assertEquals("nineties", NumberWords.plural("ninety"))
        assertEquals("nineteen hundreds", NumberWords.plural("nineteen hundred"))
        assertEquals("sixes", NumberWords.plural("six"))
        assertEquals("twenty tens", NumberWords.plural("twenty ten"))
    }
}
