// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/kws/VoiceCommand.kt
// Purpose: The spoken commands (plan item 38) and the words that mean each one in the recogniser's text.

package io.github.skm0078.audora.speech.kws

/**
 * Every command starts with the app's name, "ListenLock", so the book being read aloud (which says "pause" or "next"
 * often enough) does not drive the player; a made-up name is rare in any text. [words] are what the phone's
 * recogniser writes for the command, lower case: the word itself, and the near spellings it writes for it in a
 * strong accent or a noisy room. Two words can mean one command ("pause" and "stop"); [CommandPhrases] matches them.
 */
enum class VoiceCommand(vararg val words: String) {
    PAUSE("pause", "paws", "pose", "pours", "stop"),
    PLAY("play", "plays", "played", "resume"),
    NEXT("next", "nest"),
    BACK("back", "go back", "bag"),
    REPEAT("repeat", "again"),
    FASTER("faster", "fast"),
    SLOWER("slower", "slow"),
}
