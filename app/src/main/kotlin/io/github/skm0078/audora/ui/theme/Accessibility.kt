// File: app/src/main/kotlin/io/github/skm0078/audora/ui/theme/Accessibility.kt
// Purpose: The two TalkBack fixes every screen shares: a slider that says what it sets, and a bottom sheet whose
//          handle has a name and closes it (CF-9).

package io.github.skm0078.audora.ui.theme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.github.skm0078.audora.R
import kotlinx.coroutines.launch

/**
 * A slider TalkBack reads as "[name], [value], slider" ("Speed, 1.25×"), not as a bare percentage of its track.
 * Material's slider is 44 dp tall whatever it is given; Compose widens its touch area to 48 dp (see the TalkBack ADR).
 */
fun Modifier.namedSlider(name: String, value: String): Modifier = semantics {
    contentDescription = name
    stateDescription = value
}

/**
 * Material's bottom sheet with a handle TalkBack can use. Material's own handle sits inside a tooltip whose long press
 * is a control with no name ("double-tap and hold to show tooltip"), so every sheet uses this one instead: it looks
 * the same, reads "Close sheet, button" and closes the sheet as a swipe down would.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    skipPartiallyExpanded: Boolean = false,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = skipPartiallyExpanded)
    val scope = rememberCoroutineScope()
    // A tap on the handle always closes: a sheet whose content just grew re-settles to its new height, which can
    // interrupt the hide, and that must not leave the sheet up after the listener asked it to go.
    val close: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismissRequest() }
    }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        containerColor = containerColor,
        dragHandle = null,
    ) {
        SheetHandle(close)
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetHandle(onClose: () -> Unit) {
    val name = stringResource(R.string.a11y_sheet_close)
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.TouchTarget)
            .clickable(onClickLabel = name, role = Role.Button, onClick = onClose)
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        BottomSheetDefaults.DragHandle(Modifier.clearAndSetSemantics {})
    }
}
