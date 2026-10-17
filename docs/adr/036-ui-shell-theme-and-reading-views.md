# ADR-036: The app shell, theme and reading views (first UI pass)

**Status:** Accepted (2026-10-17). Wave 1b of the premium plan: the shell plus plan items 25, 27, 39 and 77; wave 2
adds items 13, 5, 14 and 35 (§10-13).

## Context
Every feature so far added its own screen piece: the library grew a mini player under its list and a settings icon,
the reader a row of icons, Settings a flat list. The premium plan asks for a paid-app feel and for the next waves
(widgets, Android Auto, studio mode) to have a place to land. The mockups for this pass were ten phone screens in a
warm "reading lamp" look; this ADR records what was built from them and why.

## Decision
1. **Three tabs.** Library, Notes and Settings in a bottom navigation bar (`AppShell`). The mini player sits above
   the bar on every tab, so play and pause are always under the thumb; tapping it opens the full player. The reader,
   the player and Settings' sub-pages are drawn over the tabs. Navigation stays the hand-rolled saveable route
   (`AppDestination`, now with `Notes`, `Player(id)` and a `tab` per screen): still flat, so no navigation library.
2. **Theme tokens.** `AudoraTheme(appearance)` takes wallpaper colours by default (every phone from minSdk 31 has
   them) and Audora's own lamp palette when they are off (`LampLightColors`, `LampDarkColors`: paper #F8F2E8, ink
   #24190F, lamp #A8520E; text on the accent meets 4.5:1). Dark mode follows the system or is forced light or dark;
   "True black at night" turns dark surfaces black for OLED screens. Spacing, touch targets and cover sizes are
   named in `Dimens`. Text uses the phone's own fonts (Noto, with Devanagari and Bengali faces on every Android
   phone), so the APK gains no font files; book text uses the serif face with taller lines so Indic marks never
   clip. The choices are saved in a small DataStore file (`AppearancePreferences`), apart from the reader's.
3. **Library as a cover grid.** Adaptive columns of covers (`BookTile`) with title, status ("New", "42 % read",
   "Finished", or the missing-file warning) and the book menu; Continue reading spans the top. At 150 % font scale
   and above the grid becomes the list (`BookRow`), because two lines of title no longer fit under a cover. Search,
   sort, filter and the covers themselves are the convenience pack's, restyled only.
4. **Duplicate check (item 27).** A picked PDF whose fingerprint is already in the library gets a dialog ("You
   already have this PDF") with *Open it*, instead of a snackbar. There is no "add a second copy": the fingerprint
   keys the book's resume position, bookmarks and per-book settings, so two copies would fight over one place. A
   PDF shared from another app opens by itself, so its duplicate keeps the snackbar. The add result now carries the
   book id for this (`LibraryMessage.AlreadyInLibrary`/`FoundAgain`).
5. **Full player.** The reader's model in a second layout (`PlayerScreen`): the spoken sentence large between its
   faded neighbours, the cover, chapter list, Up next, undo jump, progress with time left, and the same controls
   panel as the reader. One model means the two never disagree on where reading is. An error falls back to the
   reader, which explains it.
6. **Focus view and bold word starts (item 77).** In the text view, Focus view fades every sentence but the one
   being read (30 % opacity) in larger reading type; bold word starts bolds the first half of each Latin-script
   word. Hindi and Bengali words stay whole, because bolding half a word there splits a conjunct. Both are per
   listener, saved apart (`ReadingLookPreferences`), and reached from the reader's overflow menu.
7. **Up next (item 25).** A sheet from the reader and the player over `UpNextPort` (owned by playback): queued books
   with reorder and remove, add from the library or from a book's menu, and the "Keep going to the next chapter" and
   "Then play the queue" switches.
8. **Notes tab.** Every book's bookmarks and notes (item 29), grouped by book; a tap opens the book at the bookmark.
   Built from one bookmark query per book until the store has a cross-book query.
9. **Accessibility (item 39).** Every icon button has a label, tiles read as one item ("title, status") with the menu
   as its own button, switches toggle with their whole row, headings are marked, and controls keep 48 dp targets.
10. **Follow-along (item 13).** While reading, the text view scrolls the spoken sentence to the middle of the screen
    (a far one is jumped to, a near one glides). *Follow along* off leaves the text where the listener put it. The
    spoken sentence is marked by a tinted band, an underline or bold type; all three are per listener, in the
    reading look.
11. **What did it say? (item 5).** In the full player. The sentence just heard (the one before the spoken one, or
    the spoken one at a page's top) plays again at 0.85× the listener's speed, snapped to a slider step, and its
    text shows in a card TalkBack announces; reading then carries on into the next sentence. Built on the reader's
    own actions (speed, read from a sentence), so playback is untouched. The speed comes back when the player moves
    past the sentence, when the replay is cancelled by a jump elsewhere, or when the player closes, and is left
    alone if the listener changed it meanwhile. Known gap: the slower speed is saved like any speed change, so if
    Android kills Audora mid-replay it comes back slower; a speed change that is not saved needs a playback hook.
12. **Word highlight, version one (item 14).** Playback publishes each sentence's timing (`SentenceTiming`: cue,
    speech and trailing silence, in the clip's time) and the controller's position in the clip. The reader polls
    that position once a frame while the voice plays, turns it into a share of the speech (`speechFraction`; nothing
    during a cue, and only for the sentence the timing names), and spreads that share evenly over the sentence's
    non-space characters (`spokenWord`), about ±150 ms off where the voice pauses at commas. Words are what spaces
    separate, so a Hindi or Bengali word is never split inside a conjunct. The reader and the full player both mark
    the word; *Highlight each word* turns it off. Version two would use per-clause timings or the voice's own word
    timings where an engine reports them.
13. **Home-screen widget (item 35).** A plain `AppWidgetProvider` with RemoteViews (Glance would be a new library):
    the book read last, page progress, and Resume. Resume opens MainActivity with a CONTINUE action and the book id,
    which runs the library's Continue (open the reader, start reading): a tap is the user's own action, so the
    "playback starts only from a user action" invariant holds, and the playback service is untouched. The app
    writes the widget's book to a small preferences file and redraws it only when it changes (SharedPreferences,
    read synchronously inside the widget's broadcast); `updatePeriodMillis` is 0, so the widget never wakes the
    phone. Known gap: progress made with the app closed (screen-off listening) shows when the app next opens.

## Alternatives considered
- **Navigation Compose:** a new dependency for six routes; the saveable route string already survives process
  death. Revisit if deep links (widgets, Android Auto) need it.
- **Bundled Noto fonts:** about 1 MB per script for a look the phone already draws.
- **A second copy for duplicates:** see 4.
- **A separate player model:** two sources of truth for the reading position.
- **A widget that binds the media session** for live play and pause: needs a controller connection from a broadcast
  receiver and still shows nothing once the service stops; the saved book plus Resume covers the main use.

## Consequences
- Settings rows go in groups (`SettingsGroup`), so a new setting is one row in its group.
- Library actions lost the mini player and Settings entries (the shell owns them) and gained Add to Up next.
- No new library. Real-device checks: TalkBack through library, reader and player; 200 % font with Hindi and
  Bengali titles; wallpaper colours off, dark and true black; a duplicate pick; the mini player across tabs.
