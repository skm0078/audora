# ADR 2026-10-28-phone-drawer: a side drawer on phones too, and Settings by name

**Status:** Accepted (2026-10-28). Reverses the "no drawer on phones" choice in ADR 2026-10-28-four-places at the
maintainer's request, following the "Side drawer on phones too" boards on the design canvas, which the maintainer
approved.

## Context

On phones the four places sit in a bottom bar, and the tablet sidebar's other rows (shelves, collections) were only
chips over the books. The maintainer asked for the drawer on phones too. Testing the build, they also could not find
Settings: they live on the You place, whose page said "You" and nowhere "Settings".

## Decision

- **A menu button** at the start of the library's top bar opens a modal drawer over the bottom bar and the mini
  player. The bottom bar stays: the drawer holds what the bar does not, so nothing is listed twice.
- **The drawer lists:** the app's name; Shelves (All books, Reading, Not started, Finished, the same four with the
  same counts as the chips); the listener's collections (the tablet sidebar's own list and look); Made by you
  (Audiobooks and clips, which opens Made, and Listening stats); Show me around; Settings.
- **One thing at a time:** every row closes the drawer before it acts, so a sheet (Listening stats) never opens on top
  of it. Listening stats asked for from another place opens once the library shows, through the navigation's pending
  action, the way a found setting does.
- **Opens only from the button.** An edge swipe would fight the sideways chip and cover rows and Android's back
  gesture, so swiping only closes it; Back closes it (with the predictive back preview).
- **A closed drawer is out of reach:** it stays composed just off screen, so its rows leave the semantics tree while
  closed; TalkBack and tests never find a row that cannot be seen.
- **Only with the bottom bar.** The rail (600 dp) and the sidebar (840 dp) keep their forms; kids mode has none.
- **Settings by name:** the You page is headed "Settings", and the drawer has a Settings row. The place keeps its name
  "You" in the bar.

### Side panel, top bar and sheets (2026-10-29)

The maintainer asked for fewer separate menus, with each kind of control in one known place:

- **The side panel** (the drawer on a phone, the sidebar on a big tablet) holds places and the big things a listener
  goes looking for. Under the collections both now share one block of rows (`PanelLinks`): Your library (Make it
  yours, Edit collections, Listening stats), then Show me around and Settings.
- **The top bar** keeps Search, Sort and filter, and the ⋮ menu with all its tools.
- **Bottom sheets** stay for quick changes: sort, filters, a book's menu, Make it yours itself.

A panel row that opens a library sheet asks for it as a `LibraryAction`; the library screen runs it once it shows,
through the same path as its ⋮ menu (`OwnSheets`), so Make it yours and Edit collections open the same sheet from
the menu, the chip, search and the panel.

## Alternatives

- **A gear button in the library's top bar:** with the menu button, search, sort and ⋮ the title would keep about
  70 dp on a 360 dp phone, so the greeting would be cut. The drawer row and the page heading give Settings a name
  without crowding the bar; flagged to the maintainer.
- **Repeat the four places in the drawer** (as the tablet sidebar does): they are one tap away in the bar already.
- **Help and tips row** (in the mockup): the app has no help page yet; Show me around and each option's ⓘ cover it.
- **Edge swipe to open:** see above.
- **Fold the ⋮ menu and the sheets into the drawer:** proposed, then turned down by the maintainer: the top bar is
  where search, filter and ⋮ are expected, and sheets suit quick changes.

## Consequences

- Cost on the device: no new dependency; six small vector icons (Material Icons, Apache-2.0). The drawer is composed off screen while closed (a
  dozen and a half rows, laid out once), the drawer's counts reuse the chips' count, worked out once per library change. APK
  about 6 KB (estimate). On a rail or sidebar the empty drawer frame stays in place (no rows, nothing drawn), so the
  screen under it keeps its state when the width changes, such as a phone turned sideways.
- Strings: new `strings_phone_drawer.xml` in English, Hindi and Bengali.
- Tests: `PhoneDrawerTest` on the emulator (rows, close-then-act, a collection, hidden when closed, none on a wide
  window or in kids mode, state kept across a width change); `SettingsHomeTest` (the drawer's Settings opens
  Settings' own page, not Privacy or Advanced left open, and a found setting left mid-flash is cleared rather than
  reopening You on Advanced every time after, a bug the drawer's journey step found); release journey steps 45 (menu button, Settings) and 46 (Make it yours from the drawer); `AppShellTest` (the
  sidebar's shared rows); `MakeItYoursScreenTest` (a request from the panel opens Make it yours or the collections,
  once).
