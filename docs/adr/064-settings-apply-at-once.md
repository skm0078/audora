# ADR-064: Studio audio survives app updates; reading settings apply at once

**Status:** Accepted (2026-10-23). Amends ADR-039 (studio mode) and P2-03 (reading text settings). Follows ADR-063.

## Context
The options audit after ADR-063 found three settings that did not do what the listener saw, or did it silently:
- Studio audio was keyed by the app's install time (`lastUpdateTime`), and every folder written before it was deleted
  when the app opened. Every update, a dev build or a Play update, threw away hours of rendering, even when nothing
  about the sound had changed.
- "Skip citation numbers" applied only the next time a book was opened: the playback service kept reading the old
  text, and a tap or seek reopened it in the middle of listening.
- A voice picked in Settings → Voices does nothing for a book that has its own voices ("Use for this book only"),
  and nothing said so.

## Decision
1. `STUDIO_AUDIO_VERSION` (playback/studio/StudioAudioVersion.kt) replaces the install time in `StudioSignature`.
   Bump it when a change can make the same sentence, voice and settings sound different (engine or model upgrade,
   speech text shaping, clip format); AGENTS.md says so next to the PIPELINE_VERSION rule. Folders are named
   `v<version>-<hash>`, so `StudioStore.dropOtherVersions` deletes other versions, and folders from before this ADR,
   by name alone.
2. `ReadingTextPreferences.changes` announces each saved change in the process. The playback service then reopens the
   open book with the new settings (`BookSession.onReadingTextChanged`) and continues from the sentence playing,
   found by its fingerprints (`ReAnchoring.relocate`), since its offsets moved; playing or paused as before. The
   reader reopens its document on the page shown, so both still agree on offsets.
3. When a Settings → Voices pick changes a language the open book reads with its own installed voice, the playback
   service says so in a toast and names the switch that turns the book's own voices off.

## Alternatives considered
- **Key by app version name.** Every release changes it, which is the same loss as the install time.
- **Never invalidate on update.** A real engine change would then play old audio next to new live sentences.
- **Apply the citations setting only from the next page.** The highlight and the text would disagree until then.
- **Let Settings → Voices override a book's own voice.** It would silently undo a choice the listener made per book.

## Consequences
- Forgetting to bump `STUDIO_AUDIO_VERSION` after a sound change plays slightly stale studio audio; the AGENTS.md
  rule and this ADR are the guard. The first install of this build drops studio audio once (unversioned folders).
- Not done: the audiobook export reading ready studio audio. Export reads at the voice's own speed for talking-book
  apps and studio at the listener's, so their audio matches only at 1×; not worth the coupling now.
