// File: core/src/main/kotlin/io/github/skm0078/audora/core/sync/SyncText.kt
// Purpose: Escaping for the sync files' tab-separated lines: tabs, line breaks and backslashes in text.

package io.github.skm0078.audora.core.sync

/** [unescape] undoes [escape] for any text; a lone backslash at the end is kept as it is. */
object SyncText {
    fun escape(text: String): String = buildString(text.length) {
        text.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '\t' -> append("\\t")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> append(char)
            }
        }
    }

    fun unescape(text: String): String {
        if ('\\' !in text) return text
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val char = text[i]
            if (char == '\\' && i + 1 < text.length) {
                out.append(
                    when (text[i + 1]) {
                        't' -> '\t'
                        'n' -> '\n'
                        'r' -> '\r'
                        else -> text[i + 1]
                    },
                )
                i += 2
            } else {
                out.append(char)
                i++
            }
        }
        return out.toString()
    }
}
