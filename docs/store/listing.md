# ListenLock: Play store listing

The listing pasted into Play Console → Grow users → Store presence → Main store listing, kept here so every change is
reviewed like code. Play limits: app name 30 characters, short description 80, full description 4,000. Every claim
must be true of the build being uploaded; the licence check for every graphic is in the private asset register.

Rules for this text (Play's metadata policy and the maintainer's): no "best", "#1" or ranking words, no other app's
name, no prices or promotions, no emoji, no testimonials, no keyword lists, and no personal details. The app is not
aimed at children, so the listing does not advertise Kids mode.

Hindi and Bengali are drafts: a native speaker reads them before they are pasted in.

## The text

The text itself lives in [`fastlane/metadata/android/`](../../fastlane/metadata/android/), one folder per language,
because F-Droid and IzzyOnDroid read it from there and the Play listing must say the same thing
(ADR 2026-10-31-fdroid-ready-build). CI checks every file against Play's limits (`scripts/check_store_metadata.py`).

| Language | Play locale | Folder |
| --- | --- | --- |
| English (default) | en-US | [en-US](../../fastlane/metadata/android/en-US/) |
| Hindi | hi-IN | [hi-IN](../../fastlane/metadata/android/hi-IN/) |
| Bengali | bn-BD (Play has no bn-IN) | [bn-BD](../../fastlane/metadata/android/bn-BD/) |

In each folder: `title.txt` is the app name (30 characters at most; the English one, "ListenLock: PDF & Books
Aloud", is the maintainer's choice), `short_description.txt` the short description (80), and
`full_description.txt` the full description (4,000). `en-US/changelogs/<versionCode>.txt` is each release's
What's new on F-Droid (500 characters; the release workflow fails without it). Paste from these files into Play
Console, so the stores never disagree.

## Graphics

| Asset | Play size | Source | Licence status |
| --- | --- | --- | --- |
| App icon | 512 × 512 PNG, 32-bit | [icon.png](icon.png), copied to `fastlane/.../en-US/images/icon.png` | Waits for the logo's copyright assignment |
| Feature graphic | 1024 × 500 PNG or JPEG, no alpha | [feature-graphic.png](feature-graphic.png), copied to `.../images/featureGraphic.png` | Waits for the logo's copyright assignment |
| Phone screenshots | 2 to 8, 16:9 or 9:16, 1080 px wide | Taken on the test phone from the release build, saved to `.../en-US/images/phoneScreenshots/` (IzzyOnDroid requires some) | Pages shown must be public-domain or our own text |

## Categorisation

- **App category:** Books & Reference. **Tags:** pick the closest from Play's list (for example Audiobooks, E-books).
- **Contact email:** `support@telosmindlabs.com`. **Website:** `https://listenlock.telosmindlabs.com/`.
