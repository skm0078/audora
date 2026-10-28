// File: app/src/main/kotlin/io/github/skm0078/audora/ui/share/QuoteCard.kt
// Purpose: The two cards drawn inside a ShareCardFrame: a quote with its credit, and a book on its own.

package io.github.skm0078.audora.ui.share

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.skm0078.audora.core.share.CardQuote

/** What a quote card says; [pageLabel] and the book's lines are already in the quote's language. */
data class QuoteCardText(
    val quote: CardQuote,
    val title: String,
    val author: String?,
    val pageLabel: String?,
    val dedication: String = "",
)

/** What a book card says: [kicker] ("Now listening") above the book's own title and author. */
data class BookCardText(val kicker: String, val title: String, val author: String?, val dedication: String = "")

/**
 * The quote fills the space between the quote mark and the credit: its size steps down from 34 to 16 card sp until
 * it fits, so a short line reads big on a story and 60 words still fit a square. The credit (title, author, page) is
 * always drawn; there is no way to share a quote without it.
 */
@Composable
fun QuoteCard(text: QuoteCardText, look: CardLook, background: ImageBitmap?) {
    CardBackdrop(look, background) {
        Column(Modifier.fillMaxSize().padding(horizontal = SIDE, vertical = TOP_BOTTOM)) {
            Text(
                OPEN_QUOTE,
                color = look.accent,
                style = TextStyle(fontFamily = QuoteFonts.of(text.quote.script), fontSize = MARK_SIZE),
                modifier = Modifier.height(MARK_BOX),
            )
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                BasicText(
                    text.quote.text,
                    style = TextStyle(
                        fontFamily = QuoteFonts.of(text.quote.script),
                        color = look.ink,
                        lineHeight = QUOTE_LINE_HEIGHT,
                    ),
                    autoSize = TextAutoSize.StepBased(QUOTE_MIN, QUOTE_MAX, QUOTE_STEP),
                )
            }
            Spacer(Modifier.height(SPACE_ABOVE_CREDIT))
            Box(Modifier.width(RULE_WIDTH).height(RULE_HEIGHT).background(look.accent))
            Spacer(Modifier.height(SPACE_IN_CREDIT))
            Credit(text.title, listOfNotNull(text.author, text.pageLabel).joinToString(" · "), text.dedication, look)
        }
    }
}

/** A book on its own, for the library's Share: its cover big in the middle, then what it is. */
@Composable
fun BookCard(text: BookCardText, look: CardLook, cover: ImageBitmap?, backdrop: ImageBitmap?) {
    CardBackdrop(look, backdrop) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = SIDE, vertical = TOP_BOTTOM),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (cover != null) {
                Image(
                    cover,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.High,
                    modifier = Modifier.size(COVER_WIDTH, COVER_HEIGHT).clip(RoundedCornerShape(COVER_CORNER)),
                )
                Spacer(Modifier.height(SPACE_UNDER_COVER))
            }
            Text(text.kicker, color = look.quiet, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(SPACE_IN_CREDIT))
            Text(
                text.title,
                color = look.ink,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                maxLines = TITLE_LINES,
                overflow = TextOverflow.Ellipsis,
            )
            text.author?.let { author ->
                Text(
                    author,
                    color = look.quiet,
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                )
            }
            if (text.dedication.isNotBlank()) {
                Spacer(Modifier.height(SPACE_IN_CREDIT))
                Text(text.dedication, color = look.quiet, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun Credit(title: String, byline: String, dedication: String, look: CardLook) {
    Text(
        title,
        color = look.ink,
        style = MaterialTheme.typography.titleSmall,
        maxLines = TITLE_LINES,
        overflow = TextOverflow.Ellipsis,
    )
    if (byline.isNotEmpty()) Text(byline, color = look.quiet, style = MaterialTheme.typography.bodySmall)
    if (dedication.isNotBlank()) {
        Spacer(Modifier.height(SPACE_IN_CREDIT))
        Text(dedication, color = look.quiet, style = MaterialTheme.typography.bodySmall)
    }
    // The mark sits in the bottom corner; the credit never runs under it.
    Spacer(Modifier.height(MARK_CLEARANCE))
}

/** The look's colour, or a photo under its scrim so the card's white text stays readable on any picture. */
@Composable
private fun CardBackdrop(look: CardLook, photo: ImageBitmap?, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(look.brush)) {
        if (look.isPhoto && photo != null) {
            Image(
                photo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High,
                modifier = Modifier.fillMaxSize(),
            )
            Box(Modifier.fillMaxSize().background(look.scrim))
        }
        content()
    }
}

private const val OPEN_QUOTE = "“"
private const val TITLE_LINES = 2

// Card dp and sp: the card is 360 wide, so these are the same share of every picture.
private val SIDE = 32.dp
private val TOP_BOTTOM = 40.dp
private val MARK_SIZE = 72.sp
private val MARK_BOX = 56.dp
private val QUOTE_MIN = 16.sp
private val QUOTE_MAX = 34.sp
private val QUOTE_STEP = 1.sp
private val QUOTE_LINE_HEIGHT = 1.45.em
private val SPACE_ABOVE_CREDIT = 24.dp
private val SPACE_IN_CREDIT = 8.dp
private val SPACE_UNDER_COVER = 24.dp
private val RULE_WIDTH = 32.dp
private val RULE_HEIGHT = 2.dp
private val MARK_CLEARANCE = 20.dp
private val COVER_WIDTH = 168.dp
private val COVER_HEIGHT = 240.dp
private val COVER_CORNER = 10.dp
