# Characters heard in a voice of their own gender (2026-10-28, ADR 2026-10-28-character-genders)
- Bengali: a woman in a book read by SYSPIN male borrows the SYSPIN female voice when it is installed (and the
  other way round). Hindi and Bengali: many more names and role words, a guess from the name's ending, and Hindi
  speakers' own verbs ("मैं जाऊँगी") tell their gender.
- All languages: action beats ("Mira smiled.") name the next speaker; an answer by name ("Lucy!") names the lines
  before it; a "she" later on the page reaches earlier lines. The story brain still wins over a name-ending guess.
- Studio audio version 7: made books re-render. "What's new" entry:
  `app/src/main/assets/whatsnew/2026-10-28-character-genders.txt`. `:pdf` unchanged. No new dependency.
- **Measured (held-out, lines heard in the other gender):** EN 33 % to 29 %, HI 53 % to 30 %, BN 30 % to 15 %;
  second held-out HI 60 % to 20 %, BN 33 % to 16 %.
- **Cost:** a partner model loads only in a book whose narrator lacks a gender, when that gender first speaks and the
  partner is installed (about 115 MB RAM for SYSPIN; at most 3 models stay loaded, as before). A few string scans
  per paragraph. APK a few KB (name tables).
- **Could affect:** who reads each line in every book with dialogue; the character guide's genders; made audiobooks
  (re-rendered once); voice loading in Bengali books.
- **Verified in CI:** `SelfGendersTest`, `NameGendersTest`, `SpeakerTagsTest`, `CastSpeakersTest`,
  `CharacterCastTest`, `QuoteCarryTest`, `ReadingSourceBrainTest`, `ExpressiveEvalTest` (wrong-gender counts pinned,
  floors raised), release journey step 29.

**Real-device checks:** an English story with a man and a woman talking: each gets a voice of their gender, not the
narrator's. A Hindi story where a woman says "मैं जाऊँगी": a woman's voice. A Bengali book on SYSPIN male with SYSPIN
female installed: the women's lines in the female voice, the first one after a short load. With Story brain on: the
same, no slower than before.
