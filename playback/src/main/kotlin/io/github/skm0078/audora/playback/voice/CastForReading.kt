// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/voice/CastForReading.kt
// Purpose: The one way playback and the audiobook export build their character cast (plan item 63).

package io.github.skm0078.audora.playback.voice

import android.content.Context
import io.github.skm0078.audora.core.model.VoiceShaping
import io.github.skm0078.audora.speech.sherpa.castSpeakers

/**
 * The app's character cast, the same for playback and the audiobook export: the other speakers of the narrator's
 * model ([castSpeakers]), never the voice [shaping] gives quotes or headings, kept in the process's
 * [CharacterCasts] so both see and save the same characters.
 */
fun CharacterCast.Companion.forReading(
    context: Context,
    engines: VoiceEngines,
    voices: () -> SpeechVoices,
    shaping: () -> VoiceShaping,
) = CharacterCast(
    narrator = { lang -> voices().knownVoice(lang) },
    speakers = { voice -> engines.bundled().getOrNull()?.engine?.castSpeakers(voice).orEmpty() },
    taken = { lang -> shaping().let { setOfNotNull(it.dialogueVoices[lang], it.headingVoices[lang]) } },
    casts = CharacterCasts.forContext(context),
)
