# ADR-061: What leaves the phone, and an optional app lock

**Status:** Accepted (2026-10-23). Adds Settings → Privacy and security; no new dependency.

## Context
The name ListenLock (ADR-060) promises that books stay on the phone, so the promise has to be checked, written down
in the app and on the privacy page, and match Play's Data safety form. An audit of `dev` on 2026-10-23 found:

| Path off the phone | Finding |
|---|---|
| Network | One HTTP client (`UrlConnectionHttpSource`), only for user-chosen downloads: voices, OCR, punctuation, dictionary, voice-command, listening and acted-voice packs. GET requests with a fixed user agent ("Audora (voice download)" and so on), HTTPS only (the parsers reject `http://`), every file checked against a SHA-256 pinned in the APK. Hosts: `github.com/skm0078/audora/releases` (and the CDN it redirects to) and `raw.githubusercontent.com` (tessdata). Nothing is sent in a request body. |
| Hugging Face | Not contacted by the app (only by build workflows); the privacy page said it was. Fixed. |
| Phone TTS engine | Used for a language without a downloaded voice; voices that need the network are refused (`SystemSpeechEngine.usable`), so text stays on the phone. |
| Analytics, crash reporting, ads, accounts | None: no Firebase, Play services, Crashlytics, Sentry or ACRA in any module. Crashes go to the local log only. |
| Android backup | `allowBackup="false"` stops Google cloud backup, but on Android 12+ not device-to-device transfer. |
| Logs | `filesDir/logs`, 2 MB × 3, no book text (AGENTS.md); leave only through Settings → Share logs. |
| Share sheet | Only when the person shares a clip, a talking book or logs, to an app they pick. |
| Exports, backups | Files the person saves: exports in shared storage (`Documents/ListenLock/`, Music), backups as an unencrypted zip. |
| On-screen | The library and pages show in the recent-apps preview and in screenshots; nothing locks the app on a shared phone. |
| Media session, widget | Book title on the notification, the lock screen, a connected car and the home-screen widget, as for any player. |

## Decision
1. **No Android backup or transfer.** `data_extraction_rules.xml` excludes every domain from both `cloud-backup` and
   `device-transfer`. Moving to a new phone is the app's own Back up and restore (ADR-046).
2. **App lock, off by default.** When on, the app asks for the phone's own fingerprint, face, PIN, pattern or
   password through the framework `BiometricPrompt` (API 30+, so no `androidx.biometric`; `USE_BIOMETRIC` is a normal
   permission) when it starts and after 30 s out of sight (`AppLockConfig`). Turning it on asks first, so it cannot
   be turned on with a lock that does not work. The app keeps no PIN or secret of its own. If the phone's screen lock
   is later removed, the lock opens instead of locking the person out. Playback controls in the notification keep
   working; the lock guards the library and notes. With the lock on, the recent-apps preview is blank (Android 13+).
3. **Hide app content, off by default.** `FLAG_SECURE`: no screenshots, screen recording or casting, blank
   recent-apps preview. Off by default because people share screenshots of pages and bugs.
4. **A plain statement** heads Settings → Privacy and security, and the privacy page gains "Your files, your
   responsibility": the data is only on the phone, nobody else can recover it, backups are unencrypted files the
   person keeps.
5. **Play Data safety:** "No data collected", "No data shared" (see docs/RELEASING.md). True only while the table
   above holds; any change to it updates the page, this ADR's table and the form in the same release.

## Alternatives considered
- **An app PIN of our own:** a secret to store, a reset flow to build, and a forgotten PIN would lock a person out of
  their library with no server to recover it. The phone's lock already does this well.
- **`androidx.biometric`:** only needed below API 30; minSdk is 31.
- **Encrypting the database and files at rest:** Android's file-based encryption already protects app data while the
  phone is locked, and other apps cannot read it. A key held by the app adds little against someone holding the
  unlocked phone, which the app lock covers.
- **Encrypted backups (a password on the zip):** useful for backups kept in a cloud drive; left as an option for later,
  since a forgotten password loses the backup.
- **Hiding the book title from the notification and lock screen:** possible later as a setting; most people want to see
  what is playing.

## Consequences
- A restore over Android's phone-to-phone transfer no longer carries the library; Back up and restore does.
- With the lock on, coming back from the camera app (Point and read) or a long file pick can ask again.
- A setting that reads as "lock on" when the preference file cannot be read: someone who never turned it on unlocks
  once with the phone's lock and can turn it off.
