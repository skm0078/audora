// File: app/src/main/kotlin/io/github/skm0078/audora/ui/licences/LibraryLicenceSource.kt
// Purpose: Adapts :speech's VoiceLibrary to the licences screen's LicenceSource port.

package io.github.skm0078.audora.ui.licences

import io.github.skm0078.audora.speech.voices.LicenceText
import io.github.skm0078.audora.speech.voices.LicenceTexts
import io.github.skm0078.audora.speech.voices.VoiceLibrary
import io.github.skm0078.audora.speech.voices.VoiceLicence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LibraryLicenceSource(private val library: VoiceLibrary) : LicenceSource {
    override suspend fun licences(): List<VoiceLicence> = library.licences()

    /** An installed voice's folder; a voice that is not installed has no folder, so no files. */
    override suspend fun licenceTexts(id: String): List<LicenceText> =
        withContext(Dispatchers.IO) { LicenceTexts.read(library.voiceDir(id)) }
}
