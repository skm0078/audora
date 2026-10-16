// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/SentenceConfig.kt
// Purpose: Word lists for Hindi and Bengali sentence splitting: abbreviations and initials a full stop doesn't end.

package io.github.skm0078.audora.core.config

/**
 * Hindi and Bengali end sentences with the danda (।), so a full stop after Devanagari or Bengali letters is usually
 * an abbreviation ("डॉ. शर्मा") or an initial ("ए. पी. जे. अब्दुल कलाम"). A sentence break after one of these words
 * and its full stop is removed (P3-02, ADR-028). Words are written without the final full stop; inner full stops
 * stay ("कि.मी").
 *
 * Initials are the Hindi and Bengali spellings of English letter names, the way Indian names are printed. Native
 * single syllables are not on the list: "है." ends a sentence in Hindi text that uses a full stop for the danda.
 *
 * @property abbreviations Hindi and Bengali; matched against the whole word before the full stop.
 * @property initials Hindi and Bengali; matched the same way.
 */
data class SentenceConfig(
    val abbreviations: Set<String> = HINDI_ABBREVIATIONS + BENGALI_ABBREVIATIONS,
    val initials: Set<String> = HINDI_INITIALS + BENGALI_INITIALS,
) {
    init {
        require((abbreviations + initials).none { it.isBlank() || it.endsWith('.') || it.any(Char::isWhitespace) }) {
            "abbreviations and initials are single words without the final full stop"
        }
    }

    private companion object {
        val HINDI_ABBREVIATIONS = setOf(
            "डॉ", "डा", "श्री", "श्रीमती", "सुश्री", "कु", "प्रो", "पं", "स्व", "मि", "सं", "पृ", "क्र", "रु", "मो",
            "कि.मी", "ई.पू", "उ.प्र", "म.प्र",
        )
        val BENGALI_ABBREVIATIONS = setOf(
            "ডা", "ড", "ডঃ", "শ্রী", "শ্রীমতী", "প্রফ", "প্রো", "মো", "মোঃ", "মি", "মিসেস", "সং", "পৃ", "খ্রি", "কি.মি",
            "নং",
        )
        val HINDI_INITIALS = setOf(
            "ए", "बी", "सी", "डी", "ई", "एफ", "जी", "एच", "आई", "जे", "के", "एल", "एम", "एन", "ओ", "पी", "क्यू", "आर",
            "एस", "टी", "यू", "वी", "डब्ल्यू", "एक्स", "वाई", "जेड", "ज़ेड",
        )
        val BENGALI_INITIALS = setOf(
            "এ", "বি", "সি", "ডি", "ই", "এফ", "জি", "এইচ", "আই", "জে", "কে", "এল", "এম", "এন", "ও", "পি", "কিউ", "আর",
            "এস", "টি", "ইউ", "ভি", "ডব্লিউ", "এক্স", "ওয়াই", "জেড",
        )
    }
}
