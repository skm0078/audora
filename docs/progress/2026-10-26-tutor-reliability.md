# Read aloud to me keeps up with slow readers; marked experimental (2026-10-26, ADR 2026-10-26-tutor-reliability)
- Root cause of the flaky tutor (Q-5): a reader pausing after every word or two got stuck for good after one word
  the recogniser missed; short stretches were taken as re-reading; a stretch after a skipped line was too. Fixed in
  `ReadAlong` (carry unexplained words, longer re-reads only, resync ahead) and `WordAligner` (resync, page end).
- Tutor log, counts only: one line per stretch and a run summary (level, quiet share, clipping, decode speed, queue
  waits, model load time, place). `TutorRunStats`, `TutorSession`, the microphone's open line.
- Labelled experimental: menu, dialog title, and a note in the dialog (en, hi, bn in `strings_tutor.xml`).
- "What's new" entry: `app/src/main/assets/whatsnew/2026-10-26-tutor-reliability.txt`. `:pdf` not changed. No new
  dependency.
- **Could affect:** the tutor only (`core/tutor`, `playback/tutor`, the tutor dialog). Voice notes share the
  microphone gate, which is unchanged.
- **Verified in CI:** `ReadAlongSimulationTest` (simulated readers finish their pages), `ReadAlongTest` (carry,
  short stretches, skipped line, page end, re-read), `TutorRunStatsTest`, `TutorSessionTest`, `TutorModelTest`.

**Real-device checks (the test phone):** the phone checklist's T1 to T3 and T6, once reading at a normal pace and
once pausing after every word or two; then Settings, Share logs, and look for the "Run summary" lines.
