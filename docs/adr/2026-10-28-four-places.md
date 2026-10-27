# ADR 2026-10-28-four-places: Library, Notes, Made and You on every screen size

**Status:** Accepted (2026-10-28). Second step of the design pass the maintainer asked for before v0.1.0 (rules and
feature map in "ListenLock: where every feature lives", mockups on the design canvas). Extends ADR-036 (tabs and
mini player) and ADR-068 (Made).

## Context

The maintainer found features hard to find: the Jobs screen sat behind an hourglass in the library's top bar,
everything the app made was two screens behind it, and Settings was a tab named for its contents rather than for the
person. Tablets got the phone layout with a rail. The ask: one clear split between the navigation and the top bar,
tablet support, and nothing a listener can miss.

## Decision

- **Four places**, the same names, icons and order everywhere: **Library, Notes, Made, You**. The navigation moves
  between places only; actions and settings never go there.
- **Made** is a place, not a page behind Jobs. Its first row is "Jobs and downloads" with what runs now; Jobs opens
  from there and Back returns to Made. The library's Jobs button stays only as a shortcut while something runs.
- **You** is the old Settings tab under the listener's name for it. Its route stays `settings`, so older saved
  states still open it; the stats card and the replayable tour join it in a later step.
- **Form by window width** (Material's size classes, no new library): a bottom bar under 600 dp, a rail from 600 dp,
  and from 840 dp a **permanent 280 dp sidebar** with the app's name, the places and "Your collections" with their
  book counts. Tapping a collection opens the library narrowed to it; tapping Library shows the whole library again.
- Kids mode keeps the library only, with no navigation at all.

## Alternatives

- **A phone drawer:** hides the places behind a gesture or button; the maintainer found too many drawers already.
- **Five tabs (a Jobs tab):** Jobs is empty most of the time; it belongs beside what it makes.
- **Material's adaptive navigation library:** a new dependency for what three `when` branches do.

## Consequences

- No new dependency; the APK grows by two vector icons. One fewer screen in the Jobs-to-Made path.
- Strings: `tab_settings`, `settings_title`, `made_title`, `made_open_all` and `jobs_open` are gone; `tab_made`,
  `tab_you`, `sidebar_collections` and the Made Jobs row's strings are new, in English, Hindi and Bengali.
- Tests: `PlacesFormTest` (breakpoints), `AppShellTest` (each form, the same places in the same order, the sidebar's
  collections, screenshots), `AppDestinationTest`, `LibraryShelvesTest`, `MadeAndEditScreensTest` (the Jobs row),
  the UI tour (Made, then Jobs from it) and release journey step 19.
