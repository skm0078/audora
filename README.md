# ListenLock: PDF Read Aloud

A free Android app that reads PDFs aloud in natural voices, in English, Hindi and Bengali. Start from any page or
sentence, pick a voice, and keep listening with the screen off or while you use other apps. Everything happens on your
phone: no account, no ads, no analytics, and no internet once your voices are in place. Made by TelosMind; free and
open source under [GPL-3.0](LICENSE).

ListenLock was called Audora until October 2026. The app id (`io.github.skm0078.audora`), this repository and the
download links keep that name, so installed copies update as before.

**Status:** in testing. Google Play is coming soon, through a closed test first (see [Roadmap](#roadmap)).
What changed and what still needs checking on a phone: [docs/PROGRESS.md](docs/PROGRESS.md).

## What it does

**Reads like a person**
- Follows two-column pages in the right order, skips page numbers, headers and footers, and pauses after headings and
  list items. Tables, footnotes, captions and maths are read in a way that makes sense when heard.
- Says "₹1,50,000" as "one lakh fifty thousand rupees" and "₹500 करोड़" as "पाँच सौ करोड़ रुपये".
- **Read to suit the book:** works out whether a book is a story, poem, news, report, textbook or scripture, and sets
  pace and pauses to match. In stories, whispered, shouted, sad and scared lines are read that way.

**English, Hindi and Bengali**
- Each part of a mixed sentence is read in its own language's voice.
- Older Hindi and Bengali fonts (Kruti Dev, Chanakya, DevLys, SutonnyMJ) are read correctly.
- Scanned pages are read with offline text recognition (OCR, a one-time 38 MB download), including worn print and
  sideways pages. A photo of a page (**Point and read**) becomes a one-page book.

**Voices** (full list and credits [below](#voices-and-credits))
- English works straight away with the built-in male voice, Bruno. Others are optional downloads.
- **Bengali emotion voices (Rasa):** narration in a book style, and each line's feeling (happy, sad, angry, scared,
  surprised) in its own style.
- **Acted Hindi voice for studio mode:** an optional 2.9 GB download (Wi-Fi, phones with 8 GB of memory) that acts
  dialogue and feelings. It renders slowly, so it is used only by studio mode.
- Characters can each get their own voice; quotes and headings can use a different one.

**Listening**
- Picks up at the same sentence, even after a restart. Sleep timer by minutes, end of page or end of chapter.
- Follow along on the page with the sentence (and word) highlighted, or in a clean text view.
- Speed from 0.5× to 3× (**Smart speed** keeps fast speech clear), headset and lock-screen controls, Android Auto,
  a home-screen widget, and screen-off voice commands ("ListenLock, pause").
- **Studio mode:** renders a book ahead of time while the phone charges, so the slower, better voices play without
  gaps. Shows progress, time left and the audio it has made, which you can listen to, save or delete.
- **Export:** save a book as an audiobook (one audio file per chapter), share a clip, or save a talking book.

**Your library**
- PDF, EPUB and plain-text books, text shared from other apps, and watched folders that add new books by themselves.
- Bookmarks, highlights, voice notes, search in the book, and an offline dictionary on tap (English, Hindi, Bengali).
- Study aids: quiz me from your highlights, and a reading tutor that listens while you read aloud.
- **Back up and restore** to move everything to a new phone.

## Privacy

- Your books, notes and reading history stay on your phone. There is no account, no server, no analytics, no crash
  reporting and no ads.
- The internet is used only for downloads you choose (voices, OCR, dictionaries, voice commands). They come from
  this project's GitHub releases or the upstream project's own GitHub, over HTTPS, and each file is checked against a
  fingerprint built into the app.
- **App lock** (off by default) asks for your phone's own fingerprint, face or screen lock. **Hide app content** blocks
  screenshots and the recent-apps preview.
- Android's cloud backup and phone-to-phone transfer are off; Back up and restore makes a file that you keep.
- Logs never contain text from your books and leave the phone only if you share them.

Full policy: [docs/privacy.html](docs/privacy.html). Design record: [ADR-061](docs/adr/061-privacy-and-app-lock.md).

## What you need

- Android 12 or newer on a 64-bit phone (almost every phone from 2019 on).
- Room for the app (over 100 MB) plus any voices you download (up to 150 MB each; the acted Hindi voice 2.9 GB).

## Install

**Google Play:** coming soon. A closed test runs first; ask to join it if you would like early access.

Until then ListenLock is published on GitHub. The last release there, v0.0.1, came before most of the features above;
v0.1.0 is next. Testers can use the [test build](#test-builds-for-testers) meanwhile.

### The easy way: Obtainium

[Obtainium](https://github.com/ImranR98/Obtainium) installs apps straight from where their makers publish them and
tells you when there is an update. You set it up once.

1. **Get Obtainium.** On your phone, open https://github.com/ImranR98/Obtainium/releases/latest, scroll to
   **Assets**, and tap the file whose name has **`arm64-v8a`** in it and ends in **`.apk`**. When it has downloaded,
   tap **Open**. (Obtainium is also on F-Droid.)
2. **Allow the install.** Android says it can't install apps from this source. Tap **Settings**, turn on
   **Allow from this source**, go back and tap **Install**. This applies only to the app you downloaded with (your
   browser), and you can turn it off afterwards.
3. **Add ListenLock.** Open Obtainium, tap **Add App**, paste
   ```
   https://github.com/skm0078/audora
   ```
   into **App source URL**, and tap **Add**. Leave every other option as it is.
4. **Install ListenLock.** Tap **Install**. Android asks once more, this time for Obtainium: turn on
   **Allow from this source**, go back, and tap **Install**. If Play Protect stops you, see below.

**Updates:** Obtainium checks in the background and notifies you. Tap **Update** next to ListenLock; your library and
reading positions are kept.

### Without Obtainium

Open https://github.com/skm0078/audora/releases/latest on your phone, tap **`audora-vX.Y.Z-arm64.apk`** under
**Assets**, then follow steps 2 and 4 above. A newer version installs over the old one and keeps your library, but
nothing tells you when one is out.

### "Blocked by Play Protect"

Play Protect warns about apps from outside the Play Store. That is expected for the GitHub builds:

- **"Unsafe app blocked"** or **"App blocked to protect your device":** tap **More details**, then **Install anyway**.
- **"Send app for a security scan?"** Either choice is fine.

ListenLock asks only for notifications (playback controls), running in the background (reading with the screen off)
and the internet (only for the downloads you choose). The microphone is used only for voice commands, the reading
tutor and voice notes, when you turn them on; Point and read uses your camera app, so ListenLock has no camera access. It never asks for contacts, messages, location or
accessibility access. Every release is signed with the same key, so an update can only come from ListenLock's own
releases. SHA-256 fingerprint: `a4a558acec075b159872c52a91bd6873b16fa340ba40eafd2608888e0ecef8ea`.

### Add your phone (once, for GitHub installs)

Google is starting to block apps from outside the Play Store unless the developer is registered and, for a free
account like ListenLock's, the phone is on the developer's list (up to 20 phones). In India this starts in 2027.
Play installs don't need this.

1. Ask whoever gave you ListenLock to add your phone. They send you a link.
2. Open it **on the phone you will use ListenLock on**, within 7 days. Check the developer name, then confirm.
3. Send back the code your phone shows. When they have entered it, you're done.

## First run

1. **Add a book:** tap **Add PDF** and pick one. ListenLock remembers where it is and does not copy it.
2. **Read aloud:** open the book, tap a line on any page to start there, or tap **Read aloud**.
3. **Keep reading with the screen off:** if the library shows **Keep reading with the screen off**, tap **Set up** and
   follow the steps for your phone's brand (also in **Settings → Background reading**). Without it, many phones stop
   reading a few minutes after the screen turns off.
4. **Voices:** **Settings → Voices** opens on the Hindi and Bengali packs, one **Download** each; every voice is under
   **All voices**, each with a sample. Until you download one, Hindi and Bengali use your phone's own offline voice.
5. **Scanned PDFs:** the first scanned page offers the OCR download (also in **Settings → Scanned pages (OCR)**).

## When something goes wrong

- **Reading stops when the screen is off:** go through **Settings → Background reading** again.
- **A voice pauses between sentences** or shows **Too slow on this phone:** pick another voice, or let studio mode
  render the book ahead while the phone charges.
- **"File moved or deleted":** tap the book and pick the same file again; your place is kept.
- **Anything else:** **Settings → Share logs**, and send the file with a line on what happened and when.

## Test builds (for testers)

The newest `dev` build, rebuilt on every change:
**https://github.com/skm0078/audora/releases/download/dev-build/audora-dev.apk**.
It installs as a separate app, "ListenLock Dev" (`io.github.skm0078.audora.debug`), next to the released one, and
also shows the voices and options the release build hides. A newer test build installs over the older one. If Android
says **"App not installed"**, uninstall the old test build first (it was signed with an earlier key; this clears its
library). Don't add the test build to Obtainium: its release name never changes, so updates go unnoticed.

## Voices and credits

Every voice runs on the phone through [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx). Full credits and licence
texts are in the app under **Settings → Voices & licences**.

| Language | Voice | Size | Licence |
|---|---|---|---|
| English | **Bruno** (built in), KittenTTS micro by KittenML | in the app | Apache-2.0 |
| English | KittenTTS nano (female, male) by KittenML | 29 MB | Apache-2.0 |
| English | Kokoro Heart, Kokoro Michael by hexgrad (premium) | 150 MB | Apache-2.0 |
| English | Piper cori by Michael Hansen, LibriVox recordings by Cori Samuel | 65 MB | MIT; recordings public domain |
| Hindi | **Piper pratham** (default), Piper priyamvada by PravalX | 64 MB | CC BY-NC-SA 4.0 |
| Hindi | Acted voice for studio mode: Chatterbox Multilingual by Resemble AI | 2.9 GB | MIT |
| Bengali | **SYSPIN male** (default), SYSPIN female by the SYSPIN project, IISc Bengaluru | 114 MB | MIT |
| Bengali | Rasa expressive (female, male) by AI4Bharat, IIT Madras | 123 MB | CC BY 4.0 |

The test build also offers Supertonic 3 Hindi (OpenRAIL-M), Piper google Bengali (CC BY-SA 4.0) and MMS Bengali by
Meta (CC BY-NC 4.0). Some voices are licensed for non-commercial use only, which is one reason ListenLock is free.

Other data: OCR by [Tesseract](https://github.com/tesseract-ocr/tesseract) with `tessdata_best` (Apache-2.0);
English dictionary from Open English WordNet (CC BY 4.0, from Princeton WordNet); Hindi and Bengali dictionaries
from Wiktionary via kaikki.org (CC BY-SA 4.0); punctuation and voice-command models from k2-fsa (Apache-2.0); Silero
VAD (MIT).

## Roadmap

1. **v0.1.0:** a short test on real phones, one batch of fixes, then the first release from `prod` (GitHub and Play).
2. **Google Play closed test:** at least 12 testers for at least 14 days, with weekly fixes for bugs and speed.
3. **Google Play for everyone,** once no serious bugs remain.
4. **A rebuilt app (Flutter)** later, delivered as an update to the same app, keeping your library. Offline and
   private stays the default.

## For contributors

Read [AGENTS.md](AGENTS.md), then [docs/BUILD-SPEC.md](docs/BUILD-SPEC.md), [docs/TICKET-PLAN.md](docs/TICKET-PLAN.md)
and the decisions in [docs/adr/](docs/adr/). Reading quality is tracked in [docs/QUALITY.md](docs/QUALITY.md) and
[docs/LISTENING.md](docs/LISTENING.md).
Quick check: `./gradlew ktlintCheck detekt test assembleDebug` (JDK 21 and the Android SDK).
Branches: `dev` is integration, `prod` is what people install; releases are cut from `prod` as described in
[docs/RELEASING.md](docs/RELEASING.md).

## Licence

ListenLock is free software under the [GNU General Public License v3.0](LICENSE). Voices and data keep their own
licences, listed above and in the app.

The GPL covers the code only. The name ListenLock™ and the ListenLock logo (`docs/store/logo.png` and the app icon)
belong to skm0078 (TelosMind Labs); the logo is © 2026, all rights reserved. A fork must use its own name and icon.
