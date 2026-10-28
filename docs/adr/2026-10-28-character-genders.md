# ADR 2026-10-28-character-genders: every speaking character heard in a voice of their own gender

**Status:** Accepted (2026-10-28). Follows ADR 2026-10-25 emotion and characters (the character cast) and the story
brain. Raised by the maintainer on the dev APK: "gender misrepresentation in the voice of each character is a huge
issue".

## Context

The cast gives a character a voice of their gender from the narrator model's other speakers. When the gender is not
known, the narrator reads the line. That is not neutral: the narrator has a gender of its own (Bruno is a man,
Supertonic's Hindi narrator a woman, SYSPIN Bengali has one voice per model), so about half of those lines are heard
in the wrong gender. On the expressive-eval held-out sets, measured as heard (narrator's gender included), 33 % of
English, 53 % of Hindi and 30 % of Bengali spoken lines came out in the other gender's voice. Causes found:

1. **Bengali SYSPIN has one speaker per model.** A woman in a book read by SYSPIN male had no female voice to get.
2. **Hindi and Bengali names missing** from `NameGenders` (only a few dozen of each), and no fallback.
3. **Hindi speakers tell their own gender** in the verb ("मैं जाऊँगी", "मैं थक गया हूँ"); this was never read.
4. **Speaker tags** took the object as the speaker ("राम से सीता ने कहा"), took the verb of a later clause, and missed
   action beats without a speech verb ("Mira smiled." before a line).
5. **A line answered by name** ("Lucy!") did not tell who spoke the lines before it.
6. **Genders learnt later on the page** (a "she" two paragraphs on) never reached the earlier lines.

## Decision

- **Partner voices.** `SherpaSpeechEngine.castPartners` lists installed models of the narrator's language with a
  different gender (SYSPIN female beside SYSPIN male). `CharacterCast` borrows a missing gender from the first partner
  only, and only for the gender the narrator is not. One-speaker models carry their gender in `CastSpeakers`.
- **Names.** About 60 more Hindi and 60 Bengali names of each gender, plus role words (दरोगा, नौकरानी, জমিদার, ঝি), leaving
  out names used either way (किरण, सुमन, মধু). `NameGenders.likely` then guesses from the ending for Hindi and
  Bengali (-ā or -ī a woman, a consonant a man); never for English.
- **Self genders.** `SelfGenders` reads a Hindi speaker's gender from first-person future forms, participles before
  हूँ, and "मैं … था/थी" in one clause; a speech that says both is unknown.
- **Tags.** Hindi object markers (से, को, का…) are never the subject; the verb is taken from the first clause; an
  action beat of 2 to 5 words names the speaker of the next line when that name's gender is known.
- **Answers by name and late genders.** A vocative answer fills the unplaced lines before it with the name called;
  a final pass fills genders learnt later on the page, else uses the name-ending guess, marked as a guess.
- **The story brain wins over a guess.** `ReadingSource` keeps a rule's speaker only when its gender is known and not
  guessed; otherwise the brain's answer (when it knows the gender) comes first.
- `StudioAudioVersion` 7, so made books re-render with the new voices.

## Alternatives

- **Drop light-voiced male speakers from the cast (Jasper, Hugo, Leo for Bruno):** tried; the maintainer's ear check
  heard all three as men, so reverted.
- **A neutral narrator for unknown genders:** no neutral voice exists in the shipped models.
- **Always load a partner model:** costs RAM in every book; loading only when a woman (or man) first speaks keeps it to
  books that need it.
- **Name-ending guesses for English:** English endings do not mark gender reliably (Kit, Dana); left out.

## Consequences

- Held-out wrong-gender lines (as heard): English 33 % to 29 %, Hindi 53 % to 30 %, Bengali 30 % to 15 %; second
  held-out Hindi 60 % to 20 %, Bengali 33 % to 16 %. Tuned-set scores not lower. CI pins these.
- Cost on the device: no new dependency or asset. A partner model loads beside the narrator only in a book whose
  narrator lacks a gender, when that gender first speaks and the partner is installed (SYSPIN female, about 115 MB
  on disk already, about as much RAM); `maxLoadedVoices` stays 3. The rules add a few string scans per paragraph.
- Left as is: a character with no gender cue anywhere in the text (no pronoun, name, title or verb) is still read
  by the narrator. Bengali verbs carry no gender, so Bengali relies on names, titles and pronouns.
- Tests: `SelfGendersTest`, new cases in `NameGendersTest`, `SpeakerTagsTest`, `CastSpeakersTest`,
  `CharacterCastTest`, `QuoteCarryTest`, `ReadingSourceBrainTest`; `ExpressiveEvalTest` pins wrong-gender counts;
  release journey step 29 renders a story's women and men in voices of their gender on the installed app.
