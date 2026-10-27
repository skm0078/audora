# ADR 2026-10-27-licence-fixes: Supertonic's use terms, a Lessac-derived voice withdrawn, missing credits

**Status:** Accepted (2026-10-27). Follows ADR-074 (commercial-use voices only) after a full licence audit of every
voice and model the Play app ships or downloads. A licence bug fix, allowed in the feature freeze.

## Context

The audit found every voice and model allows commercial use, with three gaps:

1. **Piper's Google Bengali** (`piper-bn-google`, CC BY-SA 4.0 data) is fine-tuned from Piper's LibriTTS-R voice,
   whose own model card says it was fine-tuned from English Lessac. Lessac's data may not be redistributed, and the
   catalog's rule is never to offer a Lessac-derived voice. It was also the weakest Bengali voice (CER 13.8% vs
   SYSPIN 5.1%).
2. **Supertonic 3** (the Hindi default) is under the BigScience Open RAIL-M licence. It allows commercial use and
   redistribution, but section 4 requires the use restrictions (Attachment A) to be "an enforceable provision" in the
   terms under which we distribute it, and every recipient to get a copy of the licence. Its sherpa-onnx archive
   carries only the MIT licence of Supertone's code.
3. **PDFium** (BSD-3-Clause, which asks for its notice in the materials shipped with a binary) and **DjVuLibre**
   (GPL-2.0-or-later) were not credited on Voices & licences.

## Decision

- `piper-bn-google` leaves `speech/models.json` and joins `WithdrawnVoices.IDS`: a phone that downloaded it deletes
  it at the next start and Bengali reads with its default (SYSPIN male) until another voice is picked. The simple
  voice list hid only this voice, so its hidden set is now empty.
- A catalog entry may name **use terms** (`useTerms`, today only `openrail-m`). The build checks the value, the app's
  catalog parser leaves out a voice whose terms it cannot show, and `VoiceRow.useTerms` carries it to the UI. Before a
  download or update of such a voice, both download buttons (Voices screen, the reader's language-pack card) show
  the restrictions in plain words, with "Read the full licence", and start only on "Agree and download". Asked every
  time (downloads are rare); nothing is stored. Logged as counts of shown, agreed and declined, per voice id.
- A catalog entry may name a **model licence** (`modelLicence`, a repo file). The voice mirror adds it to the zip as
  `MODEL_LICENSE`, which the licences screen shows first among a voice's files. Supertonic's zip is re-pinned with
  `speech/licences/bigscience-openrail-m.txt` (the licence text as published, from ScanCode's licence database,
  matching Supertone's LICENSE page). Installed copies show "Update available".
- PDFium and DjVuLibre are listed under the software on Voices & licences; their notices ship in
  `app/src/main/assets/licences/` (PDFium's LICENSE, DjVuLibre's COPYING).

## Alternatives considered

- **Keep the Google Bengali voice:** rejected by the user (2026-10-27); the other two Bengali voices cover it.
- **Show Supertonic's terms only on the licences screen:** a notice nobody has to see is weak as "an enforceable
  provision"; a click-through at download is the usual practice for OpenRAIL models.
- **Remember the agreement:** saves one tap on a rare action, costs a stored flag and a reset story. Not worth it.
- **Label exported audiobooks as computer-made:** restriction (e) binds the person who publishes the audio, and the
  dialog tells them. A file label can be added later without a decision.

## Consequences

- Phones with the Google Bengali voice lose it once, with a What's new line saying why.
- Supertonic downloads and updates take one more tap. Its zip is about 15 KB larger.
- Any future model with a use-restricted licence needs a `useTerms` value, its strings in three languages and,
  when its archive lacks the licence, a `modelLicence` file.
