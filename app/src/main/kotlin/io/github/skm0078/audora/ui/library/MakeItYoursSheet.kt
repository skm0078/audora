// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/MakeItYoursSheet.kt
// Purpose: The "Make it yours" sheet from the library's ⋮ menu: the name the greeting uses, covers or list, and the
//          home's sections on or off and in the listener's order (ADR 2026-10-28-make-it-yours).

package io.github.skm0078.audora.ui.library

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.home.HomeLayout
import io.github.skm0078.audora.core.home.HomeSection
import io.github.skm0078.audora.ui.theme.AppSheet
import io.github.skm0078.audora.ui.theme.Dimens
import io.github.skm0078.audora.ui.theme.SectionTitle

/**
 * Every change is saved as it is made, so the library behind the sheet shows it at once; there is no Save to forget.
 * [view] is the library's own grid or list choice, the same one the sort sheet sets.
 */
@Composable
internal fun MakeItYoursSheet(
    layout: HomeLayout,
    onLayout: (HomeLayout) -> Unit,
    view: LibraryLayoutState,
    onDismiss: () -> Unit,
) {
    AppSheet(onDismissRequest = onDismiss, skipPartiallyExpanded = true) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding)
                .padding(bottom = Dimens.SpaceXl)
                .testTag(MAKE_IT_YOURS_TAG),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
        ) {
            Text(
                stringResource(R.string.library_make_it_yours),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.home_sheet_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NameField(layout, onLayout)
            SectionTitle(stringResource(R.string.home_books_as), Modifier.padding(top = Dimens.SpaceL))
            LayoutChoice((view.layout ?: LibraryLayout.AUTO).showsList(LocalDensity.current.fontScale), view.onLayout)
            SectionTitle(stringResource(R.string.home_sections), Modifier.padding(top = Dimens.SpaceL))
            Column {
                layout.order.forEachIndexed { index, section ->
                    SectionRow(
                        section,
                        layout,
                        onLayout,
                        canMoveUp = index > 0,
                        canMoveDown = index < layout.order.lastIndex,
                    )
                }
                SectionRow(HomeSection.SHELF_CHIPS, layout, onLayout, canMoveUp = false, canMoveDown = false)
                SwitchRow(stringResource(R.string.home_cover_tint), layout.coverTint) {
                    onLayout(layout.copy(coverTint = it))
                }
            }
        }
    }
}

/** Typed text stays here, so the cursor never jumps while the saved name catches up. */
@Composable
private fun NameField(layout: HomeLayout, onLayout: (HomeLayout) -> Unit) {
    var typed by rememberSaveable { mutableStateOf(layout.name) }
    OutlinedTextField(
        value = typed,
        onValueChange = { text ->
            typed = text.take(HomeLayout.NAME_MAX)
            onLayout(layout.named(typed))
        },
        label = { Text(stringResource(R.string.home_name_label)) },
        supportingText = { Text(stringResource(R.string.home_name_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth().padding(top = Dimens.SpaceM),
    )
}

/**
 * The section's name, one move button (up, or down for the top one) and its switch. Chips stay pinned above the
 * books, so their row has no move button; the switches still line up at the end.
 */
@Composable
private fun SectionRow(
    section: HomeSection,
    layout: HomeLayout,
    onLayout: (HomeLayout) -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
) {
    val name = stringResource(sectionLabel(section))
    Row(
        Modifier.fillMaxWidth().heightIn(min = ROW_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
    ) {
        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        when {
            canMoveUp -> MoveButton(R.drawable.ic_expand_less, stringResource(R.string.home_move_up, name)) {
                onLayout(layout.moved(section, up = true))
            }

            canMoveDown -> MoveButton(R.drawable.ic_expand_more, stringResource(R.string.home_move_down, name)) {
                onLayout(layout.moved(section, up = false))
            }

            else -> Spacer(Modifier.size(Dimens.TouchTarget))
        }
        Switch(
            checked = layout.shows(section),
            onCheckedChange = { onLayout(layout.toggled(section)) },
            modifier = Modifier.semantics { contentDescription = name },
        )
    }
}

@Composable
private fun MoveButton(@DrawableRes icon: Int, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(painterResource(icon), contentDescription = label) }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = ROW_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            modifier = Modifier.semantics {
                contentDescription = label
            },
        )
    }
}

@StringRes
internal fun sectionLabel(section: HomeSection): Int = when (section) {
    HomeSection.THIS_WEEK -> R.string.home_section_week
    HomeSection.CONTINUE -> R.string.home_section_continue
    HomeSection.LINE_OF_THE_DAY -> R.string.home_section_line
    HomeSection.RECENTLY_ADDED -> R.string.home_section_recent
    HomeSection.SHELF_CHIPS -> R.string.home_section_chips
}

/** For tests: the sheet's scrolling column. */
const val MAKE_IT_YOURS_TAG = "make_it_yours"

private val ROW_HEIGHT = 56.dp
