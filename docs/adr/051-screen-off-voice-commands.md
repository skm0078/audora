# ADR-051: Screen-off voice commands through a microphone foreground service

**Status:** Accepted (2026-10-18). Plan item 38. Records the decision 63c9058 shipped without an ADR, as revised by
its review.

## Context
Voice commands ("Audora, pause") listen in the reader's own process while the reader is on screen; Android needs no
foreground service for that. Listening with the screen off is an opt-in: Android lets a backgrounded app read the
microphone only from a foreground service of type `microphone`, and since Android 14 only one started while the app
is visible. Media3's playback service cannot be that service: its type is `mediaPlayback`, it leaves the foreground
whenever reading pauses, and "Audora, play" must still work then.

## Decision
1. **A second, minimal service.** `playback/commands/VoiceCommandService` (`foregroundServiceType="microphone"`,
   not exported) only holds the foreground and its notification; the listener stays in the reader, so commands act
   on the reader's player directly. RECORD_AUDIO and FOREGROUND_SERVICE_MICROPHONE are declared in :playback's
   manifest beside it (the app's identical RECORD_AUDIO line merges), so lint checks the pair.
2. **Sessions belong to the visible reader.** A session starts each time the reader is shown (the only time Android
   allows the start) and ends when Stop is tapped or the notification swiped away, when the listener stops, when the
   reader closes, or on the idle rule below. While one runs, the reader keeps its MediaController connected even
   when hidden; otherwise the controller is released at onStop as before. After a session ends with the reader on
   screen, listening goes on on screen only.
3. **Never stopService.** Android 14+ kills the app when a service started with `startForegroundService` is stopped
   before its `onStartCommand` ran. Every later command (end, silenced) is an intent sent with `startService`, which
   is delivered after the start; `stopService` is used only when Android refuses that intent, when no start can be
   pending.
4. **The idle rule is a battery choice.** A session ends once reading has stayed paused for `screenOffIdleMs`
   (10 minutes) and the reader is not on screen; the clock starts at the pause. It is not an Android limit: while
   this service runs the process holds the background-start capability, so "Audora, play" would still work. It
   matches Media3's foreground timeout after a pause (its default and maximum), after which the playback notification
   goes too. A notification says listening stopped, and why, when it ends with the reader hidden for this reason or
   because the microphone failed.
5. **Cheap and visible.** Quiet chunks are not decoded (an energy gate with pre-roll and hangover), a capture
   Android silences (a call, the microphone toggle) is detected and shown in the notification, each command plays a
   short tone, and a speed changed by voice is not saved. Screen-off listening needs notifications (asked when it
   is turned on), since its notification is where Stop lives; without them it falls back to on-screen listening.
6. **Sharing the microphone.** Other features take the microphone through `VoiceCommandPause.holding(reason) {}`; a
   listener claims the microphone atomically before opening it, so a hold never meets a microphone opened behind
   its back.

## Alternatives considered
- **Listening inside the playback service:** its foreground ends at every pause, and its type would need
  `microphone` too, which Android would demand on every play, including with commands off.
- **A wake word with no service, screen off:** Android 9+ silences a background app's capture; not possible.
- **Keeping the session for as long as the reader is open:** costs battery for hours after a pause, the common
  "fell asleep" case.

## Consequences
- A second ongoing notification while a session runs, beside the playback one (both low importance).
- The reader's controller stays bound to the playback service while a session runs with the screen off.
- On-device checks: the 60-minute screen-off run (battery and that commands still act), Stop and swipe, a call
  during listening, and the energy gate's level in a quiet and a noisy room (phone-session checklist K15).
