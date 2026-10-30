// File: app/src/main/kotlin/io/github/skm0078/audora/ui/jobs/JobCard.kt
// Purpose: The one card every long job and download is shown in: an icon toned by how it is going, the job, a status
//          pill, one line of detail, a rounded bar and its buttons on the right (ADR
//          2026-10-29-jobs-card-and-leftovers).

package io.github.skm0078.audora.ui.jobs

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.ui.theme.Dimens

/** How a job is going, which sets the icon's and the pill's colours. */
enum class JobTone { RUNNING, WAITING, DONE, FAILED, STOPPED }

/** What the bar shows: nothing (a finished job), a moving bar (length not known yet) or how far it is (0..1). */
@Immutable
sealed interface JobBar {
    data object None : JobBar

    data object Moving : JobBar

    data class Filled(val fraction: Float) : JobBar
}

/**
 * [title] names the job ("Saving “Gitanjali” as an audiobook", "Voice: Bruno") and [status] is its pill ("34%",
 * "Didn't finish"; none for a card that is not a job). [detail] is the line under it; [notes] are what an ended
 * job leaves the listener to know, announced once by TalkBack with [detail] ([announce]). Progress is never
 * announced: read on every step it would talk over the book.
 */
@OptIn(ExperimentalLayoutApi::class)
@Suppress("LongParameterList") // One card for every job: each part is what that job says, none derivable.
@Composable
internal fun JobCard(
    @DrawableRes icon: Int,
    tone: JobTone,
    title: String,
    status: String?,
    detail: String?,
    bar: JobBar,
    modifier: Modifier = Modifier,
    notes: List<String> = emptyList(),
    announce: Boolean = false,
    actions: @Composable RowScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val border = if (tone == JobTone.FAILED) colors.error.copy(alpha = FAILED_BORDER_ALPHA) else colors.outlineVariant
    Card(
        shape = RoundedCornerShape(CARD_RADIUS),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
        border = BorderStroke(1.dp, border),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(start = Dimens.SpaceL, top = Dimens.SpaceL, end = Dimens.SpaceL, bottom = Dimens.SpaceS),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM)) {
                ToneIcon(icon, tone)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
                    Row(
                        Modifier.semantics(mergeDescendants = true) {},
                        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
                    ) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = TITLE_LINES,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        status?.let { StatusPill(it, tone) }
                    }
                    val told = if (announce) {
                        Modifier.semantics(mergeDescendants = true) {
                            liveRegion = LiveRegionMode.Polite
                        }
                    } else {
                        Modifier
                    }
                    Column(told, verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
                        detail?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                        }
                        notes.forEach {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                    }
                    JobProgress(bar, Modifier.padding(top = Dimens.SpaceXs))
                }
            }
            FlowRow(
                Modifier.fillMaxWidth().padding(top = Dimens.SpaceS),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
            ) { actions() }
        }
    }
}

/** A thick rounded bar, the same in every card; nothing for [JobBar.None]. */
@Composable
internal fun JobProgress(bar: JobBar, modifier: Modifier = Modifier) {
    val shaped = modifier.fillMaxWidth().height(BAR_HEIGHT)
    val colors = MaterialTheme.colorScheme
    when (bar) {
        JobBar.None -> Unit

        JobBar.Moving -> LinearProgressIndicator(
            modifier = shaped,
            color = colors.primary,
            trackColor = colors.surfaceContainerHighest,
            strokeCap = StrokeCap.Round,
        )

        is JobBar.Filled -> LinearProgressIndicator(
            progress = { bar.fraction.coerceIn(0f, 1f) },
            modifier = shaped,
            color = colors.primary,
            trackColor = colors.surfaceContainerHighest,
            strokeCap = StrokeCap.Round,
        )
    }
}

/** The kind's icon in a circle whose colour says how it is going. Decorative: the pill and the text say it in words. */
@Composable
private fun ToneIcon(@DrawableRes icon: Int, tone: JobTone) {
    val (container, content) = toneColors(tone)
    Box(Modifier.size(ICON_CIRCLE).background(container, CircleShape), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(ICON_SIZE))
    }
}

@Composable
private fun StatusPill(text: String, tone: JobTone) {
    val (container, content) = toneColors(tone)
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = content,
        maxLines = 1,
        modifier = Modifier.background(container, CircleShape).padding(horizontal = PILL_PADDING, vertical = 2.dp),
    )
}

@Composable
private fun toneColors(tone: JobTone): Pair<Color, Color> {
    val colors = MaterialTheme.colorScheme
    return when (tone) {
        JobTone.RUNNING -> colors.primaryContainer to colors.onPrimaryContainer
        JobTone.WAITING, JobTone.STOPPED -> colors.surfaceContainerHighest to colors.onSurfaceVariant
        JobTone.DONE -> colors.primary to colors.onPrimary
        JobTone.FAILED -> colors.errorContainer to colors.onErrorContainer
    }
}

/** The card's quiet buttons: Cancel, Stop, Details, OK. */
@Composable
internal fun JobTextButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier.height(Dimens.TouchTarget)) { Text(label) }
}

/** The card's main button while a job runs (Cancel, Stop) or the one that deletes ([destructive]). */
@Composable
internal fun JobOutlinedButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val tint = if (destructive) colors.error else colors.primary
    OutlinedButton(
        onClick = onClick,
        border = BorderStroke(1.dp, if (destructive) colors.error else colors.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = tint),
        modifier = modifier.height(Dimens.TouchTarget),
    ) { Text(label) }
}

/**
 * A button that deletes something the listener may want ([label]): it asks [question] first, and only its own
 * [label] runs [onConfirm]; Keep and a tap outside do not.
 */
@Composable
internal fun JobConfirmedButton(label: String, question: String, onConfirm: () -> Unit) {
    var asking by rememberSaveable { mutableStateOf(false) }
    JobOutlinedButton(label, { asking = true }, destructive = true)
    if (!asking) return
    AlertDialog(
        onDismissRequest = { asking = false },
        text = { Text(question) },
        confirmButton = {
            TextButton(
                onClick = {
                    asking = false
                    onConfirm()
                },
            ) { Text(label, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = { asking = false }) { Text(stringResource(R.string.confirm_keep)) } },
    )
}

private val CARD_RADIUS = 20.dp
private val BAR_HEIGHT = 8.dp
private val ICON_CIRCLE = 40.dp
private val ICON_SIZE = 22.dp
private val PILL_PADDING = 10.dp
private const val TITLE_LINES = 2
private const val FAILED_BORDER_ALPHA = 0.55f
