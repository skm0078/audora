# ADR-078: An optional story brain reads dialogue for who speaks and how

**Status:** Accepted (2026-10-25). Extends ADR-056, ADR-058, ADR-063, ADR-075, ADR-076, ADR-077.

## Context
Character voices and feelings come from rules: speaker tags ("said Mira"), turn-taking, name lists and cue words
("whispered", "!"). They are precise where a book spells things out and silent where it does not. On the held-out
passages the rules were never tuned on, the right-gender voice reached 59 % in English, 38 % in Hindi and 0 % in Bengali of lines with speech,
and the rules never hear anger at all (a "!" alone can be joy, anger or fear). Listeners judged emotion and
characters as "barely working" (Q-2, Q-3 in the coverage matrix).

A small language model reads a scene the way a person does. The question was whether one runs on a phone, offline,
with a free licence, fast enough to stay ahead of the listener.

## Decision
1. **The story brain is optional and off until downloaded.** Settings → Story brain downloads Qwen3.5 4B (Qwen team,
   Apache-2.0), Unsloth's Q4_K_M GGUF unchanged, 2.74 GB, on Wi-Fi only. Phones with less than about 6 GB of memory,
   and builds without the library (the x86_64 emulator), never see the row. With no model, reading is exactly as
   before: `StoryBrainReader` answers nothing and every call falls back to the rules.
2. **Mirrored on the app's own release.** The file is pinned to a Hugging Face commit and cut into two parts under
   GitHub's 2 GiB asset limit (`scripts/build_story_brain_mirror.py`, `.github/workflows/story-brain-mirror.yml`,
   which never replaces an asset). The phone checks each part and the joined file against the pins in
   `speech/story-brain.json` (`StoryBrainSpecTest` keeps the app's copy equal and the licence commercial-use).
3. **llama.cpp, built from its pinned source tag** (`LlamaCppSourceTask`, SHA-256 checked) into
   `libaudora_brain.so`, arm64 only, always optimised, 16 KB aligned. Every Android CPU variant of ggml ships and
   the best one these cores can run is loaded at start (dot product, int8 matrix maths, SVE), so an older core never
   meets an instruction it lacks.
4. **One question per page, spoken lines only.** The quote tracking marks which sentences have speech; the brain is
   asked only about those, with the page's narration as context, the last sentences of the page before, and the cast
   met so far. A grammar (GBNF) forces one row per spoken line, in order (`12|Tom|angry`), then a cast line with
   genders, so the answer always parses and always ends. Pages with no speech cost nothing.
5. **Ahead of the listener, behind the voice.** One background thread at low priority with two model threads reads
   the page being extracted and the two after it (not after a scanned page: that would OCR it twice). Notes are kept
   per book on disk (`BrainNotesStore`, named by a hash of the book's id, dropped when the model or prompt version
   changes), so a page is read once. A sentence whose page is not read yet keeps the rules' call.
6. **How the calls combine.** Who speaks: the rules' speaker when they also know its gender (a tag such as "said
   Mira" is rarely wrong), else the brain's, else the rules'. The brain's speaker's gender comes from the name lists
   when they know the name or role (they beat the model on Bengali roles: it called পিসি a man), else from the
   brain's cast line (its first call on a name; padding past the real speakers is dropped). A name the line calls out
   to ("Of us, Felix") is never its speaker. How: a feeling the brain heard wins over the rules' (it read the scene,
   so a "!" becomes angry, scared or happy); when it heard none, the line keeps its own cues but no carried mood.
7. **Anger is a feeling now** (`Feeling.ANGRY`, last in the enum so saved style lists keep their order): louder, a
   touch quicker and lower. Only the brain names it. Rasa's Bengali "angry" style reads it.

## Results (lines with speech; right-gender voice / wrong-gender voice)
Measured with `research/expressive-eval/llm_director.py --brainhost` (the app's prompt, grammar and native code on a
PC, 4 threads) and the rules' calls from `ExpressiveEval`, combined as above.

| Set | Rules alone | With the story brain |
|---|---|---|
| Tuned, English | 85 / 0 | 89 / 8 |
| Tuned, Hindi | 88 / 0 | 100 / 0 |
| Tuned, Bengali | 100 / 0 | 100 / 0 |
| Held-out, English | 59 / 0 | 70 / 25 |
| Held-out, Hindi | 38 / 5 | 88 / 11 |
| Held-out, Bengali | 0 / 0 | 84 / 15 |

Feelings on lines with speech (right / feeling missed / neutral line coloured): tuned, rules 35 / 77 / 0, brain
50 / 10 / 60; held-out, rules 20 / 92 / 0, brain 42 / 5 / 60. Opposite feelings (sad read happy): 0 to 2 lines.

The brain finds far more speakers and hears far more feelings, at a cost: on the held-out sets about one line in
six to one in four with speech gets a wrong-gender voice, mostly where the book never names the speaker (in one English
passage, five of its seven), and it colours more neutral lines than it should. Rules alone give fewer voices
but almost never a wrong one; the listener chooses by downloading.

Speed: on the PC, 3.6 s per sentence of a page (about 90 s for a 25-sentence page, all
sentences in the prompt, spoken ones answered). Not yet measured on the test phone with two threads; a page plays
for two to three minutes, so the brain keeps up if the phone is within about twice the PC's time.

## Alternatives
- **Cloud model:** better and faster, but against the offline-first promise (D1, no-cloud rule).
- **Smaller models (2B and under):** measured on the same set before this change, worse than the rules.
- **Bigger or reasoning models:** do not fit next to the voice in a phone's memory, or think for minutes per page.
- **JSON output:** the earlier measurement's format; several times the tokens of the compact rows for the same
  answer, so too slow on a phone.

## Consequences
- 2.74 GB of storage and more battery while a story plays; both are the listener's choice, and Storage deletes it.
- The model is mapped, not loaded: Android can drop its pages under memory pressure, and the voice comes first.
- Studio mode renders without the brain for now (its renders run without a playing session); a later change can feed
  it the saved notes.
- `PIPELINE_VERSION` is unchanged: `:pdf` is not touched.
