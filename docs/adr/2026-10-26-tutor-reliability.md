# ADR 2026-10-26-tutor-reliability: the tutor keeps up with slow readers, logs counts, and is marked experimental

**Status:** Accepted (2026-10-26). Amends ADR-050 (how heard stretches are lined up with the page).

## Context
"Read aloud to me" was reported as very flaky on the test phone (competitor coverage, Q-5). The words heard are never
logged (ADR-050), so the phone logs could not say why. A simulated reader (`ReadAlongSimulationTest`: a page of the
UDHR, seeded readers who pause after 1 to 10 words, skip one line, and a recogniser that mishears or drops words)
reproduced it without a phone:

| Reader (recogniser right 85% of words) | Pages finished before | After |
|---|---|---|
| Pauses after every 1 to 3 words (a child; the Hindi model's short phrases) | 0 of 200 | 198 of 200 |
| Pauses after every 1 to 2 words, recogniser right 75% | 0 of 200 | 185 of 200 |
| Pauses after up to 10 words, recogniser always right | 172 of 200 | 200 of 200 |

Three causes, all in how one stretch (speech up to a pause) is lined up from the reader's place:
1. **One word can never pay for a missed word.** Passing over a page word costs more than one unmatched heard word,
   so once the recogniser missed a word, a reader pausing after every word left every later word unmatched: the
   place never moved again.
2. **Short stretches looked like re-reading.** One or two common words ("of the") fit somewhere among the words just
   read almost always, so they were taken as the reader going back, and changed nothing.
3. **A stretch after a skipped line fit nowhere well**, and "nowhere well behind" still beat "nowhere well ahead".

## Decision
- Heard words a final stretch leaves unexplained are **carried** into the next stretch (at most `carryWords`, 12), so
  the next few words together pay for the gap.
- A stretch counts as **going back** only with at least `minRepeatWords` (3) words of its own, at least
  `repeatMatchShare` (half) of them matching the words just read.
- A final stretch of at least `resyncWords` (4) words that mostly matches further on **moves the place there** for one
  skip's cost, the words between marked skipped (`WordAligner.resync`).
- The page's last word heard last finishes the page past a word or so the recogniser missed.
- **Logging (counts only, never words):** one line per finished stretch (words, moved / held / re-read, place) and a
  run summary: microphone level and peak (dBFS), share of quiet audio, clipped samples, decoding speed against real
  time, times the decoder fell behind, model load time, and the place reached. A shared log can now tell a quiet
  microphone, a slow phone, a recogniser that hears nothing, and alignment trouble apart.
- The feature is labelled **experimental** (menu, title, and a line in the dialog) until 20 phone reads (adult and
  child, the three languages) meet the phone checklist's pass line.

## Alternatives considered
- **Log the heard words** (what Q-5 first asked): fastest to read, but it breaks ADR-050's promise that the reader's
  speech is never stored. Counts and levels separate the likely causes; a words log stays off the table.
- **Lower the skip cost** so one word can pay for a skip: then every misheard word turns into a skip plus a read,
  and the "to check" list fills with false skips. The carry keeps the costs and lets more evidence decide.
- **Change the English model** (a 20M LibriSpeech model, weak on Indian-accented English): may be part of it, but it
  needs a new mirrored download and ear testing on the phone. The run summary's level and stretch counts will show
  whether recognition itself is the problem before that is spent.

## Consequences
- A word misread at the end of a stretch is now marked "to check" once the next stretch explains it (it was marked
  skipped before).
- A jump ahead needs four or more mostly matching words, so a lone word repeated from further down the page never
  moves the place.
- Each final stretch of four or more words that fits poorly ahead tries up to `lookaheadWords` starts; each is the
  same small table as before, well under a frame on the phone.
