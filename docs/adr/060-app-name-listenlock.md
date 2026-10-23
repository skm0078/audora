# ADR-060: The app's name is ListenLock

**Status:** Accepted (2026-10-23). Changes only the name people see; the package, the repository and the code keep
"audora".

## Context
"Audora" is crowded: an audiobook studio at audora.art, an "Audora" app on Google Play, an audit product and more.
The app will be listed worldwide, not just in India, so the name should be English-first, say that the app is for
listening, and be free on Play, the App Store and the web.

Names checked on 2026-10-23 (Play and App Store search, the web, `.com` and `.app` DNS):

| Name | Finding |
|---|---|
| LockReader | No app of that name, but Play's search for it returns lock and security apps; says nothing about listening |
| ReadLocker | Taken (Play app, readlocker.com, browser extension) |
| PrivaRead | PrivRead is an iPhone app sold as "your private documents, read aloud" |
| Hearleaf, Readlark, Narrova, Inkvoice, LocalReader | Existing read-aloud or text-to-speech apps |
| Bolpath, Vachak, Sunao | Indic; Boipath (Bengali e-book reader), Vachak and Sunao (PDF listening apps) exist |
| **ListenLock** | No app or product; Play's search for it returns audio apps; `listenlock.com` and `listenlock.app` do not resolve |

## Decision
The app is called **ListenLock**: listening first, and the "lock" says that it keeps reading with the phone locked
and that books stay on the phone. In this change:

- The app's label (`app_name`, and "ListenLock Dev" for debug builds), and every English, Hindi and Bengali string
  that names the app. Hindi and Bengali keep the Latin spelling, as they did with Audora.
- Voice commands start with "ListenLock" ("ListenLock, pause"). The keyword phrases are the model's own BPE pieces
  (`bpe.model` from sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01), spotted as one word (`▁LI S T EN LO CK`) and
  as two (`▁LI S T EN ▁LO CK`). The narrator echo guard looks for the name in English, Hindi and Bengali spellings.
- Exports go to `Documents/ListenLock/`; backups are named `listenlock-backup-<date>.zip`. Restore reads a backup's
  contents, not its name, so old backups still restore.
- README, the public pages (`docs/index.html`, `docs/privacy.html`), the licences screen and the Play feature graphic.

Not changed, on purpose: the application id `io.github.skm0078.audora` (Play and installed phones know the app by it),
the repository and release file names (`audora-dev.apk`, so Obtainium links keep working), Kotlin packages, classes,
log tags, the database and preference file names, and history in older ADRs and progress notes.

## Alternatives considered
- **Keep Audora:** no work, but it competes with an audiobook studio of the same name.
- **LockReader:** available, but it is placed next to lock and security apps and does not say "listening".
- **Indic names (Bolpath, Kanpaath, Nijvaani):** clean in India, weaker for a worldwide listing.

## Consequences
- Files exported before this change stay in `Documents/Audora/`; new ones go to `Documents/ListenLock/`.
- People who used "Audora, pause" now say "ListenLock, pause"; the Settings text says so.
- Trademark offices (USPTO, WIPO, EUIPO, India) still need a search before the public launch.
- The Play listing title (at most 30 characters) is "ListenLock: PDF Read Aloud"; "offline" and "private" go in the short
  description, since Play does not allow claims such as "100%" or "secure" in titles.
