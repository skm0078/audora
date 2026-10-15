// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/extract/RawPageFixture.kt
// Purpose: Reads the .rawpage fixtures written by scripts/pdf/rawpage_dump.cpp, and builds small synthetic pages.

package io.github.skm0078.audora.pdf.extract

object RawPageFixture {
    fun load(name: String): RawPage {
        val stream = requireNotNull(javaClass.getResourceAsStream("/rawpages/$name.rawpage")) { "missing $name" }
        var pageIndex = 0
        val fonts = ArrayList<String>()
        val codePoints = ArrayList<Int>()
        val flags = ArrayList<Int>()
        val boxes = ArrayList<Float>()
        val sizes = ArrayList<Float>()
        val fontIds = ArrayList<Int>()
        val objects = ArrayList<RawTextObject>()
        stream.bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
                val f = line.split(' ')
                when (f[0]) {
                    "page" -> pageIndex = f[1].toInt()

                    "font" -> fonts.add(if (f[2] == "-") "" else String(hex(f[2]), Charsets.UTF_8))

                    "char" -> {
                        codePoints.add(f[1].toInt())
                        flags.add(RawChars.flagsOf(f[2] == "1", f[3] == "1", f[4].toInt()))
                        (5..8).forEach { boxes.add(f[it].toFloat()) }
                        sizes.add(f[9].toFloat())
                        fontIds.add(f[10].toInt())
                    }

                    "obj" -> objects.add(
                        RawTextObject(
                            text = if (f[1] == "-") "" else String(hex(f[1]), Charsets.UTF_8),
                            actualText = when (f[2]) {
                                "-" -> null
                                "=" -> ""
                                else -> PdfTextString.decode(hex(f[2]))
                            },
                            spanKey = f[3].toLong(),
                            width = f[4].toFloat(),
                        ),
                    )

                    else -> error("unknown record ${f[0]}")
                }
            }
        }
        val chars = RawChars(
            codePoints.toIntArray(),
            flags.toIntArray(),
            boxes.toFloatArray(),
            sizes.toFloatArray(),
            fontIds.toIntArray(),
            fonts,
        )
        return RawPage(pageIndex, chars, objects)
    }

    fun expectedText(name: String): String =
        requireNotNull(javaClass.getResourceAsStream("/rawpages/$name.expected.txt")).bufferedReader().readText()

    /**
     * A synthetic page. Each [SyntheticChar] is one PDFium char; boxes advance 10 pt per char on one line.
     */
    fun page(chars: List<SyntheticChar>, objects: List<RawTextObject> = emptyList(), pageIndex: Int = 0): RawPage {
        val n = chars.size
        val boxes = FloatArray(n * RawChars.BOX_FLOATS)
        chars.forEachIndexed { i, c ->
            val box = c.box ?: floatArrayOf(i * 10f, 20f, i * 10f + 8f, 10f)
            box.copyInto(boxes, i * RawChars.BOX_FLOATS)
        }
        return RawPage(
            pageIndex = pageIndex,
            chars = RawChars(
                codePoints = IntArray(n) { chars[it].codePoint },
                flags = IntArray(n) { RawChars.flagsOf(chars[it].generated, chars[it].mapError, chars[it].renderMode) },
                boxes = boxes,
                fontSizes = FloatArray(n) { chars[it].fontSize },
                fontIds = IntArray(n) { if (chars[it].generated) RawChars.NO_FONT else chars[it].fontId },
                fontNames = listOf("Body", "Heading"),
            ),
            textObjects = objects,
        )
    }

    /** "मा|नव" style helper: real chars of [text], with a generated char wherever [generated] holds. */
    fun chars(text: String, generated: Set<Int> = emptySet()): List<SyntheticChar> {
        val out = ArrayList<SyntheticChar>()
        var i = 0
        var index = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            out.add(SyntheticChar(cp, generated = index in generated))
            i += Character.charCount(cp)
            index++
        }
        return out
    }

    private fun hex(s: String): ByteArray = ByteArray(s.length / 2) {
        s.substring(it * 2, it * 2 + 2).toInt(16).toByte()
    }
}

data class SyntheticChar(
    val codePoint: Int,
    val generated: Boolean = false,
    val mapError: Boolean = false,
    val renderMode: Int = 0,
    val fontId: Int = 0,
    val fontSize: Float = 12f,
    val box: FloatArray? = null,
)

/** Character error rate: Levenshtein distance over the length of [expected]. */
fun characterErrorRate(expected: String, actual: String): Double {
    if (expected.isEmpty()) return if (actual.isEmpty()) 0.0 else 1.0
    var previous = IntArray(actual.length + 1) { it }
    var current = IntArray(actual.length + 1)
    for (i in 1..expected.length) {
        current[0] = i
        for (j in 1..actual.length) {
            val substitution = previous[j - 1] + if (expected[i - 1] == actual[j - 1]) 0 else 1
            current[j] = minOf(previous[j] + 1, current[j - 1] + 1, substitution)
        }
        val swap = previous
        previous = current
        current = swap
    }
    return previous[actual.length].toDouble() / expected.length
}

/** Collapses whitespace and drops ZWNJ/ZWJ (P2-03 folds those) for text comparisons. */
fun normalizeForComparison(s: String): String =
    s.replace("\u200C", "").replace("\u200D", "").split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")

/** The S2 corpus footer ("Audora S2 test corpus - hi-udhr-noto", page number) is not in the ground truth. */
fun withoutCorpusFooter(s: String): String = s.replace(Regex("Audora S2 test corpus - \\S+\\s+\\d+"), " ")
