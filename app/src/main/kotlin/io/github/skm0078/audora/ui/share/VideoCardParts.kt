// File: app/src/main/kotlin/io/github/skm0078/audora/ui/share/VideoCardParts.kt
// Purpose: The card screen's video mode: Picture or Video, the video's quality, and the preview's words drawn by the
//          video's own painter (ADR 2026-10-28-quote-video).

package io.github.skm0078.audora.ui.share

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.share.CardQuote
import io.github.skm0078.audora.playback.R as PlaybackR
import io.github.skm0078.audora.playback.export.video.AiVoiceLabel
import io.github.skm0078.audora.playback.export.video.QuoteVideoFonts
import io.github.skm0078.audora.playback.export.video.QuoteVideoPainter
import io.github.skm0078.audora.playback.export.video.QuoteVideoText
import io.github.skm0078.audora.ui.theme.Dimens

/** Picture or Video, for a quote: the same card, still or read aloud. */
@Composable
internal fun KindChips(video: Boolean, onVideo: (Boolean) -> Unit) {
    ChipRow(R.string.share_card_kind) {
        FilterChip(
            selected = !video,
            onClick = { onVideo(false) },
            label = { Text(stringResource(R.string.share_card_kind_picture)) },
        )
        FilterChip(
            selected = video,
            onClick = { onVideo(true) },
            label = { Text(stringResource(R.string.share_card_kind_video)) },
        )
    }
}

/** Standard (720p, the default: quick to make on any phone) or Sharp (1080p: about twice the work, a bigger file). */
@Composable
internal fun QualityChips(sharp: Boolean, onSharp: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
        ChipRow(R.string.share_card_video_quality) {
            FilterChip(
                selected = !sharp,
                onClick = { onSharp(false) },
                label = { Text(stringResource(R.string.share_card_video_standard)) },
            )
            FilterChip(
                selected = sharp,
                onClick = { onSharp(true) },
                label = { Text(stringResource(R.string.share_card_video_sharp)) },
            )
        }
        if (sharp) {
            Text(
                stringResource(R.string.share_card_video_sharp_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A titled row of chips that scrolls sideways when large text makes it wider than the screen. */
@Composable
private fun ChipRow(@StringRes title: Int, chips: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
        ) { chips() }
    }
}

/**
 * The video's first moment over the card: its words laid out by the same painter and fonts the video uses, the first
 * word lit, and the "AI voice" label, all in the card's full-size pixels (the frame's overlay is not recorded, so the
 * backdrop the video is drawn over stays without them).
 */
@Composable
internal fun rememberVideoWords(quote: CardQuote, look: CardLook, shape: CardShape): DrawScope.() -> Unit {
    val context = LocalContext.current
    val labelText = stringResource(PlaybackR.string.video_ai_label)
    val ink = look.ink.toArgb()
    val accent = look.accent.toArgb()
    return remember(quote, ink, accent, shape, labelText) {
        val text = QuoteVideoText(quote.text, QuoteVideoFonts.of(context, quote.script), ink, accent)
        val painter = QuoteVideoPainter(text, shape.widthPx, shape.heightPx)
        val label = AiVoiceLabel(labelText)
        val draw: DrawScope.() -> Unit = {
            drawIntoCanvas { canvas ->
                painter.draw(canvas.nativeCanvas, FIRST_WORD)
                label.draw(canvas.nativeCanvas, shape.heightPx)
            }
        }
        draw
    }
}

private const val FIRST_WORD = 0
