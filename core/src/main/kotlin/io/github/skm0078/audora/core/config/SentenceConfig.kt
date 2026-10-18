// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/SentenceConfig.kt
// Purpose: Word lists for sentence splitting: Hindi, Bengali and English abbreviations a full stop doesn't end.

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
 * English text ends sentences with the full stop too, so only a title or a Latin abbreviation that is usually
 * followed by a capital ("Dr. Smith", "e.g. Apples") is listed, besides capital initials ("A. P. J.", "U.S."), which
 * need no list. A single capital is an initial only after a name, a comma or a full stop ("Abbott B.", "Smith, J.",
 * "Dr. A."): "says I.", "matrix Q." and "Appendix A." end sentences. BreakIterator already keeps a full stop before a
 * lower-case word ("et al. found"). Words that often end a sentence stay off the list: "etc.", "Jr.", "Ltd.", and
 * "No." ("No. It was her."), which is kept only as a reference before a number or bracket ("No. 5", "Fig. (a)").
 *
 * @property abbreviations Hindi and Bengali; matched against the whole word before the full stop.
 * @property initials Hindi and Bengali; matched the same way.
 * @property englishAbbreviations English titles and Latin abbreviations, matched the same way, case and all.
 * @property referenceAbbreviations English words that point at a numbered thing: no break after one before a digit
 *   or an opening bracket.
 * @property letterLabels capitalised words that a letter after them labels ("Appendix A."), so the letter is not an
 *   initial; shared with the speech normalizer ([NumberingWords]).
 */
data class SentenceConfig(
    val abbreviations: Set<String> = HINDI_ABBREVIATIONS + BENGALI_ABBREVIATIONS,
    val initials: Set<String> = HINDI_INITIALS + BENGALI_INITIALS,
    val englishAbbreviations: Set<String> = ENGLISH_ABBREVIATIONS,
    val referenceAbbreviations: Set<String> = REFERENCE_ABBREVIATIONS,
    val letterLabels: Set<String> = NumberingWords.ALL,
) {
    init {
        require(
            (abbreviations + initials + englishAbbreviations + referenceAbbreviations + letterLabels).none {
                it.isBlank() || it.endsWith('.') ||
                    it.any(Char::isWhitespace)
            },
        ) {
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
            "নং", "খ্রি.পূ",
        )
        val HINDI_INITIALS = setOf(
            "ए", "बी", "सी", "डी", "ई", "एफ", "जी", "एच", "आई", "जे", "के", "एल", "एम", "एन", "ओ", "पी", "क्यू", "आर",
            "एस", "टी", "यू", "वी", "डब्ल्यू", "एक्स", "वाई", "जेड", "ज़ेड",
        )
        val ENGLISH_ABBREVIATIONS = setOf(
            "Mr", "Mrs", "Ms", "Messrs", "Dr", "Prof", "Shri", "Smt", "Sh", "Kum", "Hon", "Gen", "Lt", "Col", "Maj",
            "Capt", "Brig", "Cdr", "Rev", "St", "Mt", "e.g", "i.e", "viz", "cf", "vs",
            // Indian government and company usage: "Govt. Order", "Pvt. Ltd.", "Dy. Director", "Asstt. Engineer".
            "Sri", "Govt", "Pvt", "Dy", "Addl", "Jt", "Asstt", "Spl",
        )
        val REFERENCE_ABBREVIATIONS = setOf(
            "No", "Nos", "no", "Art", "Sec", "Ch", "Fig", "Figs", "Eq", "Eqs", "Vol", "Vols", "pp", "p", "Para", "Ref",
            "Rs", "Re",
        )
        val BENGALI_INITIALS = setOf(
            "এ", "বি", "সি", "ডি", "ই", "এফ", "জি", "এইচ", "আই", "জে", "কে", "এল", "এম", "এন", "ও", "পি", "কিউ", "আর",
            "এস", "টি", "ইউ", "ভি", "ডব্লিউ", "এক্স", "ওয়াই", "জেড",
        )
    }
}
