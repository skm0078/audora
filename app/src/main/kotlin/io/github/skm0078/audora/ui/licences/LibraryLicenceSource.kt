// File: app/src/main/kotlin/io/github/skm0078/audora/ui/licences/LibraryLicenceSource.kt
// Purpose: Adapts :speech's VoiceLibrary and the APK's licence folder to the licences screen's LicenceSource port.

package io.github.skm0078.audora.ui.licences

import android.content.res.AssetManager
import io.github.skm0078.audora.speech.voices.LicenceText
import io.github.skm0078.audora.speech.voices.LicenceTexts
import io.github.skm0078.audora.speech.voices.VoiceLibrary
import io.github.skm0078.audora.speech.voices.VoiceLicence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LibraryLicenceSource(private val library: VoiceLibrary, private val assets: AssetManager) : LicenceSource {
    override suspend fun licences(): List<VoiceLicence> = library.licences()

    /** An installed voice's folder; a voice that is not installed has no folder, so no files. */
    override suspend fun licenceTexts(id: String): List<LicenceText> =
        withContext(Dispatchers.IO) { LicenceTexts.read(library.voiceDir(id)) }

    override suspend fun noticeTexts(): List<LicenceText> = withContext(Dispatchers.IO) { AppNotices.read(assets) }
}
