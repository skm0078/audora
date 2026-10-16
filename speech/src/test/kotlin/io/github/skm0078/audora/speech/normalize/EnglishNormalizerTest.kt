// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/normalize/EnglishNormalizerTest.kt
// Purpose: Table-driven cases for the English normalizer (P2-04): every rule, their interplay, and its invariants.

package io.github.skm0078.audora.speech.normalize

import io.github.skm0078.audora.core.config.DateOrder
import io.github.skm0078.audora.core.config.NormalizerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnglishNormalizerTest {
    private data class Case(
        val group: String,
        val written: String,
        val spoken: String,
        val role: TextRole = TextRole.BODY,
    )

    private val normalizer = EnglishNormalizer()

    private val cases = listOf(
        // Plain numbers
        Case("number", "I have 3 cats.", "I have three cats."),
        Case("number", "There were 42 of them", "There were forty-two of them"),
        Case("number", "It cost 0 effort", "It cost zero effort"),
        Case("number", "about 999 pages", "about nine hundred ninety-nine pages"),
        Case(
            "number",
            "1,234,567 people",
            "one million two hundred thirty-four thousand five hundred sixty-seven people",
        ),
        Case("number", "1,50,000 people", "one lakh fifty thousand people"),
        Case("number", "1,00,00,000 votes", "one crore votes"),
        Case("number", "150000 people", "one hundred fifty thousand people"),
        Case("number", "a list 1,2,3", "a list one,two,three"),
        Case("number", "Pi is 3.14", "Pi is three point one four"),
        Case("number", "0.5 of it", "zero point five of it"),
        Case("number", "agent 007", "agent zero zero seven"),
        Case(
            "number",
            "call 9876543210123456",
            "call nine eight seven six five four three two one zero one two three four five six",
        ),
        Case("number", "COVID-19 cases", "COVID-nineteen cases"),
        Case("number", "vitamin B12 levels", "vitamin B12 levels"),
        Case("number", "and 10.", "and ten."),
        // Negative numbers
        Case("sign", "It fell to -5 today", "It fell to minus five today"),
        Case("sign", "-3.5 is less", "minus three point five is less"),
        Case("sign", "a change of (−2)", "a change of (minus two)"),
        Case("sign", "−4 °C at night", "minus four degrees Celsius at night"),
        // Years
        Case("year", "In 1947 India", "In nineteen forty-seven India"),
        Case("year", "by 2024 most", "by twenty twenty-four most"),
        Case("year", "in 2005 we", "in two thousand five we"),
        Case("year", "in 1900 the", "in nineteen hundred the"),
        Case("year", "in 1905 the", "in nineteen oh five the"),
        Case("year", "the year 1205", "the year twelve oh five"),
        Case("year", "the year 1066", "the year one thousand sixty-six"),
        Case("year", "1500 soldiers", "fifteen hundred soldiers"),
        Case("year", "3000 soldiers", "three thousand soldiers"),
        Case("year", "1,947 people", "one thousand nine hundred forty-seven people"),
        // Ordinals
        Case("ordinal", "the 1st time", "the first time"),
        Case("ordinal", "his 2nd book", "his second book"),
        Case("ordinal", "the 3rd day", "the third day"),
        Case("ordinal", "the 4th wall", "the fourth wall"),
        Case("ordinal", "the 21st century", "the twenty-first century"),
        Case("ordinal", "the 100th run", "the one hundredth run"),
        Case("ordinal", "the 12th man", "the twelfth man"),
        // Decades
        Case("decade", "the 1990s were", "the nineteen nineties were"),
        Case("decade", "in the '80s", "in the eighties"),
        Case("decade", "in the ’60s", "in the sixties"),
        Case("decade", "the 1900s saw", "the nineteen hundreds saw"),
        Case("decade", "the 2000s saw", "the two thousands saw"),
        Case("decade", "in her 30s", "in her thirties"),
        Case("decade", "100s of people", "hundreds of people"),
        // Ranges
        Case("range", "ages 10-20 only", "ages ten to twenty only"),
        Case("range", "from 1990–1995", "from nineteen ninety to nineteen ninety-five"),
        Case("range", "in 2019-20 the", "in twenty nineteen to twenty the"),
        Case("range", "pages 10 – 15", "pages ten to fifteen"),
        Case("phone", "phone 98765-43210", "phone nine eight seven six five, four three two one zero"),
        Case("phone", "call 033-2241-0000 now", "call zero three three, two two four one, zero zero zero zero now"),
        Case("range", "2.5–3.5 hours", "two point five to three point five hours"),
        // Sections and dotted numbers
        Case("section", "see 3.2.1 below", "see three point two point one below"),
        Case(
            "section",
            "host 192.168.0.1 now",
            "host one hundred ninety-two point one hundred sixty-eight point zero point one now",
        ),
        // Currency
        Case("currency", "It costs ₹500.", "It costs five hundred rupees."),
        Case("currency", "₹1 only", "one rupee only"),
        Case("currency", "₹1,50,000 in all", "one lakh fifty thousand rupees in all"),
        Case("currency", "₹150000 in all", "one lakh fifty thousand rupees in all"),
        Case("currency", "₹ 2,000", "two thousand rupees"),
        Case("currency", "Rs. 500 each", "five hundred rupees each"),
        Case("currency", "Rs.500 each", "five hundred rupees each"),
        Case("currency", "Rs 75 each", "seventy-five rupees each"),
        Case("currency", "INR 100 fee", "one hundred rupees fee"),
        Case("currency", "₹10.50 bus fare", "ten rupees and fifty paise bus fare"),
        Case("currency", "₹5 crore budget", "five crore rupees budget"),
        Case("currency", "₹2.5 lakh loan", "two point five lakh rupees loan"),
        Case("currency", "Rs. 3 crores", "three crore rupees"),
        Case("currency", "₹40 cr deal", "forty crore rupees deal"),
        Case("currency", "$5 each", "five dollars each"),
        Case("currency", "$1 bill", "one dollar bill"),
        Case("currency", "$1.50 tip", "one dollar and fifty cents tip"),
        Case("currency", "$0.99 app", "ninety-nine cents app"),
        Case("currency", "$1,000 prize", "one thousand dollars prize"),
        Case("currency", "$2.5 million deal", "two point five million dollars deal"),
        Case("currency", "$3bn fund", "three billion dollars fund"),
        Case("currency", "$10k salary", "ten thousand dollars salary"),
        Case("currency", "US$20 fee", "twenty dollars fee"),
        Case("currency", "£3 ticket", "three pounds ticket"),
        Case("currency", "£0.01 coin", "one penny coin"),
        Case("currency", "€20 note", "twenty euros note"),
        Case("currency", "€1.05 each", "one euro and five cents each"),
        // Percent and units
        Case("quantity", "50% off", "fifty percent off"),
        Case("quantity", "a 2.5% rise", "a two point five percent rise"),
        Case("quantity", "10–20% more", "ten to twenty percent more"),
        Case("quantity", "37 °C fever", "thirty-seven degrees Celsius fever"),
        Case("quantity", "98.6°F is", "ninety-eight point six degrees Fahrenheit is"),
        Case("quantity", "a 90° angle", "a ninety degrees angle"),
        Case("quantity", "1 km away", "one kilometre away"),
        Case("quantity", "1500 km away", "one thousand five hundred kilometres away"),
        Case("quantity", "1.5 km away", "one point five kilometres away"),
        Case("quantity", "10-20 kg bags", "ten to twenty kilograms bags"),
        Case("quantity", "5kg rice", "five kilograms rice"),
        Case("quantity", "60 km/h limit", "sixty kilometres per hour limit"),
        Case("quantity", "4 GB RAM", "four gigabytes RAM"),
        Case("quantity", "2.4 GHz band", "two point four gigahertz band"),
        Case("quantity", "6 ft tall", "six feet tall"),
        Case("quantity", "200 ms delay", "two hundred milliseconds delay"),
        Case("quantity", "5 good men", "five good men"),
        // Clock times
        Case("time", "at 10:30 today", "at ten thirty today"),
        Case("time", "at 10:05 today", "at ten oh five today"),
        Case("time", "at 10:00 sharp", "at ten o'clock sharp"),
        Case("time", "at 18:00 hrs", "at eighteen hundred hrs"),
        Case("time", "at 9:15 a.m. daily", "at nine fifteen ay em daily"),
        Case("time", "by 5 PM", "by five pee em"),
        Case("time", "by 7pm tonight", "by seven pee em tonight"),
        Case("time", "It ends at 6 p.m.", "It ends at six pee em."),
        Case("time", "at 11:00 am", "at eleven ay em"),
        Case("time", "score 25:99 here", "score twenty-five:ninety-nine here"),
        // Dates
        Case("date", "on 15 August 1947 India", "on fifteenth August nineteen forty-seven India"),
        Case("date", "on 15th August, 1947", "on fifteenth August nineteen forty-seven"),
        Case("date", "the 26th of January", "the twenty-sixth of January"),
        Case("date", "on August 15, 1947 India", "on August fifteenth, nineteen forty-seven India"),
        Case("date", "on Aug. 15 they", "on August fifteenth they"),
        Case("date", "by Sept 3rd", "by September third"),
        Case("date", "in March 2020 the", "in March twenty twenty the"),
        Case("date", "in Jan 2021", "in January twenty twenty-one"),
        Case("date", "on 15/08/1947 India", "on fifteenth August nineteen forty-seven India"),
        Case("date", "on 05/08/2024 we", "on fifth August twenty twenty-four we"),
        Case("date", "on 12/25/2023 we", "on twenty-fifth December twenty twenty-three we"),
        Case("date", "dated 01-04-2021 the", "dated first April twenty twenty-one the"),
        Case("date", "dated 15.08.1947 the", "dated fifteenth August nineteen forty-seven the"),
        Case("date", "on 3/4/07 we", "on third April oh seven we"),
        Case("date", "on 1947-08-15 India", "on fifteenth August nineteen forty-seven India"),
        Case("date", "31/14/2020 is not", "thirty-one/fourteen/twenty twenty is not"),
        Case("date", "you may 5 times", "you may five times"),
        // Abbreviations
        Case("abbreviation", "Dr. Rao said", "Doctor Rao said"),
        Case("abbreviation", "Mr. and Mrs. Sen", "Mister and Missus Sen"),
        Case("abbreviation", "Ms. Roy and Prof. Das", "Miz Roy and Professor Das"),
        Case("abbreviation", "Smt. Devi and Sh. Kumar", "Shrimati Devi and Shri Kumar"),
        Case("abbreviation", "St. Xavier's College", "Saint Xavier's College"),
        Case("abbreviation", "on Park St. near", "on Park Street near"),
        Case("abbreviation", "fruit, e.g. apples", "fruit, for example apples"),
        Case("abbreviation", "one, i.e., this", "one, that is, this"),
        Case("abbreviation", "apples, pears etc.", "apples, pears et cetera."),
        Case("abbreviation", "apples etc. and more", "apples et cetera and more"),
        Case("abbreviation", "India vs. Australia", "India versus Australia"),
        Case("abbreviation", "Rao et al. found", "Rao and others found"),
        Case("abbreviation", "See Fig. 3 above", "See figure three above"),
        Case("abbreviation", "see Fig.3", "see figure three"),
        Case("abbreviation", "on p. 5 and pp. 10–12", "on page five and pages ten to twelve"),
        Case("abbreviation", "No. 7 is", "number seven is"),
        Case("abbreviation", "say no. Then", "say no. Then"),
        Case("abbreviation", "Ch. IV starts", "chapter four starts"),
        Case("abbreviation", "Tata Sons Pvt. Ltd. said", "Tata Sons Private Limited said"),
        Case("abbreviation", "the Govt. of India", "the Government of India"),
        Case("abbreviation", "Hon'ble Court", "Honourable Court"),
        // Links
        Case("link", "Visit https://example.com/a?b=1 now", "Visit link now"),
        Case("link", "See www.india.gov.in.", "See link."),
        Case("link", "See india.gov.in for more", "See link for more"),
        Case("link", "Mail info@example.org today", "Mail link today"),
        Case("link", "(https://x.org/2024)", "(link)"),
        Case("link", "Write to a.b@c.co.in, or call", "Write to link, or call"),
        // Roman numerals
        Case("roman", "Chapter IV begins", "Chapter four begins"),
        Case("roman", "PART XII", "PART twelve"),
        Case("roman", "in World War II the", "in World War two the"),
        Case("roman", "Henry VIII had", "Henry the eighth had"),
        Case("roman", "Elizabeth II and", "Elizabeth the second and"),
        Case("roman", "under Henry I.", "under Henry the first."),
        Case("roman", "when John I met", "when John I met"),
        Case("roman", "Class X results", "Class ten results"),
        Case("roman", "Type II diabetes", "Type two diabetes"),
        Case("roman", "Part C applies", "Part C applies"),
        Case("roman", "items (i) and (iv)", "items (one) and (four)"),
        Case("roman", "options (a) and (c)", "options (a) and (c)"),
        Case("roman", "Chapter IIII is wrong", "Chapter IIII is wrong"),
        Case("roman", "IV. Results", "four. Results", TextRole.HEADING),
        Case("roman", "XII", "twelve", TextRole.HEADING),
        Case("roman", "IV. Results", "IV. Results"),
        Case("roman", "C. Methods", "C. Methods", TextRole.HEADING),
        Case("roman", "I Am Malala", "I Am Malala", TextRole.HEADING),
        // Symbols
        Case("symbol", "Tom & Jerry", "Tom and Jerry"),
        Case("symbol", "room #5 is", "room number five is"),
        Case("symbol", "a 3 × 4 grid", "a three by four grid"),
        Case("symbol", "a 3x4 grid", "a three by four grid"),
        Case("symbol", "2 + 2 = 4", "two plus two equals four"),
        Case("symbol", "~10 km away", "about ten kilometres away"),
        Case("symbol", "5 ± 0.5 mm", "five plus or minus zero point five millimetres"),
        Case("symbol", "C++ code", "C++ code"),
        // Text that must pass through untouched
        Case("untouched", "", ""),
        Case("untouched", "No digits here.", "No digits here."),
        Case("untouched", "  padded  ", "padded"),
        Case("untouched", "It was a dark night.", "It was a dark night."),
        // Interplay of several rules in one sentence
        Case(
            "mixed",
            "On 15 Aug 2023 at 10:30 a.m., Dr. Sen paid ₹1,200 (approx. 15%) via www.pay.in.",
            "On fifteenth August twenty twenty-three at ten thirty ay em, Doctor Sen paid one thousand two " +
                "hundred rupees (approximately fifteen percent) via link.",
        ),
        Case(
            "mixed",
            "Between 1990–95, GDP grew 6.5% to $300 bn, see Fig. 2.",
            "Between nineteen ninety to ninety-five, GDP grew six point five percent to three hundred billion " +
                "dollars, see figure two.",
        ),
    )

    @Test
    fun `reads every case as written in the table`() {
        val failures = cases.mapNotNull { case ->
            val actual = normalizer.normalize(case.written, case.role)
            if (actual == case.spoken) {
                null
            } else {
                "[${case.group}] \"${case.written}\"\n   expected: \"${case.spoken}\"\n   actual:   \"$actual\""
            }
        }
        assertTrue(
            "${failures.size} of ${cases.size} cases failed:\n${failures.joinToString("\n")}",
            failures.isEmpty(),
        )
    }

    @Test
    fun `the table holds at least 100 cases (P2-04 done-when)`() {
        assertTrue("only ${cases.size} cases", cases.size >= 100)
    }

    @Test
    fun `normalizing twice is the same as once`() {
        for (case in cases) {
            val once = normalizer.normalize(case.written, case.role)
            assertEquals(case.written, once, normalizer.normalize(once, case.role))
        }
    }

    @Test
    fun `month-first config reads ambiguous numeric dates as US dates`() {
        val us = EnglishNormalizer(NormalizerConfig(numericDateOrder = DateOrder.MONTH_FIRST))
        assertEquals("on May eighth, twenty twenty-four", us.normalize("on 05/08/2024", TextRole.BODY))
        assertEquals("on August fifteenth, nineteen forty-seven", us.normalize("on 15/08/1947", TextRole.BODY))
    }

    @Test
    fun `rupees can use the international system`() {
        val international = EnglishNormalizer(NormalizerConfig(rupeesInIndianSystem = false))
        assertEquals("one hundred fifty thousand rupees", international.normalize("₹150000", TextRole.BODY))
        assertEquals("one lakh fifty thousand rupees", international.normalize("₹1,50,000", TextRole.BODY))
    }

    @Test
    fun `link word and long-number cut-off come from the config`() {
        val custom = EnglishNormalizer(NormalizerConfig(linkWord = "web address", maxCardinalDigits = 4))
        assertEquals("see web address", custom.normalize("see https://a.org", TextRole.BODY))
        assertEquals("one two three four five", custom.normalize("12345", TextRole.BODY))
    }

    @Test
    fun `never throws and never leaves an ASCII digit in long mixed text`() {
        val text = cases.joinToString(" ") { it.written }.repeat(3)
        val spoken = normalizer.normalize(text, TextRole.BODY)
        // "B12" is left for the engine on purpose: a digit glued to letters is a name, not a number.
        val leftovers = Regex("""\d+""").findAll(spoken).map { it.value }.toSet()
        assertEquals(setOf("12"), leftovers)
        assertFalse(spoken.contains("  "))
    }
}
