# Dyslexia-friendly fonts in the text view (2026-10-26, CF-5)
- Page and text → Font now offers four choices: Book (serif), Clean (sans), **Easy letters (Atkinson Hyperlegible)**
  and **Dyslexia-friendly (OpenDyslexic)**. The row's help says what each new one does and that both change English
  text only.
- Both fonts are bundled in `app/src/main/res/font/` (regular and bold each, about 470 KB together; OpenDyslexic is
  most of it) under the SIL Open Font License 1.1. Their licence texts ship in `assets/licences/`, and Voices &
  licences gains a Fonts section crediting both with a link to their source. Atkinson Hyperlegible 1.006 is from
  google/fonts; OpenDyslexic is the compiled release from its author's repository.
- Hindi and Bengali: neither font has Devanagari or Bengali letters, so Android draws those with its Noto faces in
  every choice, exactly as before. Real bold files back "Bold word starts" and the bold highlight.
- The choice is saved by name with the rest of the reading look; an older build that reads a name it does not know
  falls back to Book (serif).
- "What's new" entry: `app/src/main/assets/whatsnew/2026-10-26-dyslexia-fonts.txt`. No `:pdf` change. No new
  dependency (font files only).
- **Could affect:** the text view and Focus view type, bold word starts, the Font choice dialog, Voices & licences.
- **Verified in CI:** `ReadingFontsTest` (a typeface per choice; serif and sans unchanged; both bundled fonts carry
  regular and bold; names unique), `DyslexiaFontsTest` (each bundled font measures differently from serif, so it
  really loaded; Hindi and Bengali draw in every choice; screenshot `text-view-fonts.png` in the "player-screens"
  artifact), `ReleaseJourneyTest`.

**Real-device checks (the test phone):** open an English book's text view, Page and text → Font → Easy letters, then
Dyslexia-friendly: the text changes at once, and "Bold word starts" still bolds. Open a Hindi and a Bengali book with
each font: the text looks as before. Settings → Voices & licences ends with a Fonts section.
