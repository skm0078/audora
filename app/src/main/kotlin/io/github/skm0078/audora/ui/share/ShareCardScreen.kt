// File: app/src/main/kotlin/io/github/skm0078/audora/ui/share/ShareCardScreen.kt
// Purpose: The card screen: the picture as it will be shared, filling the space it has, with its size, background,
//          an optional dedication and Share (mockups "Quote card" and "Share sheet" on the design canvas).

package io.github.skm0078.audora.ui.share

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.ui.theme.Dimens

/** The listener's choices on the card screen. */
data class CardChoices(
    val shape: CardShape = CardShape.STORY,
    val look: CardLook = CardLook.DUSK,
    val dedication: String = "",
)

/** What the screen's controls do; the route keeps the state and does the work. */
interface CardScreenActions {
    fun onBack()

    fun onChoices(choices: CardChoices)

    /** The listener tapped "Your own picture": open the photo picker. */
    fun onPickPicture()

    fun onShare()
}

/**
 * Phones in portrait stack the picture over its controls, the picture taking all the height the controls leave; a
 * short, wide window (landscape, split screen) puts them side by side, so the picture is never a postage stamp.
 */
@Composable
fun ShareCardScreen(
    subject: CardSubject,
    choices: CardChoices,
    picture: ImageBitmap?,
    busy: Boolean,
    recorder: CardRecorder,
    actions: CardScreenActions,
) {
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val sideBySide = maxWidth > maxHeight && maxHeight < SIDE_BY_SIDE_BELOW
        val title = if (subject.quote != null) R.string.share_card_title else R.string.share_card_book_title
        val preview = @Composable { modifier: Modifier ->
            CardPreview(subject, choices, picture, recorder, modifier)
        }
        val controls = @Composable { modifier: Modifier ->
            CardControls(subject, choices, busy, actions, modifier)
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
                controls(Modifier.fillMaxWidth().heightIn(max = maxHeight * CONTROLS_SHARE))
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
    picture: ImageBitmap?,
    recorder: CardRecorder,
    modifier: Modifier,
) {
    val look = choices.look
    val description = stringResource(R.string.share_card_preview)
    ShareCardFrame(
        choices.shape,
        recorder,
        markColor = look.quiet,
        modifier = modifier.semantics { contentDescription = description },
    ) {
        val backdrop = if (look == CardLook.PICTURE) picture else subject.softCover
        val quote = subject.quote
        if (quote != null) {
            QuoteCard(
                QuoteCardText(quote, subject.title, subject.author, subject.pageLabel, choices.dedication),
                look,
                backdrop,
            )
        } else {
            BookCard(
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
}

@Composable
private fun CardControls(
    subject: CardSubject,
    choices: CardChoices,
    busy: Boolean,
    actions: CardScreenActions,
    modifier: Modifier,
) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
    ) {
        if (subject.quote?.cut == true) {
            Text(
                stringResource(R.string.share_card_cut_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ShapeChips(choices.shape) { actions.onChoices(choices.copy(shape = it)) }
        LookSwatches(
            choices.look,
            hasCover = subject.softCover != null,
            onLook = { actions.onChoices(choices.copy(look = it)) },
            onPickPicture = actions::onPickPicture,
        )
        DedicationField(choices.dedication) { actions.onChoices(choices.copy(dedication = it)) }
        ShareButton(busy, actions::onShare)
    }
}

@Composable
private fun ShareButton(busy: Boolean, onShare: () -> Unit) {
    Button(
        onClick = onShare,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth().heightIn(min = SHARE_BUTTON_HEIGHT),
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(PROGRESS_SIZE), strokeWidth = PROGRESS_STROKE)
            Spacer(Modifier.width(Dimens.SpaceS))
        }
        Text(stringResource(R.string.share_card_send), textAlign = TextAlign.Center)
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
