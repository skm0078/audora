// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/LightingConfig.kt
// Purpose: Tunables for evening out a photographed page's light (a hard shadow) before OCR reads it.

package io.github.skm0078.audora.core.config

/**
 * A phone photo of a page often has a hand's or the phone's shadow across it. Sauvola ([OcrThreshold]) copes with
 * light that changes slowly but not with a shadow's edge, so such a page is read again with its light evened out:
 * the paper's brightness is estimated on a copy shrunk to one sample per block (the brightest pixel of each, so ink
 * drops out), widened and smoothed there, and every pixel divided by it. Only a page whose paper is uneven and that
 * read unsure is read again, and the evened read is kept only when it reads clearly better with as many words: a
 * cover's white title on a grey band reads worse evened (the band turns white), so it keeps its first read.
 *
 * Measured on UDHR pages in English, Hindi and Bengali with a hard shadow: Sauvola alone read them at 71-78 mean
 * confidence and 15-33 % character error, evened at 92-96 and 3-10 %, as well as the same pages unshadowed. Pages
 * with a gradient, a vignette or blur read at 87 or more and are left alone. Every test scan has paper at least 0.95
 * even, so scans are never touched.
 *
 * @property enabled off never evens a page.
 * @property readAgainBelow a page read with a mean word confidence (0–100) below this may be read again evened.
 * @property unevenBelow the paper's evenness (the 5th percentile of its estimated brightness over the 95th) below
 *   which a page counts as unevenly lit.
 * @property minGain the evened read is kept only when its mean confidence is at least this much higher,
 * @property minWordShare it holds at least this share of the first read's words,
 * @property minEvenedConfidence its mean confidence is at least this (a page that read no words takes no junk),
 *   and its confidence summed over its characters is higher too: the mean is weighted by characters, so a read that
 *   drops a few sure words (a cover's title) can raise it while reading less.
 * @property blockPerLine one brightness sample per block this share of the first read's median line height wide:
 *   smaller than a letter's stroke gap, so a block always holds some paper between strokes; about 9 pixels for body
 *   text at 300 dpi. Taken from the text, not the render's dpi, which a photo's page size may not match.
 * @property blockInches the block's width in inches when the first read found no lines.
 * @property maxSamples the block is never so small that the page has more samples than this, which bounds the
 *   memory a large photo takes (an int per sample, several arrays).
 * @property widenBlocks the samples are widened (each takes the brightest within this many blocks), so a block
 *   inside a heading's letter still finds paper; 3 covers 24 pt type.
 * @property smoothBlocks then averaged over this many blocks each way, so the estimate follows the light, not the
 *   blocks.
 */
data class LightingConfig(
    val enabled: Boolean = true,
    val readAgainBelow: Int = 85,
    val unevenBelow: Float = 0.85f,
    val minGain: Int = 5,
    val minWordShare: Float = 0.9f,
    val minEvenedConfidence: Int = 80,
    val blockPerLine: Float = 0.17f,
    val blockInches: Float = 0.03125f, // 1/32 inch
    val maxSamples: Int = 250_000,
    val widenBlocks: Int = 3,
    val smoothBlocks: Int = 2,
) {
    init {
        require(readAgainBelow in 0..MAX_CONFIDENCE) { "readAgainBelow must be in 0..100, was $readAgainBelow" }
        require(unevenBelow > 0f && unevenBelow <= 1f) { "unevenBelow must be in (0, 1], was $unevenBelow" }
        require(minGain >= 0) { "minGain must be >= 0, was $minGain" }
        require(minWordShare >= 0f && minWordShare <= 1f) { "minWordShare must be in [0, 1], was $minWordShare" }
        require(minEvenedConfidence in 0..MAX_CONFIDENCE) { "minEvenedConfidence must be in 0..100" }
        require(blockPerLine > 0f && blockInches > 0f) { "blockPerLine and blockInches must be > 0" }
        require(maxSamples > 0) { "maxSamples must be > 0, was $maxSamples" }
        require(widenBlocks >= 0 && smoothBlocks >= 0) { "widenBlocks and smoothBlocks must be >= 0" }
    }

    private companion object {
        const val MAX_CONFIDENCE = 100
    }
}
