// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/FurnitureConfig.kt
// Purpose: Tunables for tagging page furniture: figure and table captions, display equations (RI-7a, ADR-035).

package io.github.skm0078.audora.core.config

/**
 * ADR-035: `:pdf` tags captions and display equations so playback can announce or skip them.
 *
 * @property captionSizeRatio a paragraph opening with a caption label ("Fig. 2", "Table 3", "चित्र 1") is a caption
 *   when the label is followed by a delimiter (". ", ":", "—", "।") or a capital ("Fig. 4 Regions…"), the paragraph
 *   is bold or set as a heading, or its first line is at most this share of the body text size. "Table 2 shows
 *   that…" in body text is none of these.
 * @property equationMaxWordShare a display line is an equation when at most this share of its visible characters
 *   belongs to dictionary words of three or more letters, and it holds a maths symbol or ends in an equation
 *   number ("(12)"). Body text with inline maths ("where φi = 0 for each i") stays well above it.
 * @property numberedEquationMaxWordShare the same bar for a line with a maths symbol that ends in an equation number:
 *   such a line is display maths even with words as variable names ("lrate = d · min(step_num, …) (3)").
 * @property minEquationWordLetters words shorter than this do not count as words for [equationMaxWordShare]:
 *   "dy", "or" and single letters are variables as often as words in maths.
 * @property equationMinIndentEm a line of maths without an equation number is a display equation only when it starts
 *   at least this many body-text sizes right of its block's left edge (centred or indented); a wrapped sentence's
 *   last line ("m − 1.") starts at the edge.
 */
data class FurnitureConfig(
    val captionSizeRatio: Float = 0.95f,
    val equationMaxWordShare: Float = 0.25f,
    val numberedEquationMaxWordShare: Float = 0.6f,
    val minEquationWordLetters: Int = 3,
    val equationMinIndentEm: Float = 2f,
) {
    init {
        require(captionSizeRatio in 0f..1f) { "captionSizeRatio must be in 0..1, was $captionSizeRatio" }
        require(equationMaxWordShare in 0f..1f) { "equationMaxWordShare must be in 0..1, was $equationMaxWordShare" }
        require(numberedEquationMaxWordShare in equationMaxWordShare..1f) {
            "numberedEquationMaxWordShare must be in equationMaxWordShare..1, was $numberedEquationMaxWordShare"
        }
        require(equationMinIndentEm >= 0f) { "equationMinIndentEm must be >= 0, was $equationMinIndentEm" }
        require(minEquationWordLetters >= 1) { "minEquationWordLetters must be >= 1, was $minEquationWordLetters" }
    }
}
