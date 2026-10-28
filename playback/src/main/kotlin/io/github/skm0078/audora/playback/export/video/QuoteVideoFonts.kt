// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/export/video/QuoteVideoFonts.kt
// Purpose: The quote's typeface on a video, the same Tiro files the share card's quote is set in.

package io.github.skm0078.audora.playback.export.video

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.playback.R

/** Tiro Bangla for Bengali, Tiro Devanagari Hindi (with its Latin) for Hindi and English (SIL OFL 1.1). */
object QuoteVideoFonts {
    /** Reads the font file once per call; the phone's serif if the file cannot be read. */
    fun of(context: Context, script: Lang): Typeface {
        val font = if (script == Lang.BN) R.font.tiro_bangla_regular else R.font.tiro_devanagari_hindi_regular
        return ResourcesCompat.getFont(context, font) ?: Typeface.SERIF
    }
}
