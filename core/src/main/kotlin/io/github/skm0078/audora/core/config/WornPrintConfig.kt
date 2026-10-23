// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/WornPrintConfig.kt
// Purpose: When a scanned page of worn or speckled print is read again softened (Bengali and Hindi books first).

package io.github.skm0078.audora.core.config

/**
 * A book scanned to black and white (most Bengali and Hindi books online) carries the scanner's speckle and the
 * print's broken, ragged strokes, which Tesseract reads as wrong conjuncts and vowel signs. Softened (each pixel
 * averaged with its neighbours, twice), the strokes join up again and the speckle fades. Only a page that read unsure
 * and is drawn almost wholly in black and white is read again, and the softened read is kept only when it reads
 * clearly better with as many words; a grey photo or a clean page keeps its first read.
 *
 * Measured with tessdata_best and Sauvola on magazine pages re-scanned worn (150 dpi, speckle, ragged strokes):
 * Bengali pages rose from 84 and 66 mean confidence to 87 and 73, and from 74 and 62 % of words right to 79 and
 * 74 %; Hindi UDHR pages from 78 and 81 % of words right to 88 and 87 %. Grey low-resolution pages read worse
 * softened (confidence 91 to 88) and are not bilevel, so they are never touched.
 *
 * @property enabled off never softens a page.
 * @property readAgainBelow a page read with a mean word confidence (0–100) below this may be read again softened.
 * @property minBilevelShare only a page with at least this share of its pixels near black or near white (a black and
 *   white scan; grey photos and renders hold more mid-tones) is softened. Worn test scans: 0.90-0.92.
 * @property darkBelow a pixel darker than this (0–255) counts as black.
 * @property lightAbove a pixel lighter than this (0–255) counts as white.
 * @property radiusPerInch the softening averages each pixel over a square reaching this many inches each way (one
 *   pixel at 300 dpi), at least one pixel.
 * @property passes the averaging runs this many times; two passes of a box come close to a Gaussian blur.
 * @property minGain the softened read is kept only when its mean confidence is at least this much higher,
 * @property minWordShare it holds at least this share of the first read's words,
 * @property minSoftenedConfidence its mean confidence is at least this, and its confidence summed over its
 *   characters is higher too.
 */
data class WornPrintConfig(
    val enabled: Boolean = true,
    val readAgainBelow: Int = 88,
    val minBilevelShare: Float = 0.85f,
    val darkBelow: Int = 48,
    val lightAbove: Int = 207,
    val radiusPerInch: Float = 1f / REFERENCE_DPI,
    val passes: Int = 2,
    val minGain: Int = 2,
    val minWordShare: Float = 0.95f,
    val minSoftenedConfidence: Int = 60,
) {
    init {
        require(readAgainBelow in 0..MAX_CONFIDENCE) { "readAgainBelow must be in 0..100, was $readAgainBelow" }
        require(minBilevelShare in 0f..1f) { "minBilevelShare must be in 0..1, was $minBilevelShare" }
        require(darkBelow in 0..lightAbove && lightAbove <= MAX_GREY) { "need 0 <= darkBelow <= lightAbove <= 255" }
        require(radiusPerInch > 0f && passes > 0) { "radiusPerInch and passes must be > 0" }
        require(minGain >= 0) { "minGain must be >= 0, was $minGain" }
        require(minWordShare in 0f..1f) { "minWordShare must be in 0..1, was $minWordShare" }
        require(minSoftenedConfidence in 0..MAX_CONFIDENCE) { "minSoftenedConfidence must be in 0..100" }
    }

    private companion object {
        /** The resolution Tesseract reads body text best at, where the softening reaches one pixel. */
        const val REFERENCE_DPI = 300f
        const val MAX_CONFIDENCE = 100
        const val MAX_GREY = 255
    }
}
