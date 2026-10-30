# ADR 2026-10-30-bengali-spoken-spelling: Bengali words spelled as said, a slower pace and calmer Rasa

**Status:** Accepted (2026-10-30). Builds on ADR-053 (SYSPIN) and ADR-057 (Rasa). It comes from the maintainer, a
native Bengali speaker, listening round by round to a Bengali children's storybook read by the app's voices.
Bengali stories ship as beta in v0.1.0 (the maintainer's call); this is the work to make them as good as these voices
allow.

## Context

All three Bengali voices of the Play build, SYSPIN male and female (ADR-053) and Rasa (ADR-057), are VITS models
whose tokens are Bengali letters. They have no espeak-ng data and no lexicon, so each learned to pronounce from
spelling alone, and Bengali spelling leaves out two things a reader supplies by knowing the word:

- **Whether the last consonant keeps its vowel.** "সব" is "shob" and "চোর" is "chor", but "কাণ্ড" is "kando" and
  "করল" is "korlo". The listener heard "sobo", "choro" and "kand".
- **Whether "এ" (or "ে") is "e" or the wide "æ".** "এমন" is "æmon" and "গেল" is "gælo"; the voices said "emon"
  and "gelo".

The listener also found:

- **The pace too fast for a story.** Of 80 % and 90 % of the app's speed, 90 % was right (round 2).
- **Rasa's female voice shrill, its pitch and pace jumping about.** The jumps come from VITS's sampling noise
  (`noise_scale`, `noise_w`). The expressiveness steps gave Rasa the same values as a Piper voice (0.667 and 0.8 at
  the middle step), yet Rasa already carries a sentence's feeling in its speaking style (ADR-057), so the noise only
  added random swings. At 0.35 and 0.4 both Rasa voices were steady, and the listener called the male "almost
  realistic" (round 4).
- **A word said wrongly that the spelling did not cause:** "sukh sasti" for সুখশান্তি came from the page's OCR
  ("সুখশাস্তি"), which is OCR work, not this change.

Round 4 tried the respellings by hand (অ্যামন, চোর্, কাণ্ডো, দ্যাখা, গ্যালো) at 90 % with Rasa calmer, and the
listener found all four voices better; the one miss left was "সব", still "sobo", as it had not been respelled. Round 5
is this change's own output, every word respelled by rule.

## Decision

1. **Respell Bengali words as said** (`BengaliSpelling`, with its word lists in `BengaliSpellingWords`). Each run of
   Bengali letters and marks is one word, matched in NFC (so "ড়" typed as one character or two finds the same
   entry). In order:
   1. a word listed with its spoken form takes it: 59 spoken ends (কত → কতো), 33 wide "e" words (এমন → অ্যামন্),
      138 silent ends in ল, ব or ত (সব → সব্). A word listed to stay as written is left alone: 20 bare verb stems
      ("কর" is "kor" and "koro"), 6 words with two readings (আসল is "ashol", real, and "ashlo", came) and 5 English
      loans;
   2. a past, future or habitual form of one of 138 known verb stems is spoken to its end (করল → করলো, বলছিল →
      বলছিলো, sadhu করিল → করিলো);
   3. a cluster at the end keeps its vowel (গল্প → গল্পো), except য-ফলা (সত্য) and the 24 clusters English loans end
      in (লাস্ট, পার্ক), which these voices already end well;
   4. one consonant at the end is silent where Bengali is regular (চোর → চোর্). A final ল, ব or ত is left to the
      lists, as both endings are common; হ, ছ, visarga and anusvara have small rules of their own.

   Anything else stays as written, so a word the rules do not know reads as it did. A word only gains a mark (a
   hasanta or "ো"), or takes a listed spelling with the same consonants (a wide "e" adds "্য"), so words keep their
   number and order and the word highlight needs nothing.
2. **Only where it is needed.** `SherpaSpeechEngine` respells through `LetterVoices`, after the text is shaped for
   speech and just before the end-of-sentence handling (`StyledText`, `ClosingMarks`) and the model. A voice reads
   letters when it is VITS with no `dataDir` and no lexicon (`SherpaVoiceSpec.readsLetters`), and only Bengali ones
   are respelled. A Bengali voice with espeak-ng data (an imported Piper voice), every English and Hindi voice and the
   phone's own engine get their text as before. What the reader shows, searches and highlights is untouched.
3. **A storyteller's pace.** A Bengali letter voice speaks at `SpeechConfig.bengaliPace` (0.9) of the asked rate,
   inside the engine. The speed control works over it as before, and the measured voice speed sees the slower pace as
   the voice's own, so time estimates stay right.
4. **Calmer noise for a model with speaking styles.** Rasa takes `VoiceShapingConfig.styledNoiseScales` and
   `styledNoiseWidths` (0.2 to 0.55 and 0.25 to 0.6; 0.35 and 0.4 at the middle step) instead of the plain steps
   (0.33 to 0.9 and 0.4 to 1.05). The storyteller end stays below the plain middle. SYSPIN and Piper keep the plain
   steps.
5. **Studio audio version 9**, because SYSPIN and Rasa now sound different.
6. **Guards.** `BengaliSpellingTest` (each rule, the words the listener heard wrong, what must stay as written, NFC,
   joiners, respelling twice, listed words keep their consonants, no word both respelled and kept),
   `LetterVoicesTest`, `SherpaSpeechEngineTest` (what a letter voice is handed and at what rate; Piper Bengali and
   English unchanged), `ExpressionNoiseTest`, `VoiceShapingConfigTest`, `SpeechConfigTest`, and release journey step
   64, which respells on the phone's own Unicode library (Android's NFC is ICU, not the JVM's).

## Performance cost

- Respelling: one pattern pass and a map lookup per Bengali word, 0.37 µs a word on a desktop JVM (about a
  microsecond on a phone): under half a millisecond for a 400-word page, next to seconds of synthesis. The lists are
  built once, on the first Bengali sentence. Other voices pay nothing.
- Memory and size: about 400 short strings, a few KB of APK and well under 100 KB of memory.
- Pace: Bengali audio from these voices is about 11 % longer (1 / 0.9), so a Bengali book takes that much longer to
  hear, render and keep as studio audio.
- Calmer noise: none (the same model, other numbers).
- Studio audio version 9: saved studio audio of every language is deleted once when the updated app opens, as with
  every version bump (ADR-064), and is made again by studio mode.

## Alternatives considered

- **A Bengali phonemizer** (espeak-ng's Bengali, or a G2P model). These voices were trained on letters and cannot
  take phonemes without retraining.
- **Respell in the text normalizers.** Every Bengali voice and the phone's engine would get marks they do not need;
  at the engine only the voices that need it see them.
- **A pronouncing dictionary.** It covers only the words it lists, needs a licence check and a download, and still
  needs rules for the rest. Rules and short lists cover the common words now; a licensed lexicon can feed the lists
  later.
- **Retrain the voices on respelled text.** Better in the end, but it needs training data, GPU time and licence work;
  a server voice is planned for Pro, and the offline app keeps improving on its own (it never gets worse to sell
  Pro).
- **A catalog key per voice** (say `respell=bn` in `models.json`). Every Bengali letter voice needs this, and "VITS
  with no data dir" says so exactly; a key is one more thing to forget for the next voice.
- **Slow Bengali with the speed control.** The speed is the listener's choice for every voice; the pace fixes the
  voices that were recorded fast.
- **Calmer noise for every VITS voice.** SYSPIN and Piper sound flat at Rasa's values, so they keep their steps.

## Consequences

- Only SYSPIN male and female and Rasa sound different; every other voice renders byte for byte as before. `:pdf` is
  unchanged, so `PIPELINE_VERSION` is too.
- On the six storybook pages used for listening, 446 of 1,182 different words change (38 %; 37 % of all words read):
  358 gain a hasanta, 72 a "ো", and 16 take a wide "e" spelling.
- The lists grow by ear. A wrong entry makes one word sound wrong in these voices only.
- **Open risk:** how a hasanta on nearly every closed word sounds. Round 5 asks the maintainer whether it sounds
  natural or clipped; if clipped, rule 4 narrows to listed words.

## Word list

The source of truth is `BengaliSpellingWords.kt`; this is a copy made from it at this change, for a native speaker
to check by eye. Each pair is the word as printed and as the voice is handed it.

**Spoken to the end (59).**

কত → কতো · যত → যতো · তত → ততো · গত → গতো · শত → শতো · নত → নতো · হত → হতো · অত → অতো · বড় → বড়ো · ছোট → ছোটো ·
ভাল → ভালো · শুভ → শুভো · নব → নবো · তব → তবো · অথচ → অথচো · এগার → এগারো · তের → তেরো · পনের → পনেরো · ষোল → ষোলো ·
সতের → সতেরো · আঠার → আঠারো · এল → এলো · দিল → দিলো · নিল → নিলো · ছিল → ছিলো · পেল → পেলো · হল → হলো · শুল → শুলো ·
রইল → রইলো · চাইল → চাইলো · গাইল → গাইলো · পাইল → পাইলো · খাইল → খাইলো · হইল → হইলো · যাব → যাবো · খাব → খাবো ·
পাব → পাবো · নেব → নেবো · হব → হবো · শোব → শোবো · দিব → দিবো · নিব → নিবো · চাইব → চাইবো · গাইব → গাইবো · যাইব → যাইবো ·
খাইব → খাইবো · পাইব → পাইবো · হইব → হইবো · যেত → যেতো · দিত → দিতো · নিত → নিতো · পেত → পেতো · শুত → শুতো ·
চাইত → চাইতো · গাইত → গাইতো · যাইত → যাইতো · খাইত → খাইতো · পাইত → পাইতো · হইত → হইতো

**The wide "e" (33).**

এক → অ্যাক্ · একটা → অ্যাকটা · একবার → অ্যাকবার্ · একদিন → অ্যাকদিন্ · একজন → অ্যাকজন্ · একা → অ্যাকা ·
একসাথে → অ্যাকসাথে · একসঙ্গে → অ্যাকসঙ্গে · এমন → অ্যামন্ · কেমন → ক্যামন্ · যেমন → য্যামন্ · তেমন → ত্যামন্ ·
এখন → অ্যাখন্ · এখনও → অ্যাখনও · এখনো → অ্যাখনো · এত → অ্যাতো · এতো → অ্যাতো · কেন → ক্যানো · যেন → য্যানো ·
হেন → হ্যানো · দেখা → দ্যাখা · দেখো → দ্যাখো · দেখ → দ্যাখ · দেখাল → দ্যাখালো · দেখানো → দ্যাখানো · খেলা → খ্যালা ·
বেলা → ব্যালা · ফেলা → ফ্যালা · ঠেলা → ঠ্যালা · গেল → গ্যালো · গেছে → গ্যাছে · দেওয়া → দ্যাওয়া · নেওয়া → ন্যাওয়া

**A silent end, listed by name (138).** Each is handed with a hasanta: সব → সব্.

সব · খুব · শব · যব · আজব · গজব · আরব · আদব · সাহেব · নবাব · জবাব · কাবাব · কিতাব · হিসাব · অভাব · স্বভাব · প্রভাব ·
গরিব · জীব · শিব · নীরব · গৌরব · উৎসব · সম্ভব · অসম্ভব · অনুভব · দানব · রাত · হাত · ভাত · সাত · দাঁত · ছাত · জাত ·
ডাকাত · শীত · গীত · ভূত · দূত · প্রভাত · আঘাত · অতীত · উচিত · ইঙ্গিত · সংগীত · সঙ্গীত · বিপরীত · ভারত · ভরত · জগত ·
মানত · আদালত · শরবত · দৌলত · ইজ্জত · বরকত · রহমত · জল · ফল · দল · কল · তল · ছল · খল · নল · ফুল · ভুল · চুল · মূল · কূল ·
নীল · বিল · ঝিল · তিল · কিল · খাল · ডাল · চাল · জাল · তাল · গাল · মাল · লাল · পাল · হাল · ছাল · শাল · সকাল · বিকাল ·
বিকেল · কপাল · শিয়াল · বিড়াল · খেয়াল · দেয়াল · আড়াল · পাতাল · মশাল · রাখাল · কাঙাল · পাগল · ছাগল · জঙ্গল · মঙ্গল ·
সকল · কেবল · মহল · তেল · বেল · জেল · গোল · কোল · দোল · বোল · ঘোল · ঢোল · পুল · ফসল · কাজল · আঁচল · শিকল · পটল · বাদল ·
নকল · দখল · সরল · তরল · প্রবল · সফল · বিফল · কুশল · চঞ্চল · উজ্জ্বল · দুর্বল · পিতল · শীতল · কোমল · কমল

**Left as written (31).** Bare verb stems (20):

কর · ধর · পড় · শোন · শুন · রাখ · থাক · বস · ওঠ · হাস · লেখ · শেখ · ছাড় · মার · আন · জান · চেন · বাঁচ · বোঝ · কাঁদ

Two readings (6): আসল · মানব · পরব · বার · কোন · মত. English loans (5): ফর্ম · ফার্ম · প্ল্যাটফর্ম · ইউনিফর্ম · ব্যাঙ্ক.

**Verb stems (138).** A past, future or habitual form of these is spoken to its end. Ending in a
consonant (85):

কর · বল · চল · ধর · মর · পড় · লড় · নড় · জ্বল · গল · টল · দেখ · শুন · উঠ · বস · হাস · কাঁদ · লাগ · থাক · রাখ · ভাব ·
ডাক · পার · মার · নাম · খুঁজ · খুল · ফেল · ছুট · হাঁট · ফির · ঘুর · চিন · জান · আন · কাট · বাঁধ · মান · টান · ছাড় ·
ভাঙ · নাচ · বাঁচ · ঢুক · জাগ · বুঝ · গড় · চড় · লিখ · শিখ · খেল · ভুল · দুল · তুল · মিল · গুন · পুড় · উড় · জিত ·
হার · ভর · গিল · চাপ · কাঁপ · থাম · সাজ · ছিঁড় · মুছ · নিভ · বাড় · ফুট · পাক · রাঁধ · বাজ · ডুব · ভাস · আস · পৌঁছ ·
ঝুল · সার · খস · ঘষ · মাখ · ঢাল · গাঁথ

Ending in a vowel sign (53):

তাকা · দাঁড়া · পালা · জানা · শোনা · ঘুমা · বানা · পাঠা · চালা · হারা · ফুরা · লুকা · ছড়া · সাজা · নাচা · হাসা ·
কাঁদা · চেঁচা · জাগা · ফেরা · খাওয়া · তাড়া · কামড়া · থামা · নামা · বসা · চমকা · হাঁপা · মেলা · পৌঁছা · ঘোরা · ওঠা ·
শোয়া · বোঝা · শেখা · দৌড়া · লাফা · ঝাঁপা · কাটা · পোড়া · ভাসা · ডোবা · লাগা · বাজা · গড়া · ছাড়া · বাড়া · ঝরা ·
ঘুমো · এগো · পেরো · ফুরো · বেরো
