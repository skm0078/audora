// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/voices/WithdrawnVoices.kt
// Purpose: Catalog voices taken out of the app, deleted from phones that downloaded them (ADR-074, ADR 2026-10-27).

package io.github.skm0078.audora.speech.voices

/**
 * Voices that left the catalog because their licence forbids commercial use (ADR-074): Piper's two Hindi voices
 * (CC BY-NC-SA 4.0) and Meta's MMS Bengali (CC BY-NC 4.0); and Piper's Google Bengali, fine-tuned from Piper's
 * LibriTTS-R voice, itself fine-tuned from Lessac, whose data may not be redistributed (ADR 2026-10-27). A phone
 * that downloaded one deletes it at the next start ([VoiceFiles.withdraw]); its language then reads with its default
 * until the user picks another voice.
 */
object WithdrawnVoices {
    val IDS: Set<String> = setOf("piper-hi-pratham", "piper-hi-priyamvada", "mms-bn", "piper-bn-google")
}
