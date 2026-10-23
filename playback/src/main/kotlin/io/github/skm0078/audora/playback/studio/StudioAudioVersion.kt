// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/studio/StudioAudioVersion.kt
// Purpose: Version of studio audio's sound; saved audio is kept across app updates until this changes (ADR-064).

package io.github.skm0078.audora.playback.studio

/**
 * Bump whenever a change can make the same sentence, voice and settings sound different (AGENTS.md): a speech engine
 * or voice model upgrade, how text is shaped for speech (numbers, pauses, prosody, character voices), or what a clip
 * holds. Studio audio of another version is never played and is deleted when the app opens. Until 064 the key held
 * the app's install time instead, so every update threw away hours of rendering.
 * 1 = ADR-064: first explicit version.
 */
const val STUDIO_AUDIO_VERSION: Int = 1
