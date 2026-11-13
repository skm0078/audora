// File: app/src/main/kotlin/io/github/skm0078/audora/ui/settings/TextSizeItem.kt
// Purpose: Settings' "Text size" row: the reader's text view size, the same control as the reader's Text size sheet,
//          so it is found where people look for it first.

package io.github.skm0078.audora.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.skm0078.audora.R
import io.github.skm0078.audora.ui.reader.ReadingLook
import io.github.skm0078.audora.ui.reader.ReadingLookPreferences
import io.github.skm0078.audora.ui.reader.TextSizeBlock
import kotlinx.coroutines.launch

/**
 * Reads and saves the same reading look as the reader, so a size set here is the reader's next time a book opens, and
 * the other look options are written back as they were. Shown once the saved look is read (DataStore, off the main
 * thread), so the slider never jumps from the default.
 */
@Composable
internal fun TextSizeItem() {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val prefs = remember(context) { ReadingLookPreferences(context) }
    val look by remember(prefs) { prefs.look }.collectAsStateWithLifecycle(null as ReadingLook?)
    look?.let { current ->
        TextSizeBlock(
            current,
            { changed -> scope.launch { prefs.save(changed) } },
            subtitle = stringResource(R.string.text_size_short),
            help = stringResource(R.string.text_size_body),
        )
    }
}
