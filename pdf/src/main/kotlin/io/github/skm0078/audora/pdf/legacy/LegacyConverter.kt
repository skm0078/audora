// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/legacy/LegacyConverter.kt
// Purpose: Converts text set in legacy Hindi and Bengali fonts to Unicode, keeping source offsets (P3-04, ADR-023).

package io.github.skm0078.audora.pdf.legacy

import io.github.skm0078.audora.core.config.LegacyEncoding
import io.github.skm0078.audora.pdf.cleanup.MappedText
import io.github.skm0078.audora.pdf.quality.IndicChars
import io.github.skm0078.audora.pdf.quality.IndicClass
import io.github.skm0078.audora.pdf.quality.IndicReorder

/** Converted text and how much of the legacy text the tables covered. */
internal class LegacyConversion(
    val text: MappedText,
    /** Letters of the legacy runs before conversion. */
    val letters: Int,
    /** Codes of the legacy runs no table entry covers; they are dropped. */
    val unknown: Int,
)

/**
 * Build Spec §5 step 3a (ADR-011 §3, ADR-023). Every stretch of [MappedText] whose characters come from legacy-font
 * runs of one script ([LegacyRuns]; a Kruti Dev word may hold a Chanakya glyph) is rewritten in Unicode; everything
 * else passes through. Per stretch:
 * 1. **Spaces inside a syllable go.** PDFium adds a space where a sign glyph is drawn apart from its consonant
 *    ("g qbZ" for "हुई"); a space before a vowel sign, virama or reph code, or after a pre-base code, is dropped.
 * 2. **Codes become letters** by longest match in each run's [LegacyTable]. A code no entry covers is dropped and
 *    counted as unknown: a page with too many goes to OCR (PageQualityScorer).
 * 3. **Visual order becomes logical:** each pre-base vowel sign moves after the consonant cluster that follows it,
 *    each reph before the cluster of the syllable it was drawn after. Unlike [IndicReorder], the table says which
 *    glyphs are pre-base signs and rephs, so nothing is guessed from the text.
 * 4. **Signs typed out of order are put in Unicode order** within a syllable: nukta, then a phala (्र), then vowel
 *    signs, then anusvara or candrabindu ("प ं ु" → "पुं"). Typists place these by eye, so files hold both orders.
 * 5. **Split letters are joined:** vowel halves (अ + ा, ে + া) and doubled viramas from half form + phala glyphs.
 * 6. **"0" after a Hindi letter is the abbreviation sign** ("पी0एस0" → "पी॰एस॰", [LegacyTable.abbreviationSign]).
 *
 * Characters keep the source offset of the position they land on (ADR-018), so offsets stay ascending.
 */
internal object LegacyConverter {
    private val DEPENDENT_CLASSES =
        setOf(IndicClass.VOWEL_SIGN, IndicClass.VIRAMA, IndicClass.NUKTA, IndicClass.MODIFIER)
    private const val ASCII_END = 0x80
    private const val SPACE = ' '
    private const val ZERO = '0'

    fun convert(input: MappedText, runs: LegacyRuns): LegacyConversion {
        if (runs.isEmpty) return LegacyConversion(input, 0, 0)
        val out = MappedText.Builder(input.text.length)
        var letters = 0
        var unknown = 0
        var i = 0
        while (i < input.text.length) {
            val encoding = runs.encodingAt(input.sources[i])
            if (encoding == null) {
                out.append(input.text[i], input.sources[i])
                i++
                continue
            }
            val script = LegacyTable.of(encoding)?.reph
            val stretch = Stretch.read(input, i, runs) { e -> e == encoding || LegacyTable.of(e)?.reph == script }
            letters += stretch.letters()
            unknown += if (script == null) {
                // No converter for this layout: nothing of it can be read.
                stretch.letters()
            } else {
                convertStretch(stretch.withoutSyllableSpaces(), out)
            }
            i += stretch.inputLength
        }
        return LegacyConversion(out.build(), letters, unknown)
    }

    /** Appends the stretch's Unicode text to [out]; returns how many codes were unknown. */
    private fun convertStretch(stretch: Stretch, out: MappedText.Builder): Int {
        val glyphs = Glyphs(stretch.text.length)
        var unknown = 0
        var pos = 0
        while (pos < stretch.text.length) {
            val table = stretch.tables[pos]
            val match = table?.match(stretch.text, pos, stretch.segmentEnd(pos))
            if (table != null && match != null) {
                glyphs.add(match.second, table, stretch.sources[pos])
                pos += match.first
                continue
            }
            val c = stretch.text[pos]
            if (c.isLetter() || c.code >= ASCII_END) unknown++ else glyphs.addPlain(c, stretch.sources[pos])
            pos++
        }
        glyphs.movePrebaseSigns()
        glyphs.moveRephs()
        glyphs.orderSyllableTails()
        val table = requireNotNull(stretch.tables.firstNotNullOfOrNull { it })
        table.abbreviationSign?.let(glyphs::markAbbreviations)
        glyphs.appendComposed(table.compositions, out)
        return unknown
    }

    /**
     * Characters of one stretch, with their source offsets and each one's table (null for a space layout inserted).
     * [inputLength] is how many input characters it covers, dropped spaces included.
     */
    private class Stretch(
        val text: String,
        val sources: IntArray,
        val tables: Array<LegacyTable?>,
        val inputLength: Int,
    ) {
        fun letters(): Int = text.count { it.isLetter() }

        /** End of the same-table segment at [pos]: a code never spans two fonts. */
        fun segmentEnd(pos: Int): Int {
            var end = pos + 1
            while (end < text.length && tables[end] === tables[pos]) end++
            return end
        }

        fun withoutSyllableSpaces(): Stretch {
            val keep = BooleanArray(text.length) { true }
            for (i in text.indices) {
                if (text[i] != SPACE) continue
                val before = (i - 1 downTo 0).firstOrNull { keep[it] && text[it] != SPACE }?.let(::glyphAt)
                val after = (i + 1 until text.length).firstOrNull { text[it] != SPACE }?.let(::glyphAt)
                if (before?.isPrebase() == true || after?.isDependent() == true) keep[i] = false
            }
            if (keep.all { it }) return this
            val kept = text.indices.filter { keep[it] }
            return Stretch(
                String(CharArray(kept.size) { text[kept[it]] }),
                IntArray(kept.size) { sources[kept[it]] },
                Array(kept.size) { tables[kept[it]] },
                inputLength,
            )
        }

        private fun glyphAt(i: Int): LegacyGlyph? = tables[i]?.glyphOf(text[i])

        private fun LegacyGlyph.isPrebase() = role == GlyphRole.PREBASE || role == GlyphRole.PREBASE_REPH

        /** A glyph that cannot start a syllable: a vowel sign, virama, nukta, modifier or reph. */
        private fun LegacyGlyph.isDependent(): Boolean =
            role == GlyphRole.REPH || text.firstOrNull()?.let { IndicChars.classOf(it) } in DEPENDENT_CLASSES

        companion object {
            /**
             * The stretch at [start]: characters whose run's encoding is [joins], bridging spaces layout inserted
             * between them (a word split into two text spans).
             */
            fun read(input: MappedText, start: Int, runs: LegacyRuns, joins: (LegacyEncoding) -> Boolean): Stretch {
                fun joinsAt(k: Int) = k < input.text.length && runs.encodingAt(input.sources[k])?.let(joins) == true
                var end = start
                var extending = true
                while (extending) {
                    while (joinsAt(end)) end++
                    var k = end
                    while (k < input.text.length && input.text[k] == SPACE &&
                        runs.encodingAt(input.sources[k]) == null
                    ) {
                        k++
                    }
                    extending = k > end && joinsAt(k)
                    if (extending) end = k
                }
                val sources = input.sources.copyOfRange(start, end)
                val tables = Array(end - start) { runs.encodingAt(sources[it])?.let(LegacyTable::of) }
                return Stretch(input.text.substring(start, end), sources, tables, end - start)
            }
        }
    }

    /** Converted characters in drawing order: positional source offsets and each character's role. */
    private class Glyphs(capacity: Int) {
        private var chars = CharArray(capacity)
        private var sources = IntArray(capacity)
        private var roles = arrayOfNulls<GlyphRole>(capacity)
        private var size = 0

        fun add(glyph: LegacyGlyph, table: LegacyTable, source: Int) {
            val rephLength = if (glyph.text.startsWith(table.reph)) table.reph.length else 0
            glyph.text.forEachIndexed { i, c ->
                val role = when (glyph.role) {
                    GlyphRole.REPH -> if (i < rephLength) GlyphRole.REPH else GlyphRole.TEXT

                    // The reph is written first, already before the cluster; the sign then moves after the cluster.
                    GlyphRole.PREBASE_REPH -> if (i < rephLength) GlyphRole.TEXT else GlyphRole.PREBASE

                    else -> glyph.role
                }
                addPlain(c, source, role)
            }
        }

        fun addPlain(c: Char, source: Int, role: GlyphRole = GlyphRole.TEXT) {
            if (size == chars.size) {
                val grown = maxOf(size * 2, 1)
                chars = chars.copyOf(grown)
                sources = sources.copyOf(grown)
                roles = roles.copyOf(grown)
            }
            chars[size] = c
            sources[size] = source
            roles[size] = role
            size++
        }

        private fun classAt(i: Int): IndicClass =
            if (i in 0 until size) IndicChars.classOf(chars[i]) else IndicClass.NONE

        /**
         * Each pre-base sign moves after the consonant cluster that follows it. Signs of the previous syllable that
         * extraction put between the two (a ृ drawn under its consonant, "o f ` )" for "वृद्धि") are stepped over.
         */
        fun movePrebaseSigns() {
            var i = 0
            while (i < size) {
                val end = if (roles[i] == GlyphRole.PREBASE) prebaseTarget(i) else -1
                if (end < 0) {
                    i++
                } else {
                    move(i, end - 1)
                    i = end
                }
            }
        }

        /** The end of the cluster the pre-base sign at [i] belongs before, or -1 when none follows. */
        private fun prebaseTarget(i: Int): Int {
            var clusterStart = i + 1
            while (classAt(clusterStart).isTail()) clusterStart++
            return IndicReorder.clusterEnd(chars.copyOf(size), clusterStart)
        }

        /** Each reph moves before the consonant cluster of the syllable it was drawn after. */
        fun moveRephs() {
            val keep = BooleanArray(size) { true }
            var i = 0
            while (i + 1 < size) {
                if (roles[i] != GlyphRole.REPH) {
                    i++
                    continue
                }
                val start = IndicReorder.syllableStart(chars.copyOf(size), keep, i)
                if (start >= 0) {
                    // Two moves of one character each: ra, then virama, land before the cluster.
                    move(i, start)
                    move(i + 1, start + 1)
                    roles[start] = GlyphRole.TEXT
                    roles[start + 1] = GlyphRole.TEXT
                }
                i += 2
            }
        }

        /**
         * Puts each syllable's trailing marks in Unicode order, by repeated swaps of neighbours: a nukta before
         * vowel signs, a virama + consonant (a phala such as ्र) before vowel signs and modifiers, vowel signs
         * before anusvara or candrabindu.
         */
        fun orderSyllableTails() {
            var swapped = true
            while (swapped) {
                swapped = false
                var i = 0
                while (i + 1 < size) {
                    val a = classAt(i)
                    val b = classAt(i + 1)
                    when {
                        (a == IndicClass.VOWEL_SIGN && b == IndicClass.NUKTA) ||
                            (a == IndicClass.MODIFIER && b == IndicClass.VOWEL_SIGN) -> {
                            move(i + 1, i)
                            swapped = true
                        }

                        (a == IndicClass.VOWEL_SIGN || a == IndicClass.MODIFIER) && b == IndicClass.VIRAMA &&
                            classAt(i + 2) == IndicClass.CONSONANT && classAt(i - 1) != IndicClass.NONE -> {
                            move(i + 1, i)
                            move(i + 2, i + 1)
                            swapped = true
                        }
                    }
                    i++
                }
            }
        }

        /** A "0" right after an Indic character and not before a digit becomes [sign]. */
        fun markAbbreviations(sign: Char) {
            for (i in 1 until size) {
                val beforeDigit = i + 1 < size && chars[i + 1].isAsciiDigit()
                if (chars[i] == ZERO && IndicChars.isIndic(chars[i - 1]) && !beforeDigit) chars[i] = sign
            }
        }

        /** Moves the character at [from] to [to] with its role; positions keep their source offsets. */
        private fun move(from: Int, to: Int) {
            if (from == to) return
            val c = chars[from]
            val role = roles[from]
            if (from < to) {
                System.arraycopy(chars, from + 1, chars, from, to - from)
                System.arraycopy(roles, from + 1, roles, from, to - from)
            } else {
                System.arraycopy(chars, to, chars, to + 1, from - to)
                System.arraycopy(roles, to, roles, to + 1, from - to)
            }
            chars[to] = c
            roles[to] = role
        }

        fun appendComposed(compositions: Map<String, String>, out: MappedText.Builder) {
            var i = 0
            while (i < size) {
                val pair = if (i + 1 < size) compositions[String(charArrayOf(chars[i], chars[i + 1]))] else null
                if (pair != null) {
                    // The composed letter may compose again with the next one (अ + ा + े → ओ).
                    chars[i + 1] = pair[0]
                    sources[i + 1] = sources[i]
                } else {
                    out.append(chars[i], sources[i])
                }
                i++
            }
        }
    }
}

private fun Char.isAsciiDigit() = this in '0'..'9'

private fun IndicClass.isTail() =
    this == IndicClass.VOWEL_SIGN || this == IndicClass.MODIFIER || this == IndicClass.NUKTA
