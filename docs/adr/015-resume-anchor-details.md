# ADR-015: Resume anchor details: tail fingerprint, no voice, fixed fingerprint hash, brand onboarding as text

**Status:** Accepted (P1-06, 2026-10-15). Refines ADR-009; no change of scope.

## Context
ADR-009 and spec §4/§6 fix the resume key (page + `charStart` + sentence fingerprint + `pipelineVersion`) and the
re-anchoring order (exact → unit containing `charStart` verified by fingerprint → nearest fingerprint on the page).
Building it raised four questions the spec leaves open: what the fingerprint hashes, what happens when the start of
the saved sentence changes, whether the anchor carries the voice (the §4 sample has a `voice` field), and how the
"brand battery onboarding" opens each maker's settings.

## Decision
1. **Fingerprint = FNV-1a 64 over UTF-8** of the first (head) or last (tail) 48 *normalized* characters: NFKC,
   lower-case, keep only letters, digits and combining marks (Devanagari/Bengali vowel signs are marks). Spaces,
   hyphens, punctuation, soft hyphens and zero-width characters are dropped, so P2's de-hyphenation, ligature
   expansion and whitespace fixes keep a sentence's fingerprint. The algorithm is pinned by a unit test: anchors
   outlive app versions. It hashes the extracted sentence, not the speech-normalized one (P2-04 must keep it).
2. **A second, tail fingerprint.** A running header glued to a page's first sentence (removed in P2-03) or a
   different sentence split changes how a sentence *starts* but not how it *ends*. Order:
   exact (same pipeline, same offset, same head) → offset verified by head → nearest head match → nearest tail
   match → the unit at or before the saved offset (unverified; at worst re-reads a little) → top of the page. Every
   resume logs which rule matched (`Audora.Resume … match=…`), so a wrong resume in a shared log is explainable.
   Fingerprints are checked even on the same pipeline: P1-01 keeps a book whose file was overwritten in place.
3. **No voice in the anchor.** Voices become per-language settings (P3-01); a per-book voice saved at every
   sentence would fight that setting. The anchor stores only where, never how.
4. **Storage:** table `resume_anchors` in `audora.db` (version 2), one row per book, foreign key to `books` with
   `ON DELETE CASCADE`, added by Room's `AutoMigration(1, 2)` generated from the committed schemas. Writes are
   throttled to one per 2 s (`ResumeConfig`), the newest sentence written when the interval ends and at once on
   pause/stop; a kill loses at most 2 s, i.e. resumes at most one sentence early.
5. **Brand onboarding uses only public intents:** the system "ignore battery optimization" prompt
   (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, declared in the manifest) and the app info page. Each maker's own steps
   (Motorola, OnePlus, Samsung, Xiaomi, OPPO, realme, vivo, generic) are shown as text. Brand-specific settings
   activities (`com.miui.securitycenter/...`, `com.coloros.safecenter/...`) are private, change between OS versions
   and crash when missing, so they are not opened directly.

## Alternatives considered
- **Head fingerprint only (spec as written):** loses the sentence whenever its start changes; the unverified offset
  fallback then guesses. The tail costs one column.
- **Proportional offset fallback** (old offset / old page length × new length): needs the page length saved with
  every anchor and is still a guess; the unit at the saved offset is as good in practice.
- **Opening OEM settings activities by component name** (as some reader apps do): faster for the user when it works,
  a crash or a wrong screen when it doesn't. Text steps plus app info work on every OS version.

## Consequences
- P2-04 must keep the extracted sentence text available for fingerprinting when `ReadingUnit.text` becomes the
  speech-normalized form (`TODO(P2-04)` in `SentenceFingerprint`).
- Changing the fingerprint algorithm later makes every saved anchor fall back to offsets once; avoid it.
- Removing a book deletes its position; adding the same PDF again starts from page 1.
