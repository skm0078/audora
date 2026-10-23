// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/kws/VoiceCommand.kt
// Purpose: The spoken commands (plan item 38) and the keyword spotter's phrases for them.

package io.github.skm0078.audora.speech.kws

/**
 * Every command starts with the app's name, "ListenLock", so the book being read aloud (which says "pause" or "next"
 * often enough) does not drive the player; a made-up name is rare in any text. The name is spotted as one word and
 * as two ("listen lock"), since people say it both ways. [commandTokens] are each command word in the model's BPE
 * pieces and [tokens] the full phrases, produced once with the model's own `bpe.model` (sentencepiece), so the phone
 * needs no tokenizer. Two phrases can mean one command ("pause" and "stop").
 */
enum class VoiceCommand(private vararg val commandTokens: String) {
    PAUSE("▁PA U SE", "▁ST O P"),
    PLAY("▁PLAY", "▁RE S U ME"),
    NEXT("▁NEXT"),
    BACK("▁BACK", "▁GO ▁BACK"),
    REPEAT("▁RE PE AT"),
    FASTER("▁FA S TER"),
    SLOWER("▁S LO W ER"),
    ;

    /** Every phrase for this command: each spelling of the app's name followed by each command word. */
    val tokens: List<String> get() = WAKE_TOKENS.flatMap { wake -> commandTokens.map { "$wake $it" } }

    companion object {
        /** "ListenLock" as one word and as "listen lock", in the model's pieces. */
        val WAKE_TOKENS = listOf("▁LI S T EN LO CK", "▁LI S T EN ▁LO CK")

        /**
         * The keywords file: one phrase per line, tagged with its command's name (`@PAUSE`), which is what the
         * spotter reports back when it hears the phrase.
         */
        fun keywordsFile(): String = entries.flatMap { command ->
            command.tokens.map { phrase -> "$phrase $TAG${command.name}" }
        }.joinToString("\n", postfix = "\n")

        /** The command a spotter result names; null for an empty result or a tag this build doesn't know. */
        fun of(keyword: String): VoiceCommand? {
            val name = keyword.trim().removePrefix(TAG)
            return entries.firstOrNull { it.name == name }
        }

        private const val TAG = "@"
    }
}
