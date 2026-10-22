// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/NarratorBlendState.kt
// Purpose: What the speech sheet's narrator blend (plan item 75) can do for the voice narrating now.

package io.github.skm0078.audora.ui.reader

/**
 * What the narrator blend can do now: [ownSpeaker] the narrator's own Kokoro English speaker (null: another voice
 * narrates, and the section is not shown); [neededBytes] what the last blend that could not be built needed free.
 */
data class NarratorBlendState(val ownSpeaker: Int? = null, val neededBytes: Long? = null)
