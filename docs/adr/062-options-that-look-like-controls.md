# ADR-062: Options that look like controls

**Status:** Accepted (2026-10-23). Restyles Settings, the speech sheet, the reader's menus and sheets, the book and
library menus, first run and the empty states; no behaviour or preference changes, no new dependency.

## Context
A first-pass usability fix (2026-10-22) put a one-line hint under every option. They grew: 66 option texts were
over 70 characters, the longest 420. The speech sheet and the reader's overflow menu laid switches, headings and
paragraphs in one flat column, so options did not read as controls ("options do not look like options, essay below
options"). Research into Voice Dream, Speechify, @Voice, ReadEra, NaturalReader and Audible, their Play reviews,
and Android's and Material's own guidance is in `research/06-settings-ux-market.md` in the project files.

## Decision
One set of option rows (`ui/theme/OptionRows.kt`, `OptionDialogs.kt`) used everywhere:

1. **Groups are cards.** A titled card of rows, a step lighter than the screen or sheet behind it.
2. **Every row is a control:** a switch (the whole row toggles), a value at the row's end that opens radio
   choices, a slider or segmented buttons under a title that shows the value ("1.25×", "40%"), or a chevron row that
   opens a page or sheet.
3. **One line under a title, at most** (two when a translation needs them). It states the current value or the
   one fact that decides the choice, never an explanation.
4. **Long help behind an info button.** The existing explanations became its dialog text, so nothing was lost or
   retranslated.
5. **Short first screens.** The speech sheet opens on speed, pace, Read to suit the book and This book only; What
   to read, Sound, Cast voices, Study and Focus sound are one row each that opens its own sheet over it. Settings
   holds Listening, Reading and look, App, and two rows that open Privacy and Advanced as pages (Back returns).
6. **Menus are actions.** The reader's ⋮ menu is five one-line actions with icons; every look option moved to one
   Page and text sheet. The sentence, book and library menus are one line each; book and library actions keep their
   explanation behind an info button.
7. **First run and empty states:** a progress bar over "Step n of 4", "Free and offline" said plainly, and empty
   states with one status line, one action and a one-line how-to.

| Finding (source in the research file) | Change |
|---|---|
| Android settings guidance: frequent settings first; 10-15 items overwhelm; Advanced only for 3+ items with a one-line summary | Settings regrouped; Privacy and Advanced as pages with one-line summaries |
| Material 3 lists: supporting text one to three lines; Android: status under the title, explanations in a footer | One-line subtitles that state the value; help behind ⓘ |
| Hick's law, NN/g progressive disclosure (no more than 2 levels) | Speech sheet's first screen down to 4 controls plus 5 rows, each one level deep |
| Recognition over recall; visibility of system status | Each choice row shows its current value ("Tables: Summary", "Focus sound: Rain") |
| Android: prefer switches; never mix radios with sliders or switches in one row | Checkbox and radio items in the reader menu replaced by switches and a choice dialog |
| Reviews: cluttered menus, options hard to find (@Voice, ReadEra) | Reader ⋮ menu is actions only; look options in one sheet |
| Reviews: paywalls and surprise costs (Speechify, NaturalReader) | First run says "Free and offline: no account, no ads, no tracking" first |
| Reviews: tables, footnotes and equations read wrongly or silently | What to read row names the table and footnote choices on the sheet itself |
| NN/g empty states: status, action, how-to | Library and Notes empty states shortened to that shape |

## Alternatives considered
- **Shorten every hint and keep it inline:** still a paragraph per row in Hindi and Bengali, and the hints would
  grow again with the next complaint about unexplained options.
- **A help screen per section:** a second place to keep in step with the options; the info button keeps the help
  next to the option it explains.
- **Sub-pages inside the speech sheet:** Back would close the whole sheet; a sheet over the sheet keeps Back working.

## Consequences
- New options use `SwitchOption`, `ChoiceOption`, `LinkOption` or `OptionBlock` with a one-line subtitle at most;
  anything longer goes in `help`.
- `ui/settings/SettingsComponents.kt` and the reader's checkbox menu items are gone; `Dimens.MenuMaxWidth` too.
- Strings: new `strings_options.xml` (en, hi, bn); 25 unused hint and label strings removed; six summaries
  shortened in all three languages.
- Not changed: when the background-reading request is asked (still first run step 3); a sample PDF on first run
  needs a bundled file and is left for later.
