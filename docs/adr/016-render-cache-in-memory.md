# ADR-016: Rendered speech is cached in memory (8 MiB), not on disk (20 MB)

**Status:** Accepted (P1-04, 2026-10-15).

## Context
Build Spec §6 has the render-ahead queue write rendered sentences to a bounded ~20 MB LRU in `cacheDir/tts/`.
P1-04 renders each sentence once into PCM and hands it to `TtsDataSource`; the only reuse is skipping back a
few sentences, or a rebuild (seek, voice change, stuck-player recovery) landing on a sentence just played.

## Decision
Keep the PCM of sentences that left the render window in an in-memory LRU bounded in bytes
(`QueueConfig.recentAudioBytes`, default 8 MiB, about three minutes of 22 kHz speech). Nothing rendered from a
user's book is written to disk. A rebuild with a new voice starts a new queue, so stale-voice audio is never reused.

## Alternatives considered
- **Disk LRU in `cacheDir/tts/` (the spec):** survives a process death, but resume (P1-06) restarts at the sentence
  anyway, and rendering one sentence is faster than real time. It adds file I/O on the render path, cleanup and
  eviction code, and leaves the spoken text of a user's PDF on disk.
- **No cache:** skip-back would re-render every sentence; with Kokoro (RTF near 1 on the desktop) that is a
  noticeable wait.

## Consequences
- About 8 MiB more heap while reading; tunable in `QueueConfig`.
- Skip-back beyond the cache (or after the process dies) renders again.
- If long skip-back or a premium voice slower than real time makes re-rendering painful, a disk tier can be added
  behind `RecentAudio` without changing the queue's contract.
