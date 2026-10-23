// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/OcrQuality.kt
// Purpose: What the "OCR" badge says about a scanned page: its OCR quality band from the engine's confidence (RI-9).

package io.github.skm0078.audora.ui.reader

import androidx.annotation.StringRes
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.config.ReaderConfig

enum class OcrQuality { GOOD, FAIR, POOR }

/** [confidence] (0–100) in [config]'s bands. */
fun ocrQualityOf(confidence: Int, config: ReaderConfig): OcrQuality = when {
    confidence >= config.ocrGoodFrom -> OcrQuality.GOOD
    confidence >= config.ocrFairFrom -> OcrQuality.FAIR
    else -> OcrQuality.POOR
}

/**
 * The line beside the "OCR" badge, or null when there is no badge: only a page read by OCR has one (the other
 * origins show nothing or their own notice). Without a confidence (an engine that gives none) it keeps the general
 * warning.
 */
@StringRes
fun ocrBadgeText(origin: PageOrigin, confidence: Int?, config: ReaderConfig): Int? = when {
    origin != PageOrigin.OCR -> null

    confidence == null -> R.string.reader_ocr_badge_body

    else -> when (ocrQualityOf(confidence, config)) {
        OcrQuality.GOOD -> R.string.reader_ocr_quality_good
        OcrQuality.FAIR -> R.string.reader_ocr_quality_fair
        OcrQuality.POOR -> R.string.reader_ocr_quality_poor
    }
}
