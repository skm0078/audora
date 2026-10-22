// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/kws/VoiceCommand.kt
// Purpose: The spoken commands (plan item 38) and the keyword spotter's phrases for them.

package io.github.skm0078.audora.speech.kws

/**
 * Every command starts with "Audora", so the book being read aloud (which says "pause" or "next" often enough) does
 * not drive the player; a made-up name is rare in any text. [tokens] are each phrase in the model's BPE pieces,
 * produced once with the model's own `bpe.model` (sentencepiece), so the phone needs no tokenizer. Two phrases can
 * mean one command ("pause" and "stop").
 */
enum class VoiceCommand(vararg val tokens: String) {
    PAUSE("▁A U D OR A ▁PA U SE", "▁A U D OR A ▁ST O P"),
    PLAY("▁A U D OR A ▁PLAY", "▁A U D OR A ▁RE S U ME"),
    NEXT("▁A U D OR A ▁NEXT"),
    BACK("▁A U D OR A ▁BACK", "▁A U D OR A ▁GO ▁BACK"),
    REPEAT("▁A U D OR A ▁RE PE AT"),
    FASTER("▁A U D OR A ▁FA S TER"),
    SLOWER("▁A U D OR A ▁S LO W ER"),
    ;

    companion object {
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
