# ADR-070: Small touches testers notice: covers outside the app, shortcuts, What's new

**Status:** Accepted (2026-10-25).

## Context
Friends testing the dev APK kept finding small gaps that the listening apps they know (Pocket Casts, Audible, Voice
Dream, Play Books) do not have. An audit of dev against those apps listed twelve; the listener picked nine. Each is
small and cheap on a low-end phone, but a few choices are worth recording.

## Decision
- **Cover, author and chapter on the notification, lock screen, car screen and watch.** The session's items carry
  the cover as an `artworkUri` (a `file://` address of the cover the library already drew, with the file's time as
  a query so a new cover is a new address), not as bytes: the playlist crosses to controllers, and a picture in every
  item would make that large. Media3 loads it once with its own bitmap loader for the notification and the platform
  session. Where the cover lives moved from the app to `:playback` (`CoverFolders`), so the service and the widget
  find the same file without drawing one. The second line reads "Author · Chapter 2 of 9 · Page 14 of 300".
  Android Auto's browse rows still have no pictures (they would need a content provider).
- **App icon shortcuts** are dynamic, not declared in XML, so their labels follow the language picked in Settings.
  Resume reuses the widget's Continue intent; Add a book and Read copied text are intents MainActivity handles once.
  Reading the clipboard waits for the window's focus, which Android requires.
- **What's new** is a string array with a number in code (`WHATS_NEW_ID`). Dev builds all share one version name,
  so the list cannot be keyed to the version. Someone with books who has not seen the current number sees it once;
  a new listener (empty library, or just through the first-run screens) is marked as having seen it.
- **Mark as finished / not started** use the resume anchor alone: finished is the start of the last page (what the
  Finished shelf already means), not started deletes the anchor (`ResumeAnchorStore.clear`). The save time is kept,
  so marking does not reorder "Continue reading".
- **Try a sample** is a short page about the app, written for it in English, Hindi and Bengali (so it can ship), and
  saved like shared text.
- **Haptics** use Compose's `LocalHapticFeedback`, which follows the phone's own setting: long-press on a page or a
  paragraph, each speed step, a bookmark, setting the sleep timer.

## Consequences
- No schema change (one DAO delete), no `:pdf` change (PIPELINE_VERSION 12), no new dependency.
- A PR that adds something a listener would notice replaces `whats_new_items` (en, hi, bn) and bumps `WHATS_NEW_ID`.
- Not done: time left on the widget (the library has no per-book reading-time estimate yet), pictures in Android
  Auto's browse list, notification buttons for bookmark and sleep, undo after removing a book, and resuming when
  headphones connect (Android 12+ limits starting playback from the background).
