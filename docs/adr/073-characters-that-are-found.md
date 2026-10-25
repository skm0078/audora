# ADR-073: Find the characters, give them a voice, and measure it

**Status:** Accepted (2026-10-25). Follows ADR-056 to 058. First stage of the expressive reading rework, after the
maintainer judged character voices as "no intelligence, doesn't work most of the time".

## Context
Nothing measured who speaks a line or how. A marked set of 20 short stories in English, Hindi and Bengali
(`research/expressive-eval/passages/`, 157 sentences, 96 spoken lines, each marked with its speaker and feeling)
showed that a character got a voice of the right gender on 29 % of English lines, 11 % of Hindi lines and 0 % of
Bengali lines. No line got the wrong gender: the code gave up and kept the plain dialogue voice. The causes, in
order of damage:
1. Dialogue in curly single quotes (‘…’), common in British, Hindi and Bengali printing, was never speech.
2. Quotes under three words ("Alive?", "बिल्ली?") stayed in the narrator's voice.
3. Hindi and Bengali tags took the word before the verb as the speaker ("उसने धीरे से कहा" named "से", "দাদা হেসে
   বলল" named "হেসে"), and older Bengali verbs (বলিল, কহিলেন, জিজ্ঞাসা করিল) were not known.
4. A name told no gender, so a character waited for a pronoun, which Bengali never gives.
5. An untagged line after "Ravi stopped at a stall." was not given to Ravi.

Feelings: two thirds of the lines that call for one were read flat, and a bare "!" was read as joy, so "Get out!"
and "Help!" sounded cheerful (Bengali Rasa's HAPPY style).

## Decision
1. **A measured set and a regression test.** `ExpressiveEval` reads the marked passages the way playback does
   (`QuoteCarry`, `DeliveryPlanner`) and `ExpressiveEvalTest` fails when a score drops below its floor, when any line
   gets the wrong gender's voice, or when a feeling is read as its opposite. A second set (`holdout/`) was written
   after the rules were tuned and is never tuned on, so the scores stay honest. Floors only go up.
2. **Every quote style.** `QuoteSplitter` reads `‘…’`: `‘` opens at a word's start, `’` closes at a word's end, so an
   apostrophe inside a word ("don’t") never does. A short quote is speech when it ends on speech punctuation.
3. **Subject first in Hindi and Bengali.** Both put the subject first: a tag's speaker is the clause's first word
   or the word before ने, skipping openers (तब, তারপর) and a noun a participle takes as its object
   ("দীর্ঘশ্বাস ফেলে"). Bengali sadhu verbs are known. Hindi intransitive verbs tell gender (बोली, मुस्कुराया).
4. **Names tell gender.** `NameGenders` lists clearly gendered names and role words in each language (Mira, दादी,
   পিসি, the captain). Names used for both genders are left out: a wrong-gender voice is worse than none.
5. **Narration helps.** A name the narration makes a sentence's subject is remembered for a later "she said"; an
   untagged line goes to the subject of the narration just before it in its paragraph; a line that calls someone by
   name ("My phone died, Tom.") is not given to them.
6. **"!" is emphatic, not happy.** A new feeling `EMPHATIC` (a little quicker and louder, Rasa's CONV style) is used
   for an exclamation without a cue word.
7. **Experimental.** "A voice for each character" is labelled experimental until the held-out scores are good.

## Results
| Set | Right-gender voice before → after (en / hi / bn) | Opposite feelings |
|---|---|---|
| Tuned | 29 / 11 / 0 % → 74 / 88 / 100 % | 5 → 0 |
| Held out | 22 / 0 / 0 % → 55 / 7 / 0 % | 4 → 1 |

The held-out Hindi and Bengali scores show the limit of rules: names that are in no list (बिरजू, পল্টু) still get no
gender, and lines tagged with an action instead of a speech verb ("बिरजू ने मुँह फेर लिया") are not attributed. That
needs reading the scene, the next stage (an on-phone director model, measured in `research/expressive-eval`).

## Consequences
- More lines change voice than before. STUDIO_AUDIO_VERSION goes to 4, so studio audio renders again.
- A Rasa voice installed before keeps its old style table: EMPHATIC falls back to its narration style until the
  voice is installed again.
- The name lists are data, not rules: adding a name is a one-line change with a test.
