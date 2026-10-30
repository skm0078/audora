// File: app/src/main/kotlin/io/github/skm0078/audora/ui/share/ShareCardScreen.kt
// Purpose: The card screen: the picture as it will be shared, filling the space it has, with its size, background,
//          an optional dedication and Share (mockups "Quote card" and "Share sheet" on the design canvas).

package io.github.skm0078.audora.ui.share

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.config.ShareCardConfig
import io.github.skm0078.audora.core.share.PhotoFocus
import io.github.skm0078.audora.ui.theme.Dimens

/**
 * The listener's choices on the card screen. A [video] is always a story (9:16, what Status, Reels and Shorts show
 * full screen), at 1080 x 1920 when [sharp], else 720 x 1280. [wholePage] shows the whole page instead of the lines
 * or part picked (ADR 2026-10-30-whole-page-cards); a video always reads the lines picked.
 */
data class CardChoices(
    val shape: CardShape = CardShape.STORY,
    val look: CardLook = CardLook.DUSK,
    val dedication: String = "",
    val video: Boolean = false,
    val sharp: Boolean = false,
    val wholePage: Boolean = false,
) {
    /** The card's size as drawn: a video's is the story's, whatever size the picture had. */
    val shownShape: CardShape get() = if (video) CardShape.STORY else shape

    /** The whole page is what the card shows: picked, and not a video. */
    val showsWholePage: Boolean get() = wholePage && !video
}

/** What the screen's controls do; the route keeps the state and does the work. */
interface CardScreenActions {
    fun onBack()

    fun onChoices(choices: CardChoices)

    /** The listener tapped "Your own picture": open the photo picker. */
    fun onPickPicture()

    fun onShare()

    /** Video mode's button: the card is captured without its words and the video made in the background. */
    fun onMakeVideo() = Unit

    /** The listener dragged their own picture on the card, or moved it with a TalkBack action. */
    fun onPhotoFocus(focus: PhotoFocus) = Unit
}

/**
 * Phones in portrait stack the picture over its controls, the picture taking all the height the controls leave; a
 * short, wide window (landscape, split screen) puts them side by side, so the picture is never a postage stamp.
 */
@Composable
fun ShareCardScreen(
    subject: CardSubject,
    choices: CardChoices,
    picture: CardPhoto?,
    busy: Boolean,
    recorder: CardRecorder,
    actions: CardScreenActions,
) {
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val height = maxHeight
        val sideBySide = maxWidth > height && height < SIDE_BY_SIDE_BELOW
        val title = when {
            subject.body == CardBody.WholeBook -> R.string.share_card_book_title
            choices.video -> R.string.share_card_video_title
            choices.showsWholePage -> R.string.share_card_page_title
            subject.body is CardBody.Snippet -> R.string.share_card_snippet_title
            else -> R.string.share_card_title
        }
        // A whole page of text set too small to fit the size picked says so under the card.
        var pageOverflow by remember { mutableStateOf(false) }
        val preview = @Composable { modifier: Modifier ->
            CardPreview(subject, choices, picture, recorder, actions::onPhotoFocus, { pageOverflow = it }, modifier)
        }
        val controls = @Composable { modifier: Modifier ->
            CardControls(subject, choices, busy, actions, pageOverflow, modifier)
        }
        if (sideBySide) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    TopBar(title, actions::onBack)
                    preview(Modifier.weight(1f).fillMaxWidth().padding(Dimens.ScreenPadding))
                }
                controls(Modifier.width(CONTROLS_WIDTH).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                TopBar(title, actions::onBack)
                preview(Modifier.weight(1f).fillMaxWidth().padding(horizontal = Dimens.ScreenPadding))
                controls(Modifier.fillMaxWidth().heightIn(max = height * CONTROLS_SHARE))
            }
        }
    }
}

@Composable
private fun TopBar(title: Int, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = TOP_BAR_HEIGHT).padding(horizontal = Dimens.SpaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.share_card_back))
        }
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
private fun CardPreview(
    subject: CardSubject,
    choices: CardChoices,
    picture: CardPhoto?,
    recorder: CardRecorder,
    onPhotoFocus: (PhotoFocus) -> Unit,
    onPageOverflow: (Boolean) -> Unit,
    modifier: Modifier,
) {
    val look = choices.look
    // The quote read aloud over the card, in Video mode; a book or a snippet has no words to read.
    val videoQuote = subject.quote?.takeIf { choices.video }
    val video = videoQuote != null
    val description = stringResource(if (video) R.string.share_card_video_preview else R.string.share_card_preview)
    ShareCardFrame(
        choices.shownShape,
        recorder,
        markColor = look.quiet,
        modifier = modifier.semantics { contentDescription = description },
        overlay = videoQuote?.let { rememberVideoWords(it, look, choices.shownShape) },
    ) {
        // Only the listener's own picture moves; the book's softened cover stays centred.
        val own = picture?.takeIf { look == CardLook.PICTURE }
        val backdrop = own ?: subject.softCover?.let(::CardPhoto)
        val placing = if (own != null) Modifier.photoDrag(own, onPhotoFocus) else Modifier
        Box(Modifier.fillMaxSize().then(placing)) {
            CardContent(subject, choices, backdrop, quoteShown = !video, onPageOverflow)
        }
    }
}

@Composable
private fun CardContent(
    subject: CardSubject,
    choices: CardChoices,
    backdrop: CardPhoto?,
    quoteShown: Boolean,
    onPageOverflow: (Boolean) -> Unit,
) {
    val look = choices.look
    when (val body = subject.body) {
        is CardBody.Snippet -> SnippetCard(
            SnippetCardText(subject.title, subject.author, body.pageLabel, choices.dedication),
            look,
            if (choices.showsWholePage) body.wholePage else body.picture,
            backdrop,
        )

        is CardBody.Quote -> {
            val page = body.wholePage?.takeIf { choices.showsWholePage }
            QuoteCard(
                QuoteCardText(page ?: body.quote, subject.title, subject.author, body.pageLabel, choices.dedication),
                look,
                backdrop,
                quoteShown = quoteShown,
                fit = if (page != null) QuoteFit.PAGE else QuoteFit.QUOTE,
                onOverflow = onPageOverflow,
            )
        }

        CardBody.WholeBook -> BookCard(
            BookCardText(
                stringResource(R.string.share_card_now_listening),
                subject.title,
                subject.author,
                choices.dedication,
            ),
            look,
            subject.cover,
            backdrop,
        )
    }
}

@Composable
private fun CardControls(
    subject: CardSubject,
    choices: CardChoices,
    busy: Boolean,
    actions: CardScreenActions,
    pageOverflow: Boolean,
    modifier: Modifier,
) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
    ) {
        CutNote(subject, choices, pageOverflow)
        if (subject.quote != null) KindChips(choices.video) { actions.onChoices(choices.copy(video = it)) }
        if (subject.hasWholePage && !choices.video) {
            SpanChips(choices.wholePage) { actions.onChoices(choices.copy(wholePage = it)) }
        }
        if (choices.video) {
            QualityChips(choices.sharp) { actions.onChoices(choices.copy(sharp = it)) }
        } else {
            ShapeChips(choices.shape) { actions.onChoices(choices.copy(shape = it)) }
        }
        LookSwatches(
            choices.look,
            hasCover = subject.softCover != null,
            onLook = { actions.onChoices(choices.copy(look = it)) },
            onPickPicture = actions::onPickPicture,
        )
        DedicationField(choices.dedication) { actions.onChoices(choices.copy(dedication = it)) }
        if (choices.video) {
            ShareButton(busy, R.string.share_card_make_video, actions::onMakeVideo)
        } else {
            ShareButton(busy, R.string.share_card_send, actions::onShare)
        }
    }
}

/** Why the card shows less than was picked: a quote cut to 60 words, or a page cut or too long for the size. */
@Composable
private fun CutNote(subject: CardSubject, choices: CardChoices, pageOverflow: Boolean) {
    val page = (subject.body as? CardBody.Quote)?.wholePage?.takeIf { choices.showsWholePage }
    val note = when {
        page == null -> stringResource(R.string.share_card_cut_note).takeIf { subject.quote?.cut == true }
        page.cut -> stringResource(R.string.share_card_page_cut, ShareCardConfig().maxPageWords)
        pageOverflow -> stringResource(R.string.share_card_page_overflow)
        else -> null
    }
    note?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ShareButton(busy: Boolean, @StringRes label: Int, onShare: () -> Unit) {
    Button(
        onClick = onShare,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth().heightIn(min = SHARE_BUTTON_HEIGHT),
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(PROGRESS_SIZE), strokeWidth = PROGRESS_STROKE)
            Spacer(Modifier.width(Dimens.SpaceS))
        }
        Text(stringResource(label), textAlign = TextAlign.Center)
    }
}

/** A phone wider than tall and shorter than this shows the picture beside its controls. */
private val SIDE_BY_SIDE_BELOW = 560.dp
private val CONTROLS_WIDTH = 340.dp
private val TOP_BAR_HEIGHT = 64.dp
private val SHARE_BUTTON_HEIGHT = 56.dp
private val PROGRESS_SIZE = 20.dp
private val PROGRESS_STROKE = 2.dp

// The controls never take more than this share of a tall screen; past it they scroll and the picture keeps the rest.
private const val CONTROLS_SHARE = 0.55f
