// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/document/IntentSignals.kt
// Purpose: What one page's sentences say about the book's intent: dialogue, verse lines, datelines, worship (ADR-056).

package io.github.skm0078.audora.pdf.document

import io.github.skm0078.audora.core.config.DeliveryConfig
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.TextBox
import io.github.skm0078.audora.core.model.UnitKind
import kotlin.math.abs

/**
 * Counts over one page (or several, [plus]), from its reading units alone, so a PDF, a scan read by OCR, an EPUB
 * and shared text are all told the same way. The word lists cover English, Hindi and Bengali; each is a cue, never
 * proof, and [IntentClassifier] only acts on rates over many words.
 */
data class IntentSignals(
    val words: Int = 0,
    val bodySentences: Int = 0,
    val bodyWords: Int = 0,
    val dialogueSentences: Int = 0,
    val speechVerbs: Int = 0,
    val storyOpeners: Int = 0,
    val structure: Int = 0,
    val teachCues: Int = 0,
    val citations: Int = 0,
    val furniture: Int = 0,
    val officialTerms: Int = 0,
    val newsCues: Int = 0,
    val sacredWords: Int = 0,
    val verseMarks: Int = 0,
    val lines: Int = 0,
    val lineWords: Int = 0,
    val shortLines: Int = 0,
    val digits: Int = 0,
    val letters: Int = 0,
) {
    operator fun plus(o: IntentSignals) = IntentSignals(
        words + o.words, bodySentences + o.bodySentences, bodyWords + o.bodyWords,
        dialogueSentences + o.dialogueSentences, speechVerbs + o.speechVerbs, storyOpeners + o.storyOpeners,
        structure + o.structure, teachCues + o.teachCues, citations + o.citations, furniture + o.furniture,
        officialTerms + o.officialTerms,
        newsCues + o.newsCues, sacredWords + o.sacredWords, verseMarks + o.verseMarks, lines + o.lines,
        lineWords + o.lineWords, shortLines + o.shortLines, digits + o.digits, letters + o.letters,
    )

    companion object {
        /** One page's units, in reading order; units below [readableFrom] are OCR junk and say nothing. */
        fun of(
            units: List<ReadingUnit>,
            readableFrom: Float,
            config: DeliveryConfig = DeliveryConfig(),
        ): IntentSignals {
            val kept = units.filter { (it.speakScore ?: 1f) >= readableFrom }
            val text = kept.joinToString(" ") { it.text }
            val body = kept.filter { it.kind == UnitKind.BODY }
            val lined = body.filter { !it.bounds.isNullOrEmpty() }
            val lines = PageLines.of(lined, config.shortLine)
            return IntentSignals(
                words = wordsIn(text),
                bodySentences = body.size,
                bodyWords = body.sumOf { wordsIn(it.text) },
                dialogueSentences = body.count { unit -> unit.text.any { it in QUOTES } },
                speechVerbs = SPEECH_VERBS.findAll(text).count(),
                storyOpeners = STORY_OPENERS.findAll(text).count(),
                structure = kept.count { it.kind == UnitKind.HEADING || it.kind == UnitKind.LIST_ITEM } +
                    body.count { ENUMERATOR.containsMatchIn(it.text) },
                teachCues = TEACH_CUES.findAll(text).count(),
                citations = CITATION.findAll(text).count(),
                furniture = kept.count { it.kind in FURNITURE },
                officialTerms = OFFICIAL.findAll(text).count(),
                newsCues = NEWS_CUES.findAll(text).count(),
                sacredWords = SACRED.findAll(text).count(),
                verseMarks = VERSE.findAll(text).count(),
                lines = lines.count,
                lineWords = lined.sumOf { wordsIn(it.text) },
                shortLines = lines.short,
                digits = text.count(Char::isDigit),
                letters = text.count(Char::isLetter),
            )
        }

        private fun wordsIn(text: String): Int = WORD.findAll(text).count()

        private val FURNITURE = setOf(UnitKind.CAPTION, UnitKind.EQUATION, UnitKind.FOOTNOTE)

        private val WORD = Regex("[\\p{L}\\p{M}\\p{Nd}]+")
        private const val QUOTES = "“”\"«»"

        private val ENUMERATOR = Regex("^\\s*(?:\\(?(?:\\p{Nd}{1,2}|\\p{L})\\)|\\p{Nd}{1,2}[.।]\\s|[•●▪■◆➢►]\\s?)")

        private val SPEECH_VERBS = lexicon(
            "said|says|asked|replied|answered|whispered|shouted|cried|exclaimed|muttered|murmured|called out|" +
                "yelled|sobbed|laughed",
            "कहा|पूछा|बोला|बोली|बोले|चिल्लाया|चिल्लाई|जवाब दिया|उत्तर दिया|कहने लगा|कहने लगी|" +
                "বলল|বললেন|বলে উঠল|বলেছিল|জিজ্ঞেস করল|জিজ্ঞাসা করল|উত্তর দিল|চেঁচিয়ে উঠল",
        )

        private val STORY_OPENERS = lexicon(
            "once upon a time|long,? long ago|there once (?:was|lived)|one fine day|happily ever after",
            "एक बार की बात|बहुत समय पहले|बहुत पुरानी बात|एक समय की बात|किसी गाँव में|किसी गांव में|" +
                "অনেক দিন আগে|অনেককাল আগে|একদা|এক দেশে|এক যে ছিল",
        )

        private val TEACH_CUES = lexicon(
            "Example|Exercises?|Activity|Definition|Theorem|Lemma|Proof|Solution|Summary|Key terms|Let us|" +
                "Note that|Learning objectives",
            "उदाहरण|अभ्यास|क्रियाकलाप|गतिविधि|परिभाषा|प्रश्नावली|सारांश|" +
                "উদাহরণ|অনুশীলনী|কার্যক্রম|সংজ্ঞা|সমাধান|সারসংক্ষেপ",
            ignoreCase = false,
        )

        /** "[4]", "[12, 13]", "(Kot and Schaffer, 1986)", "(Smith et al. 2004)". */
        private val CITATION = Regex(
            "\\[\\p{Nd}{1,3}(?:\\s?[,–-]\\s?\\p{Nd}{1,3})*]|" +
                "\\(\\p{Lu}\\p{L}+(?: et al\\.?| and \\p{Lu}\\p{L}+)?,? \\p{Nd}{4}[a-z]?\\)",
        )

        /** The vocabulary of acts, notices and government reports, as `:pdf`'s RI-10 kinds count it. */
        private val OFFICIAL = lexicon(
            "Section|Sub-section|Clause|Regulations?|Notification|Ministry|Government|Gazette|Commission|hereby|" +
                "aforesaid|Department|Annexure",
            "अधिसूचना|धारा|विनियम|अधिनियम|सरकार|मंत्रालय|आयोग|राजपत्र|शासन|विभाग|आदेश|जिला|जनपद|कार्यालय|" +
                "अधिकारी|निदेशालय|प्रतिवेदन|बजट|दिनांक|" +
                "সরকার|মন্ত্রণালয়|প্রজ্ঞাপন|ধারা|বিধি|বিভাগ|আদেশ|অধিদপ্তর|কার্যালয়|জেলা|কর্মকর্তা|বাজেট|পরিপত্র",
            ignoreCase = false,
        )

        private const val EN_DAYS = "Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday"

        /** Agencies, datelines and the phrases reporters use; "(PTI)" has no word boundary before its bracket. */
        private val NEWS_CUES = lexicon(
            "Reuters|Press Trust of India|" +
                "(?:said|told|announced|reported) (?:on |late |early )?(?:$EN_DAYS)|" +
                "according to (?:officials|police|sources|the|a)|(?:officials|sources|police) said|in a statement|" +
                "spokesperson|correspondent",
            "संवाददाता|ने बताया कि|सूत्रों के अनुसार|सूत्रों ने|रिपोर्ट के मुताबिक|प्रवक्ता ने|पुलिस ने बताया|" +
                "প্রতিবেদক|সংবাদদাতা|জানিয়েছেন|সূত্রে জানা|সূত্র জানায়|মুখপাত্র|বলে জানা গেছে",
            ignoreCase = false,
            prefix = "\\((?:PTI|IANS|ANI|UNI|AP|AFP|Reuters)\\)|",
        )

        /**
         * Words of worship. Not "श्री" or "धर्म": a Hindi notice has "श्री" before every name, a charter has "धर्म".
         * "मंत्र" and "মন্ত্র" (a mantra) are kept apart from "मंत्री" and "মন্ত্রণালয়" (a minister, a ministry).
         */
        private val SACRED = lexicon(
            "God|Lord|LORD|Jesus|Christ|Allah|thee|thou|thy|thine|hath|unto|shalt|verily|Amen|psalm|hymn|holy|" +
                "Holy|blessed",
            "भगवान|ईश्वर|प्रभु(?!त्व)|परमेश्वर|परमात्मा|श्लोक|मं?न्?त्र(?!ाल|ी)|ॐ|यहोवा|यीशु|आमीन|प्रार्थना|भक्ति|" +
                "उवाच|स्तुति|ঈশ্বর|ভগবান|প্রভু|পরমেশ্বর|সদাপ্রভু|শ্লোক|মন্ত্র(?!ণ|ী)|ওঁ|আল্লাহ|প্রার্থনা|ভক্তি|" +
                "যীশু|আমেন|উবাচ|স্তোত্র",
            ignoreCase = false,
        )

        /**
         * English words between word boundaries, then Hindi and Bengali ones that start a word: `\\b` knows no
         * Indic letters, and an Indic word takes endings ("भगवानों"), so only its start is fixed.
         */
        private fun lexicon(english: String, indic: String, ignoreCase: Boolean = true, prefix: String = ""): Regex {
            val pattern = "$prefix\\b(?:$english)\\b|(?<![\\p{L}\\p{M}])(?:$indic)"
            return if (ignoreCase) Regex(pattern, RegexOption.IGNORE_CASE) else Regex(pattern)
        }

        /** "3:16" not followed by a clock's "am"/"pm"; a closing "॥" or "।।" after a verse; Hindi "॥ १२ ॥". */
        private val VERSE = Regex("\\b\\p{Nd}{1,3}:\\p{Nd}{1,3}\\b(?!\\s*(?:[ap]\\.?m\\b|बजे))|॥|।।")
    }
}

/** A page's printed lines of body text, from the units' line boxes: how many, and how many are short. */
internal class PageLines(val count: Int, val short: Int) {
    companion object {
        fun of(body: List<ReadingUnit>, shortLine: Float): PageLines {
            val boxes = body.flatMap { it.bounds.orEmpty() }.filter { it.width > 0f && it.height > 0f }
            if (boxes.isEmpty()) return PageLines(0, 0)
            // A line two sentences share comes as two boxes; boxes at the same height are one printed line. Two
            // columns side by side also merge into one wide line, so a paper's narrow columns never look like verse.
            val lines = mutableListOf<TextBox>()
            boxes.sortedByDescending { it.top }.forEach { box ->
                val i = lines.indexOfFirst { abs(centre(it) - centre(box)) < minOf(it.height, box.height) / 2 }
                if (i < 0) lines.add(box) else lines[i] = lines[i].union(box)
            }
            val widest = lines.maxOf { it.width }
            return PageLines(lines.size, lines.count { it.width < widest * shortLine })
        }

        private fun centre(box: TextBox): Float = (box.top + box.bottom) / 2
    }
}
