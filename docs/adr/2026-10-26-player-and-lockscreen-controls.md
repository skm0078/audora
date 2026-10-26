# ADR 2026-10-26-player-and-lockscreen-controls: controls a listener reaches without opening the app

**Status:** Accepted (2026-10-26). Tickets CF-1, CF-2, CF-4 and CF-7 of the competitor gap list.

## Context
Testers use the lock screen more than the app. Until now the notification and lock screen showed Media3's default
previous / play / next, and "previous" restarts the sentence once it is a few seconds in, which on a lock screen reads
as a button that did nothing. Bookmarking needed the reader's long-press or a headset double press. Pulling the
headphones out paused reading (Media3's "becoming noisy"), and putting them back did nothing. The home-screen widget
showed pages, not the time left, which is what a listener plans by.

## Decision
1. **Lock screen and notification buttons (CF-1).** The session's media button preferences are three custom commands
   (`LockScreenCommands`): previous sentence (back slot), next sentence (forward slot) and bookmark (overflow), with
   Media3's own play/pause in the middle. Previous always goes to the previous sentence (`seekToPreviousMediaItem`),
   never back to the start of this one. Only Audora's own controllers get the commands; System UI's taps reach them
   through Audora's media notification controller, as Media3 routes them. The bookmark uses the headset bookmark's
   anchor (no text stored), buzzes, and shows filled while the bookmarked sentence plays.
2. **One-tap bookmark on the player (CF-2).** A bookmark button at the end of the title row saves the sentence being
   read with no note and shows a notice with Add note and Undo, for 6 s or the time the listener's accessibility
   settings ask for. A sentence that already has a bookmark shows the button filled; a tap then opens its note
   instead of saving a twin. Not on the controls row: it is full (speed, back, play, forward, sleep) and the UI tour
   fails squeezed rows.
3. **Resume when headphones reconnect (CF-4), opt-in.** A Settings switch, off by default. Reading resumes only when
   the last pause was Android's own disconnect pause, nothing played since, and a listening output (wired, Bluetooth
   A2DP or LE, USB, hearing aid; not the phone's speaker or a call headset) came back within 30 minutes; at most once
   per disconnect. Build Spec C7 says playback starts only from a user action: here the listener asked for it in
   Settings and then put the headphones back on, which we count as that action. Media3 keeps a paused service in the
   foreground for 10 minutes; after that Android 12+ refuses to start it from the background unless the app may run
   unrestricted, so past 10 minutes it resumes only with Background reading allowed, and otherwise logs why and stays
   paused.
4. **Widget time left (CF-7).** The reader's own estimate (`TimeLeft`, the player's "About 2 h left" line) goes to the
   widget on every pause and every 5 minutes while reading. With the screen off the reader is not running, so the
   playback service reports each span of time the voice was heard (at each pause and every 5 minutes) in a broadcast
   to Audora's own package, received by a non-exported receiver; the widget subtracts the part of each span after the
   estimate. The estimate is in heard time already, so a minute heard is a minute less. A speed change mid-way is
   corrected the next time the reader is open.

## Alternatives considered
- **Media3's default previous / next on the lock screen:** restarts the sentence instead of going back one; rejected.
- **Bookmark in the controls row:** six controls do not fit a narrow phone at large font sizes; rejected.
- **Resume on reconnect always on:** reading that starts by itself must be asked for (C7); rejected.
- **The service computing time left itself:** it would need every page's text to estimate pages not read yet, which
  only the reader keeps; counting down the reader's estimate needs nothing new.
- **A widget timer (updatePeriodMillis or alarms):** wakes the phone for nothing; the service's reports cost one
  broadcast every 5 minutes only while reading.

## Consequences
- `SessionCallback` grants own controllers three more custom commands; other apps and Android Auto see no change.
- `AGENTS.md`'s "playback starts only from a user action" now names the opt-in reconnect resume.
- New preference files: `player_controls` (the switch) and `widget_time_left` (the widget's estimate). Neither holds
  any of the book's text.
