// File: core/src/main/kotlin/io/github/skm0078/audora/core/model/Lang.kt
// Purpose: The languages Audora reads aloud (D1); picks the voice and sentence rules for a ReadingUnit.

package io.github.skm0078.audora.core.model

enum class Lang(val bcp47Tag: String) {
    EN("en"),
    HI("hi"),
    BN("bn"),
}
