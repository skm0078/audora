# Audora

A free Android app that reads PDFs aloud in natural voices, in English, Hindi and Bengali.
Start from any page or sentence, pick a voice, and keep listening with the screen off or while you use other apps.
Everything happens on your phone: no account, no ads, no tracking, and no internet needed once your voices are in place.

- **Reads like a person:** follows two-column pages in the right order, skips page numbers, headers and footers,
  says "₹1,50,000" as "one lakh fifty thousand rupees", and pauses after headings and list items.
- **English, Hindi and Bengali,** including a sentence that mixes them (each part in its own language's voice),
  older Hindi and Bengali fonts (Kruti Dev, Chanakya, DevLys, SutonnyMJ) and scanned pages (read with OCR).
- **Picks up where you stopped,** at the same sentence, even after the phone restarts. A sleep timer stops after a set
  number of minutes, at the end of the page or at the end of the chapter.
- **Follow along** on the page itself, with the sentence being read highlighted.

Free and open source under [GPL-3.0](LICENSE). Progress and known limits: [docs/PROGRESS.md](docs/PROGRESS.md).

## What you need

- An Android phone with **Android 12 or newer** and a 64-bit processor (almost every phone from 2019 on).
- Room for the app (over 100 MB, mostly the built-in English voice) plus any voices you download (up to 150 MB each).
- Your phone added to Audora's list, once (next section).

## Add your phone (once)

Google is starting to block apps from outside the Play Store unless their developer is registered and, for a free
account like Audora's, the phone is on the developer's list (up to 20 phones). In India this starts in 2027;
Google has not said when. Until then Audora installs without this step, but do it now so updates keep working.

1. Ask whoever gave you Audora to add your phone. They send you a link.
2. Open the link **on the phone you will use Audora on**, within 7 days. It shows the developer's name: check it is
   the person you expect, then confirm.
3. Your phone shows a code. Send the code back to them. When they have entered it, you're done.

New phone? Ask again; your old phone can be taken off the list.

## Install (the easy way: Obtainium)

[Obtainium](https://github.com/ImranR98/Obtainium) is a free app that installs apps straight from where their
makers publish them and tells you when there is an update. You set it up once.

1. **Get Obtainium.** On your phone, open https://github.com/ImranR98/Obtainium/releases/latest, scroll to
   **Assets**, and tap the file whose name has **`arm64-v8a`** in it and ends in **`.apk`**. When it has downloaded, tap **Open**.
   (Obtainium is also on F-Droid if you use that.)
2. **Allow the install.** Android says it can't install apps from this source. Tap **Settings**, turn on
   **Allow from this source**, go back and tap **Install**. This permission is only for the app you downloaded
   with (your browser), and you can turn it off again afterwards.
3. **Add Audora.** Open Obtainium, tap **Add App** at the bottom, paste
   ```
   https://github.com/skm0078/audora
   ```
   into **App source URL**, and tap **Add**. Leave every other option as it is.
4. **Install Audora.** Tap **Install**. Android asks once more to allow installs, this time from Obtainium:
   turn on **Allow from this source**, go back, and tap **Install**. If Play Protect stops you, see below.

**Updates:** Obtainium checks for new versions in the background and shows a notification. Open Obtainium and tap
**Update** next to Audora. Your library and reading positions are kept.

## Install without Obtainium

Open https://github.com/skm0078/audora/releases/latest on your phone, tap **`audora-vX.Y.Z-arm64.apk`** under
**Assets**, then follow steps 2 and 4 above. To update later, do the same with the newer version: it installs over
the old one and keeps your library. You will not be told about updates, which is why Obtainium is easier.

## "Blocked by Play Protect"

Google Play Protect warns about apps that don't come from the Play Store. That is expected for Audora, which is
published only on GitHub. Depending on your phone you see one of these:

- **"Unsafe app blocked"** or **"App blocked to protect your device":** tap **More details**, then **Install anyway**.
- **"Send app for a security scan?"** Either choice is fine. **Scan app** uploads Audora to Google for checking and
  then installs it; **Don't send** installs it straight away.

Audora asks only for what reading aloud needs: notifications (the playback controls), running in the background
(so reading continues with the screen off), and the internet (only to download voices and the OCR files you ask
for). It never asks for your contacts, messages, location, or accessibility access.
Every release is signed with the same key, and Android refuses an update signed with any other, so an update can
only come from Audora's own releases. The key's SHA-256 fingerprint is
`a4a558acec075b159872c52a91bd6873b16fa340ba40eafd2608888e0ecef8ea`.

## First run

1. **Add a PDF:** tap **Add PDF** and pick one from your phone. Audora remembers where it is and does not copy it.
2. **Read aloud:** open the book, go to any page and tap a line (or a sentence in the text view) to start there,
   or tap **Read aloud**.
3. **Keep reading with the screen off:** if your phone may stop Audora in the background, the library shows
   **Keep reading with the screen off**. Tap **Set up** and follow the steps for your phone's brand. Without this, many phones (Motorola, Xiaomi,
   OnePlus, Samsung and others) stop reading a few minutes after the screen turns off.
   It is also in **Settings → Background reading**.
4. **Voices:** English works straight away with the built-in voice. For Hindi and Bengali, and for other English
   voices, open **Settings → Voices**, tap **Download** next to the voice you want, and pick it for its language.
   Until you download one, Hindi and Bengali use the voice from your phone's own text-to-speech settings.
   Who made each voice and its licence are listed in Settings.
5. **Scanned PDFs:** the first scanned page you open offers to download the text recognition files (about 38 MB,
   once, for all three languages). Also in **Settings → Scanned pages (OCR)**.

Downloads need a connection; everything else works offline.

## When something goes wrong

- **Reading stops when the screen is off:** go through **Settings → Background reading** again, then try once more.
- **A voice pauses between sentences** or Settings → Voices says **Too slow on this phone:** pick another voice for
  that language. Large "premium" voices are too slow on many phones.
- **"File moved or deleted":** tap the book and pick the same PDF again; your place is kept.
- **Anything else:** open **Settings → Share logs** and send the file to whoever gave you Audora, with a line on
  what happened and when. The logs never contain text from your PDFs.

## Test builds (for testers)

The newest build of the `dev` branch, rebuilt on every change, is at
**https://github.com/skm0078/audora/releases/download/dev-build/audora-dev.apk**.
It installs as a separate app ("Audora" with package `io.github.skm0078.audora.debug`), next to the released one,
and is for testing only. A newer test build installs over the older one and keeps its library. If Android says
**"App not installed"**, the old test build was signed with a different key (builds from before the dev key was set
up were): uninstall it first, which clears its library and reading positions. Don't add the test build to
Obtainium; its release name never changes, so Obtainium would not notice new builds.

## For contributors

Read [AGENTS.md](AGENTS.md), then [docs/BUILD-SPEC.md](docs/BUILD-SPEC.md) and [docs/TICKET-PLAN.md](docs/TICKET-PLAN.md).
Quick check: `./gradlew ktlintCheck detekt test assembleDebug` (needs JDK 21 and the Android SDK).
Releases are cut from `prod` as described in [docs/RELEASING.md](docs/RELEASING.md).
