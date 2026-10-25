# ADR-074: Only voices licensed for commercial use; Supertonic 3 reads Hindi

**Status:** Accepted (2026-10-25). Supersedes ADR-030's Hindi default and ADR-058's per-voice respellings; amends
ADR-053's Bengali alternatives.

## Context
The catalog offered three voices whose licences forbid commercial use: Piper's Hindi **pratham** (the recommended
Hindi download) and **priyamvada** (both CC BY-NC-SA 4.0), and Meta's **MMS Bengali** (CC BY-NC 4.0). ListenLock
is free today, but a paid tier, a company, or a store listing with ads or a support link would put it in breach, and
removing a default voice after launch strands listeners. The owner decided to remove them before v0.1.0.

Hindi still needs a voice. Supertonic 3 (Supertone, OpenRAIL-M model, MIT code) was already in the catalog but
hidden from the Play build because its model licence file was missing upstream; it is now published with the
archived model (`supertone-oss-archive/supertonic-3`). OpenRAIL-M allows commercial use: it asks that its use
restrictions be passed on to users and its notices kept, which the voice's credit line and licence link already do.
On a desktop it reads Hindi at a real-time factor of about 0.3 (4 threads), and an ASR check transcribes it almost
word for word. Its single model holds ten voice styles that each read every language it supports: five women
(styles 0-4) and five men (5-9), told apart by ear and by pitch (163-224 Hz against 93-135 Hz).

## Decision
1. **Remove the three NC voices from `speech/models.json`.** Their ids live on in `WithdrawnVoices`: at start a
   phone deletes a downloaded copy (catalog origin only; an imported folder is the user's and stays), and forgets
   it as a language's reading voice and its speed measurement. That language reads with its default (the phone's
   voice for Hindi and Bengali) until the listener picks another.
2. **Supertonic 3 is the recommended Hindi download**, no longer hidden from the Play build, and no longer
   labelled "(premium)".
3. **Hindi characters get voices from Supertonic's other nine styles.** `CastSpeakers` gains a table keyed on
   `supertonic-hi` and its archive, so `CharacterCast` can give a woman another female style and a man a male one.
   The narrator stays style 2.
4. **Drop `VoiceRespellings`.** Its only fixes were for the Piper Hindi voices' nukta letters; Supertonic reads
   them.
5. **A test guards the rule:** `CatalogLicencesTest` fails the build if either catalog offers a licence with NC in
   its name or URL, lists a withdrawn voice, or recommends a voice it does not offer.

## Alternatives considered
- **Keep the NC voices, hidden from the Play build.** Rejected: the test build would still ship them, and the
  owner asked for them gone.
- **Rasa's Devanagari speakers (Dogri, Maithili, Marathi) for Hindi.** They read Hindi with emotion styles but with
  another language's accent; kept as a later experiment, not a default.
- **Piper hi_IN rohan.** Lessac-derived training data, not licensed for redistribution (ADR-012).

## Consequences
- A tester who downloaded pratham, priyamvada or MMS Bengali loses it at the next start and hears the phone's voice
  until they pick another; "What's new" says so.
- Supertonic's download is 145 MB against Piper's 64 MB.
- **Needs a phone check:** Supertonic's speed on the test phone. The existing speed measure (P4-02) marks it too slow
  if it cannot keep up, and the Hindi default would then need revisiting.
- The `s1-voices` release still hosts the MMS Bengali archive for the benchmark workflow; it is no longer installed
  by the app.
