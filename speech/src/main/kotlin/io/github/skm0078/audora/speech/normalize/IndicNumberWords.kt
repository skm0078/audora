// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/normalize/IndicNumberWords.kt
// Purpose: Hindi and Bengali number words: 0–99 by table, then hundreds, thousand, lakh and crore (P3-02).

package io.github.skm0078.audora.speech.normalize

import java.text.Normalizer

/**
 * Hindi and Bengali name every number below a hundred with its own word (Hindi 47 is "सैंतालीस", not "forty
 * seven"), so 0–99 is a table. Larger numbers use the Indian system: crore (10⁷), lakh (10⁵), thousand, hundred.
 * A count of crores above 99 is read the same way ("एक हज़ार करोड़"). Every word is stored in NFC, the form
 * `:pdf` gives the text in (ADR-028).
 */
internal class IndicNumberWords private constructor(
    belowHundred: List<String>,
    private val hundredOf: (String) -> String,
    scale: Scale,
    terms: Terms,
    conjunctions: Conjunctions,
) {
    /** The words for 10³, 10⁵ and 10⁷. */
    class Scale(val thousand: String, val lakh: String, val crore: String)

    /**
     * Words the normalizer puts around numbers. [currency] is "रुपये" even after one: the oblique case ("एक रुपये
     * का सिक्का") is the common one in running text.
     */
    class Terms(val point: String, val percent: String, val to: String, val currency: String, val paise: String)

    /** Words for "/" and "&" between words (RI-6). */
    class Conjunctions(val or: String, val and: String)

    private val belowHundred = belowHundred.map(::nfc)
    private val thousand = nfc(scale.thousand)
    private val lakh = nfc(scale.lakh)
    private val crore = nfc(scale.crore)
    val point = nfc(terms.point)
    val percent = nfc(terms.percent)
    val to = nfc(terms.to)
    val currency = nfc(terms.currency)
    val paise = nfc(terms.paise)
    val or = nfc(conjunctions.or)
    val and = nfc(conjunctions.and)

    init {
        require(this.belowHundred.size == HUNDRED.toInt()) { "one word for each of 0..99" }
    }

    fun cardinal(n: Long): String {
        require(n >= 0) { "negative numbers are read by the caller" }
        if (n < HUNDRED) return belowHundred[n.toInt()]
        val parts = ArrayList<String>()
        val crores = n / CRORE
        if (crores > 0) parts.add("${cardinal(crores)} $crore")
        addCount(parts, (n / LAKH) % HUNDRED, lakh)
        addCount(parts, (n / THOUSAND) % HUNDRED, thousand)
        val hundreds = (n / HUNDRED) % DIGIT_BASE
        if (hundreds > 0) parts.add(hundredOf(belowHundred[hundreds.toInt()]))
        val rest = n % HUNDRED
        if (rest > 0) parts.add(belowHundred[rest.toInt()])
        return parts.joinToString(" ")
    }

    /** 1947 as people say a year: nineteen hundred forty-seven. Only for 1100–1999; other years read as counts. */
    fun year(n: Int): String {
        val hundreds = n / HUNDRED.toInt()
        if (hundreds !in YEAR_HUNDREDS) return cardinal(n.toLong())
        val rest = n % HUNDRED.toInt()
        val head = hundredOf(belowHundred[hundreds])
        return if (rest == 0) head else "$head ${belowHundred[rest]}"
    }

    /** "042" → "zero four two": codes, phone numbers and the digits after a decimal point. */
    fun digits(ascii: String): String = ascii.map { belowHundred[it - '0'] }.joinToString(" ")

    private fun addCount(parts: MutableList<String>, count: Long, unit: String) {
        if (count > 0) parts.add("${belowHundred[count.toInt()]} $unit")
    }

    companion object {
        private const val DIGIT_BASE = 10L
        private const val HUNDRED = 100L
        private const val THOUSAND = 1_000L
        private const val LAKH = 100_000L
        private const val CRORE = 10_000_000L
        private val YEAR_HUNDREDS = 11..19

        private fun nfc(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFC)

        val HINDI = IndicNumberWords(
            belowHundred = (
                "शून्य एक दो तीन चार पाँच छह सात आठ नौ " +
                    "दस ग्यारह बारह तेरह चौदह पंद्रह सोलह सत्रह अठारह उन्नीस " +
                    "बीस इक्कीस बाईस तेईस चौबीस पच्चीस छब्बीस सत्ताईस अट्ठाईस उनतीस " +
                    "तीस इकतीस बत्तीस तैंतीस चौंतीस पैंतीस छत्तीस सैंतीस अड़तीस उनतालीस " +
                    "चालीस इकतालीस बयालीस तैंतालीस चवालीस पैंतालीस छियालीस सैंतालीस अड़तालीस उनचास " +
                    "पचास इक्यावन बावन तिरपन चौवन पचपन छप्पन सत्तावन अट्ठावन उनसठ " +
                    "साठ इकसठ बासठ तिरसठ चौंसठ पैंसठ छियासठ सड़सठ अड़सठ उनहत्तर " +
                    "सत्तर इकहत्तर बहत्तर तिहत्तर चौहत्तर पचहत्तर छिहत्तर सतहत्तर अठहत्तर उन्यासी " +
                    "अस्सी इक्यासी बयासी तिरासी चौरासी पचासी छियासी सत्तासी अट्ठासी नवासी " +
                    "नब्बे इक्यानबे बानबे तिरानबे चौरानबे पचानबे छियानबे सत्तानबे अट्ठानबे निन्यानबे"
                ).split(' '),
            hundredOf = { "$it सौ" },
            scale = Scale(thousand = "हज़ार", lakh = "लाख", crore = "करोड़"),
            terms = Terms(
                point = "दशमलव",
                percent = "प्रतिशत",
                to = "से",
                currency = "रुपये",
                paise = "पैसे",
            ),
            conjunctions = Conjunctions(or = "या", and = "और"),
        )

        val BENGALI = IndicNumberWords(
            belowHundred = (
                "শূন্য এক দুই তিন চার পাঁচ ছয় সাত আট নয় " +
                    "দশ এগারো বারো তেরো চোদ্দ পনেরো ষোলো সতেরো আঠারো উনিশ " +
                    "বিশ একুশ বাইশ তেইশ চব্বিশ পঁচিশ ছাব্বিশ সাতাশ আটাশ ঊনত্রিশ " +
                    "ত্রিশ একত্রিশ বত্রিশ তেত্রিশ চৌত্রিশ পঁয়ত্রিশ ছত্রিশ সাঁইত্রিশ আটত্রিশ ঊনচল্লিশ " +
                    "চল্লিশ একচল্লিশ বিয়াল্লিশ তেতাল্লিশ চুয়াল্লিশ " +
                    "পঁয়তাল্লিশ ছেচল্লিশ সাতচল্লিশ আটচল্লিশ ঊনপঞ্চাশ " +
                    "পঞ্চাশ একান্ন বাহান্ন তিপ্পান্ন চুয়ান্ন পঞ্চান্ন ছাপ্পান্ন সাতান্ন আটান্ন ঊনষাট " +
                    "ষাট একষট্টি বাষট্টি তেষট্টি চৌষট্টি পঁয়ষট্টি ছেষট্টি সাতষট্টি আটষট্টি ঊনসত্তর " +
                    "সত্তর একাত্তর বাহাত্তর তিয়াত্তর চুয়াত্তর পঁচাত্তর ছিয়াত্তর সাতাত্তর আটাত্তর ঊনআশি " +
                    "আশি একাশি বিরাশি তিরাশি চুরাশি পঁচাশি ছিয়াশি সাতাশি অষ্টাশি ঊননব্বই " +
                    "নব্বই একানব্বই বিরানব্বই তিরানব্বই চুরানব্বই পঁচানব্বই ছিয়ানব্বই সাতানব্বই আটানব্বই নিরানব্বই"
                ).split(' '),
            // Bengali writes the hundred joined to its count: একশো, দুইশো, উনিশশো.
            hundredOf = { "${it}শো" },
            scale = Scale(thousand = "হাজার", lakh = "লাখ", crore = "কোটি"),
            terms = Terms(
                point = "দশমিক",
                percent = "শতাংশ",
                to = "থেকে",
                currency = "টাকা",
                paise = "পয়সা",
            ),
            conjunctions = Conjunctions(or = "বা", and = "এবং"),
        )
    }
}
