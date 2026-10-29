// File: app/src/main/kotlin/io/github/skm0078/audora/ui/theme/NamedButtons.kt
// Purpose: One way to name a button that repeats down a list (Download, Source, Licence) after the item it acts on,
//          so TalkBack users can tell "Download: Bruno" from "Download: Priya".

package io.github.skm0078.audora.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.skm0078.audora.R

/** The modifier that makes TalkBack read [action] with the [item] it acts on; the button still shows only [action]. */
@Composable
fun buttonNamedFor(action: String, item: String): Modifier {
    val name = stringResource(R.string.a11y_named_button, action, item)
    return Modifier.semantics { contentDescription = name }
}
