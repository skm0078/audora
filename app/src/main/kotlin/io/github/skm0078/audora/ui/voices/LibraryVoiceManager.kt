// File: app/src/main/kotlin/io/github/skm0078/audora/ui/voices/LibraryVoiceManager.kt
// Purpose: Adapts :speech's VoiceLibrary to the Voices screen's VoiceManager port.

package io.github.skm0078.audora.ui.voices

import android.net.Uri
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.speech.voices.VoiceLibrary
import io.github.skm0078.audora.speech.voices.VoiceRow
import kotlinx.coroutines.flow.Flow

class LibraryVoiceManager(private val library: VoiceLibrary) : VoiceManager {
    override fun rows(): Flow<List<VoiceRow>> = library.rows()

    override fun download(id: String) = library.downloads.start(id)

    override fun cancel(id: String) = library.downloads.cancel(id)

    override suspend fun delete(id: String): Outcome<Unit, String> = library.delete(id)

    override fun choose(lang: Lang, voice: VoiceId?) = library.readingVoices.choose(lang, voice)

    override suspend fun import(treeUri: String, lang: Lang, label: String): Outcome<String, String> =
        when (val imported = library.import(Uri.parse(treeUri), lang, label)) {
            is Outcome.Success -> Outcome.Success(imported.value.spec.label)
            is Outcome.Failure -> Outcome.Failure(imported.error.message)
        }
}
