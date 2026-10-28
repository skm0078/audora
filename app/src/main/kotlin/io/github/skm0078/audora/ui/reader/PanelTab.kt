// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/PanelTab.kt
// Purpose: Which tabs the reader's side panel offers for a book (ADR 2026-10-28-tablet-panes).

package io.github.skm0078.audora.ui.reader

/** The panel's tabs, in the mockup's order. */
internal enum class PanelTab { CONTENTS, NOTES, PEOPLE }

/** Contents only for a book with chapters, People only where the guide is offered; Notes always. */
internal fun panelTabs(hasChapters: Boolean, hasPeople: Boolean): List<PanelTab> = buildList {
    if (hasChapters) add(PanelTab.CONTENTS)
    add(PanelTab.NOTES)
    if (hasPeople) add(PanelTab.PEOPLE)
}
