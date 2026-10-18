# ADR-048: Quiz me, offline fill-in-the-blank from highlights

**Status:** Accepted (2026-10-18). Plan item 70, offline half; the online half (smarter questions) waits on ADR-037.

## Context
Readest and Speechify offer flashcards, but neither speaks them, and both build them online. Audora already keeps
the listener's highlights (item 30, ADR-041), which are exactly the lines they chose to remember. The quiz must work
offline in English, Hindi and Bengali, cost nothing to download, and need no new library (the language-model route is
a user call, ADR-037).

## Decision
1. **Cards come from highlights.** Each highlight becomes one fill-in-the-blank card (`QuizCards`, `:core`). The blank
   is the highlight's rarest word across all highlights (ties: the most letters, vowel signs and viramas not counted,
   then the first), at least 4 characters; words are runs of letters, combining marks and zero-width joiners, so
   Hindi and Bengali vowel signs and joined forms (র‍্যা, khanda-ta written ত্‍) stay in their word, and are compared in
   NFC and lower case, so two Unicode spellings are one word. A token mixing scripts (usually an OCR misread) is never
   the blank. Every occurrence of the answer is blanked. A long highlight is cut at spaces to 280 characters around
   the blank, or between whole characters (never inside a conjunct) when there is no space. Counts and word pools are
   built once per quiz; only the cards asked are made, off the main thread.
2. **Choices are the listener's own words.** Up to three distractors come from other highlights in the same script,
   never a word of the card itself, nearest in letters to the answer and cased like it, shuffled with the highlight id
   as seed (the same highlights always give the same card). With too few, the card is answered by *Show answer* and *I knew it* / *I didn't*.
3. **Spaced review.** Leitner-style intervals (`QuizSchedule`): right answers in a row wait 1 day, 3 days, a week,
   3 weeks, then 60 days; a wrong answer starts over and comes back after 10 minutes. A quiz asks due cards (most
   overdue first), then new ones in reading order, up to 10; *Practise anyway* when nothing is due asks the cards
   coming back soonest, and a right answer there does not push a card further out (a wrong one still starts it over).
4. **Progress is a small file, not a Room table.** `files/quiz-progress.txt` (`QuizProgressStore`): highlight id,
   streak, due time, written by a synced temp file and a rename, one writer at a time so the latest save is the one
   kept, and listed in backups. A file in another format is set aside, not overwritten. Cards of deleted highlights drop out at the next
   save. A file keeps the v4 schema (and its committed JSON, which threads cannot regenerate without an Android build)
   unchanged.
5. **Read aloud with the listener's voices.** `QuizSpeaker` renders the question, with "blank" spoken in the card's
   language, through the same `EngineRenderer`, voices and style as playback, and plays it with short audio focus,
   so a playing book pauses and goes on after; the player's share of the speed (ADR-033) and the expressiveness
   setting apply. One line at a time: a new line stops or drops the one before, nothing plays after the quiz closes,
   and an audio error is logged, not a crash. The answer word is read after each verdict; *Hear again* repeats the
   question, or the whole line once answered. Its voices are a second copy of the playback service's (60-150 MB for a
   neural model), freed after a minute without a line and reloaded on the next.
6. **Entry:** *Quiz me* in the library's ⋮ menu, over every book's highlights. Logs carry counts and ids only, never
   highlighted words.

## Alternatives considered
- **Cards written by a language model:** better questions, but needs a new library or the online route. Deferred to
  ADR-037 (the online half of item 70).
- **Word-frequency lists to pick the blank:** the lists live in `:pdf`'s resources for the speakability gate; reusing
  them from `:core` would cross module lines. Rarity within the listener's own highlights is a good enough proxy and
  needs no data.
- **Asking on the lock screen:** needs the playback service's media session (app basics). Handed off for landing.

## Consequences
- A listener with few highlights gets few cards; a highlight of short words only gives none.
- Distractors in a mixed-language library come only from the same script, so a lone Bengali highlight is answered
  by revealing it.
- Turning the phone mid-quiz starts a new quiz; every answer was already saved.
