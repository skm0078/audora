// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/ocr/PageImages.kt
// Purpose: Grey page images turned a quarter at a time, shrunk for the orientation probe, and cut into strips.

package io.github.skm0078.audora.pdf.ocr

import io.github.skm0078.audora.pdf.document.PageImage
import kotlin.math.roundToInt

/** The image turned [quarterTurns] quarter turns clockwise. Copies the pixels (one byte each). */
internal fun PageImage.turned(quarterTurns: Int): PageImage {
    val turns = Math.floorMod(quarterTurns, OcrText.FULL_TURN)
    if (turns == 0) return this
    val sideways = turns % 2 == 1
    val outWidth = if (sideways) height else width
    val outHeight = if (sideways) width else height
    val out = ByteArray(gray.size)
    // Where pixel (x, y) lands: index = start + x·stepX + y·stepY. Clockwise quarter: (H − 1 − y, x); half:
    // (W − 1 − x, H − 1 − y); three quarters: (y, W − 1 − x).
    val (start, stepX, stepY) = when (turns) {
        1 -> Triple(height - 1, outWidth, -1)
        2 -> Triple(gray.size - 1, -1, -width)
        else -> Triple((outHeight - 1) * outWidth, -outWidth, 1)
    }
    for (y in 0 until height) {
        val row = y * width
        var target = start + y * stepY
        for (x in 0 until width) {
            out[target] = gray[row + x]
            target += stepX
        }
    }
    return PageImage(pageIndex, outWidth, outHeight, dpi, out)
}

/**
 * The image at about [targetDpi] (never larger), each pixel the mean of the block it covers: cheap enough to read a
 * page four times over for its orientation.
 */
internal fun PageImage.shrunkTo(targetDpi: Int): PageImage {
    val factor = dpi / targetDpi
    val outWidth = width / factor.coerceAtLeast(1)
    val outHeight = height / factor.coerceAtLeast(1)
    if (factor <= 1 || outWidth == 0 || outHeight == 0) return this
    val out = ByteArray(outWidth * outHeight) { i -> blockMean(i % outWidth, i / outWidth, factor) }
    return PageImage(pageIndex, outWidth, outHeight, dpi / factor, out)
}

/** The mean of the [factor] × [factor] block whose top-left pixel is ([ox]·factor, [oy]·factor). */
private fun PageImage.blockMean(ox: Int, oy: Int, factor: Int): Byte {
    var sum = 0
    for (dy in 0 until factor) {
        val row = (oy * factor + dy) * width + ox * factor
        for (dx in 0 until factor) sum += gray[row + dx].toInt() and BYTE_MASK
    }
    return (sum / (factor * factor)).toByte()
}

/** Rows from [fromShare] to [toShare] of the image's height (0 = top), or null if that is less than a row. */
internal fun PageImage.strip(fromShare: Float, toShare: Float): PageImage? {
    val top = (fromShare * height).roundToInt().coerceIn(0, height)
    val bottom = (toShare * height).roundToInt().coerceIn(top, height)
    if (bottom == top) return null
    return PageImage(pageIndex, width, bottom - top, dpi, gray.copyOfRange(top * width, bottom * width))
}

private const val BYTE_MASK = 0xFF
