// File: app/src/main/kotlin/io/github/skm0078/audora/ui/settings/LanguageItem.kt
// Purpose: Settings → Appearance → Language (plan item 44): the app's text in English, Hindi, Bengali or the phone's.

package io.github.skm0078.audora.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import io.github.skm0078.audora.R
import io.github.skm0078.audora.language.AppLanguage
import io.github.skm0078.audora.ui.theme.Dimens

/** The row shows the current language; a tap opens the choice. Each language is named in its own script. */
@Composable
internal fun LanguageItem(language: AppLanguage, onChange: (AppLanguage) -> Unit) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    SettingsRow(
        stringResource(R.string.settings_language_title),
        summary = { Text(stringResource(languageName(language))) },
        onClick = { choosing = true },
    )
    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text(stringResource(R.string.settings_language_title)) },
            text = {
                Column(Modifier.selectableGroup()) {
                    AppLanguage.entries.forEach { option ->
                        LanguageOption(option, selected = option == language) {
                            choosing = false
                            if (option != language) onChange(option)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { choosing = false }) { Text(stringResource(R.string.settings_language_cancel)) }
            },
        )
    }
}

@Composable
private fun LanguageOption(option: AppLanguage, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = Dimens.SpaceS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(stringResource(languageName(option)))
    }
}

internal fun languageName(language: AppLanguage): Int = when (language) {
    AppLanguage.ENGLISH -> R.string.language_english
    AppLanguage.HINDI -> R.string.language_hindi
    AppLanguage.BENGALI -> R.string.language_bengali
    AppLanguage.PHONE -> R.string.language_phone
}
