// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/normalize/IndicTextPackTest.kt
// Purpose: Table-driven cases for the Indic text pack (plan item 3): dates, ordinals, units, abbreviations, currency.

package io.github.skm0078.audora.speech.normalize

import io.github.skm0078.audora.core.model.Lang
import java.text.Normalizer
import org.junit.Assert.assertEquals
import org.junit.Test

class IndicTextPackTest {
    private val hindi = IndicNormalizer.forLang(Lang.HI)!!
    private val bengali = IndicNormalizer.forLang(Lang.BN)!!

    private val hindiCases = listOf(
        "स्वतंत्रता 15/08/1947 को मिली।" to "स्वतंत्रता पंद्रह अगस्त उन्नीस सौ सैंतालीस को मिली।",
        "दिनांक १-१-२०२४" to "दिनांक एक जनवरी दो हज़ार चौबीस",
        "तारीख 26.01.50 है" to "तारीख छब्बीस जनवरी पचास है",
        "गलत 32/13/2020 तिथि" to "गलत बत्तीस/तेरह/दो हज़ार बीस तिथि",
        "वह 1ला आया, 2री बार, 3रे दिन" to "वह पहला आया, दूसरी बार, तीसरे दिन",
        "4था अध्याय, 6ठी कक्षा, 21वीं सदी, 5वाँ भाग, 10वें वर्ष" to
            "चौथा अध्याय, छठी कक्षा, इक्कीसवीं सदी, पाँचवाँ भाग, दसवें वर्ष",
        "दूरी 5 km और वज़न 10kg" to "दूरी पाँच किलोमीटर और वज़न दस किलोग्राम",
        "तापमान 37°C रहा" to "तापमान सैंतीस डिग्री सेल्सियस रहा",
        "गति 60 km/h थी" to "गति साठ किलोमीटर प्रति घंटा थी",
        "डॉ. शर्मा और प्रो. वर्मा" to "डॉक्टर शर्मा और प्रोफ़ेसर वर्मा",
        "दूरी 12 कि.मी. है" to "दूरी बारह किलोमीटर है",
        "पृ. 45 देखें" to "पृष्ठ पैंतालीस देखें",
        "सन् 1526 ई. में, 326 ई.पू. में" to "सन् पंद्रह सौ छब्बीस ईसवी में, तीन सौ छब्बीस ईसा पूर्व में",
        "रु. 500 और Rs. 20" to "पाँच सौ रुपये और बीस रुपये",
        "आदि. शब्द" to "आदि. शब्द",
    )

    private val bengaliCases = listOf(
        "১৫/০৮/১৯৪৭ তারিখে" to "পনেরোই আগস্ট উনিশশো সাতচল্লিশ তারিখে",
        "১/১/২০২৪ থেকে" to "পয়লা জানুয়ারি দুই হাজার চব্বিশ থেকে",
        "২৫-১২-২০২৩" to "পঁচিশে ডিসেম্বর দুই হাজার তেইশ",
        "১লা বৈশাখ, ২রা মে, ১৯শে জুন, ৫ই আগস্ট" to "পয়লা বৈশাখ, দোসরা মে, উনিশে জুন, পাঁচই আগস্ট",
        "১ম স্থান, ২য় দিন, ৪র্থ অধ্যায়, ৬ষ্ঠ শ্রেণি, ১১শ খণ্ড, ২১তম বার" to
            "প্রথম স্থান, দ্বিতীয় দিন, চতুর্থ অধ্যায়, ষষ্ঠ শ্রেণি, একাদশ খণ্ড, একুশতম বার",
        "দূরত্ব ৫ km" to "দূরত্ব পাঁচ কিলোমিটার",
        "ডঃ রায় পৃঃ ১২ দেখুন" to "ডক্টর রায় পৃষ্ঠা বারো দেখুন",
        "দূরত্ব ১০ কিমি" to "দূরত্ব দশ কিলোমিটার",
        "১৭৫৭ খ্রিঃ পলাশি" to "সতেরোশো সাতান্ন খ্রিস্টাব্দ পলাশি",
        "টা. ৫০০ দিন" to "পাঁচশো টাকা দিন",
        "আমার ৩টি বই" to "আমার তিনটি বই",
    )

    private fun check(normalizer: IndicNormalizer, cases: List<Pair<String, String>>) =
        cases.forEach { (written, spoken) ->
            assertEquals(written, nfc(spoken), nfc(normalizer.normalize(written, TextRole.BODY)))
        }

    @Test
    fun `Hindi dates, ordinals, units and abbreviations`() = check(hindi, hindiCases)

    @Test
    fun `Bengali dates, ordinals, units and abbreviations`() = check(bengali, bengaliCases)

    @Test
    fun `both spellings of the Bengali ya ending read alike`() {
        val composed = "২য় দিন"
        val decomposed = "২য় দিন"
        assertEquals(nfc("দ্বিতীয় দিন"), nfc(bengali.normalize(composed, TextRole.BODY)))
        assertEquals(nfc("দ্বিতীয় দিন"), nfc(bengali.normalize(decomposed, TextRole.BODY)))
    }

    private fun nfc(text: String) = Normalizer.normalize(text, Normalizer.Form.NFC)
}
