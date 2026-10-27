# Optional story brain: who speaks, and how (2026-10-25, ADR 2026-10-25-story-brain)
- **Settings → Story brain** (6 GB phones and up) downloads Qwen3.5 4B (Apache-2.0, 2.7 GB, Wi-Fi only) from the
  app's own release in two checked parts. It reads each page's dialogue a page or two ahead of the listener, on the
  phone, and keeps its notes per book. Without it nothing changes.
- **New dependency, approved:** llama.cpp `b11887`, built from source for arm64 (`libaudora_brain.so`, every ggml
  CPU variant, the best one chosen at start). Jobs, Storage and Licences list the model.
- **Anger** is a feeling (`Feeling.ANGRY`), only from the brain. A brain feeling wins over the rules' cue words; the
  rules' tagged speakers with a known gender win over the brain's.
- Held-out right-gender voice: English 59 to 70 %, Hindi 38 to 88 %, Bengali 0 to 84 %, with 25 / 11 / 15 % wrong
  (see the ADR). Feelings heard on speech lines: 20 to 42 % right, missed 92 to 5 %.
- "What's new" entry: `app/src/main/assets/whatsnew/2026-10-25-story-brain.txt`. No `:pdf` change.
- **Could affect:** character voices and feelings for every voice (only when the brain is installed and character voices or
  "Suit the book" is on), Settings, Jobs, Storage, Licences, book opening (the brain reads ahead on its own thread).
- **Verified in CI:** `StoryBrainSpecTest` (pins match, no NC licence), `StoryBrainPromptTest`, `StoryBrainAnswerTest`,
  `StoryBrainDownloadJobTest` (parts resume, join, verify, delete wins), `BrainNotesStoreTest`, `StoryBrainReaderTest`
  (spoken lines only, reads ahead, reuses notes, no model = no notes), `ReadingSourceBrainTest` (no brain = the
  rules' calls exactly), `DeliveryPlannerTest` (brain feelings, no brain = as before).

**Real-device checks (the test phone):** download the story brain on Wi-Fi; play an English, a Hindi and a Bengali
story with character voices on: the log line "Story brain page N: ... ms" shows each page well under the time it
plays, the voices follow the speakers, and angry lines sound angry. Battery over 30 minutes of listening.
