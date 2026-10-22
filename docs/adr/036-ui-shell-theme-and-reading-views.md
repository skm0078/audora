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
14. **Dark and sepia pages (item 18).** The drawn page keeps its PDFium bitmap; a colour matrix maps its luminance
    onto the tone's ink-to-paper ramp (`PageTone`: Original, Sepia, Dark), so pictures stay legible and nothing is
    re-rendered. On Dark the highlight draws over at 40 % instead of multiplying (multiply vanishes on black). The
    text view takes the same paper and ink. Saved with the reading look.
15. **Pinch zoom and pan (item 19).** `PageZoom` holds 1-4×, zooms about the pinch centre and pans both ways; the
    gesture needs two fingers, so a tap still reads a sentence. The page is re-rendered at width × zoom in 0.5 steps
    after the fingers settle (300 ms), and the last drawn image stays up meanwhile, so zooming never flashes blank.
    *Fit width* resets. The reader model is unchanged: it already renders at the width it is given.
16. **Page scrubber (item 17).** A slider under the page bar; dragging shows the page's picture and number above the
    thumb and jumps on release. Pictures come from `ReaderModel.thumbnail` (app basics' hook, which shares the
    render lock) and are cached per book in memory (48) and in `cacheDir/thumbnails/<book>` as WebP, so Android may
    clear them and a second drag is instant.
17. **Text view look (item 15).** Font (serif or sans), size (85-160 % in 5 % steps), line spacing (compact, normal,
    relaxed) and *Running paragraphs*: sentences flow as paragraphs (split where the pipeline's paragraph pause
    falls), each still tappable and long-pressable and still highlighted, with the spoken word. A sheet from the
    reader's ⋮ menu; saved with the reading look. Bundled fonts stay out (see Alternatives).
18. **Shelves (item 23).** Chips above the library (All, Reading, Not started, Finished, with counts) computed from
    each book's saved progress, so no schema change. Custom shelves need Room and wait.
19. **Quick Settings tile (item 36).** A `TileService` with its own media controller while the shade is open: a tap
    plays or pauses; with nothing loaded it opens Audora continuing the last book, the widget's route, so playback
    still starts from a user action and the playback service is untouched.
20. **Kids mode (item 71).** One switch in Settings → App. The library loses its book menu and Add button, the tabs
    go, books open in a player with three big buttons (back, play or pause, forward), and every route to the page
    view, Notes or Settings lands on the library (`kidsDestination`). Leaving needs the library banner held for 3 s
    (TalkBack's long-press leaves at once). The voice reads at 0.85× or the listener's own speed if slower, through
    the existing speed preference; the old speed is kept in kids mode's own file and given back on leaving. Known
    gaps: a book already playing keeps its speed until reopened, and a book with its own settings keeps its own.
    Separate child profiles need Room and wait.
21. **App language (item 44).** English, हिन्दी, বাংলা or the phone's, in Settings → Appearance. No AppCompat: on
    Android 13+ the choice goes to the system's per-app language (`LocaleManager`), so the notification, widget and
    tile follow it; on Android 12 the activity's context is wrapped, and those three keep the phone's language. The
    choice is a SharedPreferences string, because it must be known synchronously before the first frame. English
    stays the default even on a Hindi or Bengali phone until the default is decided (call 4). Every values file is
    translated (`values-hi`, `values-bn`, in :app and :playback); feature string files that land later get theirs at
    landing.

## Alternatives considered
- **Navigation Compose:** a new dependency for six routes; the saveable route string already survives process
  death. Revisit if deep links (widgets, Android Auto) need it. Its predictive back animation is the same seekable
  transition `AnimatedScreens` uses directly.
- **material3-adaptive** for window size classes and list-detail panes: a new library for three breakpoints that
  `BoxWithConstraints` reads directly.
- **Bundled Noto fonts:** about 1 MB per script for a look the phone already draws.
- **A second copy for duplicates:** see 4.
- **A separate player model:** two sources of truth for the reading position.
- **A widget that binds the media session** for live play and pause: needs a controller connection from a broadcast
  receiver and still shows nothing once the service stops; the saved book plus Resume covers the main use.
- **AppCompat for the app language:** one call on every Android version, but a new dependency for what
  `LocaleManager` already does on 13+ and a wrapped context does on 12.
- **Kids mode through a playback hook** for speed: would touch app basics' settings logic; the speed preference
  already reaches every reader opened afterwards.

## Consequences
- Settings rows go in groups (`SettingsGroup`), so a new setting is one row in its group.
- Library actions lost the mini player and Settings entries (the shell owns them) and gained Add to Up next.
- Wave 3 (items 18, 19, 17, 15, 23, 36, 71, 44) adds no library and no Room change.
- The reader and the full player of a book are composed from one call site, so switching keeps one ReaderModel
  and one open PDF. The player follows the listener's book when Up next moves on. Back returns to where a book was
  opened (the route carries the tab, and "via the player" for Show the page), and the Up next sheet treats the
  playing book as playing, not the book on screen.
- Wave 4 (the UI plan's remaining screens): the mini player opens the full player on a swipe up as well as a tap;
  the Notes tab filters by book; the full player's progress bar marks chapter starts and a tap reads from that
  chapter (not in kids mode); a Details sheet from each book's menu gathers its pages, size, dates and actions;
  first-run screens (languages, one voice per language with size and speed, background reading, the first PDF)
  show once to an empty library, and an update with books marks them done unseen; Settings → Storage lists
  downloaded voices, the OCR and punctuation packs and the page pictures, each freed through its owner's call. The
  render cache and exports join Storage when playback and studio expose their sizes. No bundled sample PDF.
- Platform fit: MainActivity is singleTop and the widget body uses the launcher's intent (no second screen); the
  system bar icons follow the app's Light or Dark choice; the full player scrolls below 420 dp of height
  (landscape, large fonts) instead of squeezing the sentence; menu and speech-sheet switches announce their state.
- No new library. Real-device checks: TalkBack through library, reader and player; 200 % font with Hindi and
  Bengali titles; wallpaper colours off, dark and true black; a duplicate pick; the mini player across tabs.
- Wave 5 (motion and Android 16, targetSdk 36): every screen change animates (`AnimatedScreens`), and a back swipe
  scrubs to the screen it leads to (predictive back through `PredictiveBackHandler` and a seekable transition;
  cancelling springs back). The tabs, and one book's reader and player, are one content (`AppDestination.screen`),
  so they never rebuild, the one-ReaderModel rule holds, and a back swipe inside one content previews nothing. The
  mini player's cover grows into the full player's as a shared element (`sharedCover`; the library grid takes no
  part, since a book can show there twice). Window widths follow Material's size classes without the adaptive
  library (`WindowWidths`, from the window's width so split screen counts): from 600 dp the tabs become a navigation rail; the full player
  splits into book and progress beside sentence and controls when the window is wider than tall; list and settings
  screens, the reader's text and the library list keep a centred 640 dp column. The whole app stays clear of a side
  camera cutout once, at the root, and each screen pads for the system bars. Back is only ever `BackHandler` or
  `PredictiveBackHandler` (search, first-run steps, screens); nothing uses `onBackPressed` or `KEYCODE_BACK`. TalkBack:
  screen titles are headings; a running paragraph reads from its start on a double tap and opens its first
  sentence's menu on a long press; sentences announce "Read from here" and "Options for this sentence"; Storage
  buttons say which row they free; no live region anywhere near the spoken text (the "What did it say?" card lost
  its own, which talked over the replay). The tappable chapter bar is 48 dp tall. `LampContrastTest` holds the lamp
  palette to WCAG AA (4.5:1 text, 3:1 bars) in light, dark and true black. The themed icon already had its
  monochrome layer. First run is decided once per install from the saved flag and the library's first settled
  state, with the shell held until it is known. Voices carries where it was opened from (Settings or Storage), like
  the reader. Real-device checks: a back swipe from the player, reader and Voices, cancelled and finished;
  the mini player to the full player; landscape on the Moto (player panes, rail, cutout side); TalkBack through a
  running paragraph and Storage; 200 % font on first run in Hindi.
  Review of wave 5: the screen-on flag and the volume-key hook are counted per window and dropped as soon as a
  reader starts leaving, so two reader compositions during a transition never fight; the mini player's controller
  connection is hoisted above the transition; back is linear with the leaving screen on top (zIndex); insets
  come from the system bars; the spoken word is read through a lambda so only the sentence recomposes per word;
  the two-pane switch has hysteresis (1.1 on, 0.9 off).
