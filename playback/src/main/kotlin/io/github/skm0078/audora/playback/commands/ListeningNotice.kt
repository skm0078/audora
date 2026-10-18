// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/commands/ListeningNotice.kt
// Purpose: Why screen-off listening ended, when the user should be told (plan item 38).

package io.github.skm0078.audora.playback.commands

/** [VoiceCommandService] posts a notification for each but [NONE], saying why and how to start again. */
enum class ListeningNotice { NONE, MICROPHONE_LOST, IDLE }
