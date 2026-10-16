# ADR-030: Hindi and Bengali voices (provisional defaults), credits in the catalog, "Voices & licences" screen

**Status:** Accepted (P3-06, 2026-10-16). The two default picks are provisional until the maintainer's S0 listening
notes and the phone run are in, the same way ADR-012 keeps the English default open.

## Context
Spec §3 leaves the Hindi and Bengali neural defaults to S0 + S1 and asks for an in-app "Voices & licences" screen
for the CC BY-SA, BY-NC-SA and BY-NC voices. ADR-012 narrowed the candidates to Piper priyamvada and pratham, plus
Supertonic 3 (Hindi), and Piper bn_BD google and MMS-TTS ben (Bengali), all download-only. The S0 notes
(`research/notes/S0-listening.md`) and the phone table do not exist yet, and the maintainer asked to keep building
rather than wait on checks only they can run. Two practical gaps: sherpa-onnx publishes no archive for the Bengali
voices (S1 converted them in CI from Hugging Face, which the agent workspace cannot reach), and P4-02 could not fetch
Supertonic's OpenRAIL-M text (also on Hugging Face).

## Decision
1. **Four voices join the catalog** (`speech/models.json`): Piper hi_IN **pratham** and **priyamvada** (CC BY-NC-SA
   4.0), Piper bn_BD **google** speaker 0 (CC BY-SA 4.0 + CMU Indic notice) and **MMS-TTS ben** (CC BY-NC 4.0).
   Supertonic 3 Hindi stays the premium Hindi voice from P4-02.
2. **Provisional defaults by the ticket's own criteria** (S1 desktop: intelligibility by ASR round trip, speed,
   licence): Hindi **pratham** (CER 3.5 %, every text scored; priyamvada 4.6 % with one text unscorable; both RTF
   ~0.07), Bengali **Piper google** (RTF 0.068, 22 kHz; MMS is 5× slower and 16 kHz, kept as the fallback the spec
   lists). They are a top-level `"recommended": {"hi": …, "bn": …}` map in `models.json`: changing a default is one
   line. Speaker 0 of the 16 Bengali speakers is the one S1 benchmarked; a different speaker is a `speakerId` change
   with the same zip.
3. **What "default" means for a download-only voice:** the Voices screen marks the recommended voice and lists it
   first, and the **first voice downloaded for a language that reads with the phone's voice (Hindi, Bengali) becomes
   that language's reader** if the user has not picked one. English keeps the bundled voice. Downloading a second
   voice never changes an existing choice. Choosing "Phone's voice" is stored as "no choice", so a later first
   download takes over; accepted, since the user just downloaded a voice for that language.
4. **Bengali sources are our own pinned zips.** `build_voice_mirror.py` accepts `source.url` pointing at a `.zip`
   (same top-folder layout as sherpa-onnx's tarballs) on this repo's `s1-voices` release, SHA-256 pinned like every
   other source. The weights are exactly S1's conversions.
5. **Credits live with each voice** in `models.json` and `bundled-voices.json`: `credit` (who made it, the data,
   what Audora changed), `licenceUrl`, `sourceUrl`. The build validates them (https links, every entry) and packs
   them into the APK's catalog assets; the app never takes credits from downloaded files.
6. **Settings → "Voices & licences"** lists every voice the APK can use (built in, downloadable whether installed or
   not, imported), each with licence, credit, links to the licence and source, and for an installed voice its own
   `LICENSE` / `MODEL_CARD` / `README.md` files; then the phone's voice and the speech/OCR software (sherpa-onnx,
   ONNX Runtime, eSpeak NG, Tesseract, Audora itself).

## Alternatives considered
- **Wait for S0 before shipping any Hindi/Bengali voice:** blocks the ticket on a manual check for no gain; the
  catalog already offers every candidate, so the listening test only moves one line.
- **Ship only the recommended voice per language:** smaller list, but the listening test then needs a new build to
  try the others. Rejected; each extra entry costs nothing until downloaded.
- **Auto-download the recommended voice on first Hindi/Bengali book:** spends 64–78 MB of data without asking.
  Rejected; downloads stay a user action.
- **Credits in a separate licences file:** a second list to keep in sync with the catalog. Rejected.
- **Fetch Supertonic's model licence at mirror-build time in CI:** needs a pin computed from bytes the workspace
  cannot see, and turns CI into a way around the workspace's network policy. Not done; see consequences.

## Consequences
- Hindi and Bengali voices download once this is on `dev` and the voice-mirror workflow has published the new zips
  (64, 64, 78 and 114 MB).
- The S0/phone listening comparison is added to the batched phone checks; its outcome edits `recommended` (and maybe
  the Bengali `speakerId`), plus this ADR's status.
- **Open:** Supertonic 3's OpenRAIL-M text is still not inside its zip. The licences screen links to it and states
  the use restrictions; adding the file needs a copy of `LICENSE` from
  huggingface.co/supertone-oss-archive/supertonic-3 committed under `speech/` and a new Supertonic pin.
- MMS may measure too slow on the Moto (S1 predicted RTF 0.8–1.1); P4-02's too-slow label covers it.
