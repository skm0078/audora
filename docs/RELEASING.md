# Releasing Audora

How a signed APK gets from `prod` to friends' phones, and how the signing key is kept (ticket P0-02, Build Spec §7).

## How it works

- `gradle.properties` holds `audora.versionName` (for example `0.0.1`). It is the only version number: the
  Android `versionCode` is derived from it (`MAJOR*1000000 + MINOR*1000 + PATCH`), so a newer release always
  updates an older one.
- `.github/workflows/release.yml` builds `:app:assembleRelease`, signs it with the release key, checks
  16 KB alignment and the signature, and publishes `audora-vX.Y.Z-arm64.apk` plus `SHA256SUMS.txt` to
  GitHub Releases under the tag `vX.Y.Z`. It also builds the signed Play bundle (`:app:bundleRelease`) and keeps it
  as the run's `audora-play-vX.Y.Z` artifact for upload in Play Console (see "Google Play").
- It refuses to run unless the commit is on `prod` and the tag matches `audora.versionName`.
- The key lives in the GitHub environment **release**, which only `prod` and `v*` tags may use. A workflow
  changed on any other branch cannot read it. The build job that holds the key has read-only access to the
  repo; a separate job with no access to the key publishes the release.
- The release key was created on the maintainer's PC and exists in exactly three places: `D:\AUDORA\signing`,
  two offline backups, and the GitHub environment secrets. Never in the repo, chat, or a shared folder.

> **If the key is lost, nobody can update Audora.** Android only installs an update signed with the same key.
> Friends would have to uninstall the app (losing their library and reading positions) and install a new one.
> The backups in step 4 are the only protection against this.

## One-time setup

### 1. Create the "release" environment on GitHub

1. Open https://github.com/skm0078/audora/settings/environments
2. Click **New environment**, type `release`, click **Configure environment**.
3. Under **Deployment branches and tags**, open the dropdown that says *No restriction* and pick
   **Selected branches and tags**.
4. Click **Add deployment branch or tag rule**. Ref type: **Branch**. Name pattern: `prod`. Click **Add rule**.
5. Click **Add deployment branch or tag rule** again. Ref type: **Tag**. Name pattern: `v*`. Click **Add rule**.
6. Leave everything else as it is. Keep this page open for step 2.

### 2. Create the key and copy it into the environment

This needs `keytool`, which comes with any Java JDK. Android Studio is not needed.

1. If you have never installed Java: open PowerShell and run
   `winget install EclipseAdoptium.Temurin.21.JDK`, then close and reopen PowerShell.
2. Get the script: open
   https://github.com/skm0078/audora/blob/dev/scripts/release/new-release-key.ps1 and click the
   **Download raw file** button (the down-arrow icon above the code, on the right). Save it in `D:\AUDORA`.
3. In PowerShell run:
   ```
   cd D:\AUDORA
   powershell -ExecutionPolicy Bypass -File .\new-release-key.ps1
   ```
4. The script creates `D:\AUDORA\signing\audora-release.p12` and `audora-release-passwords.txt`, then copies
   four values to the clipboard one at a time. For each one, on the `release` environment page from step 1:
   click **Add environment secret**, type the **Name** the script shows, click into **Value**, press **Ctrl+V**,
   click **Add secret**, then press Enter in PowerShell.
5. Delete the downloaded `D:\AUDORA\new-release-key.ps1` (it holds no secrets; this is just tidiness).

If a paste went wrong, run the script again: it never replaces an existing key, it only repeats the copying.
If you use Windows clipboard history (Win+V), clear it afterwards (Win+V → **Clear all**).

### 3. Allow merge commits into prod

The "protect dev and prod" ruleset currently allows only squash and rebase merges. Promoting dev to prod needs
a merge commit (see "Cutting a release"), so add it once:

1. Open https://github.com/skm0078/audora/settings/rules and click **protect dev and prod**.
2. Scroll to **Require a pull request before merging** and expand **Additional settings**.
3. Under **Allowed merge methods**, tick **Merge** (leave Squash and Rebase ticked).
4. Scroll to the bottom and click **Save changes**.

Ticket PRs into `dev` keep using squash or rebase; the merge option is just for dev → prod.

### 4. Back up the key (do not skip)

Copy the whole `D:\AUDORA\signing` folder to **two** places that are not this PC, for example two USB sticks,
or one USB stick and an encrypted password manager entry (attach the `.p12` file and the password).
Do not put it in a synced cloud folder, the repo, or the project's shared files.

## Cutting a release

1. **Version.** Make sure `audora.versionName` in `gradle.properties` on `dev` is the version you want to
   release and is higher than the last release. Changing it is a normal ticket PR into `dev`.
2. **Promote dev to prod.** Open https://github.com/skm0078/audora/compare/prod...dev → **Create pull request**
   → wait for the green check → merge with **Create a merge commit**. A merge commit keeps `prod` and `dev`
   sharing history; squashing here would make later dev → prod PRs conflict.
3. **Run the release.** Open https://github.com/skm0078/audora/actions/workflows/release.yml → **Run workflow**
   → under *Use workflow from* pick **prod** → **Run workflow**. It takes about 10 minutes.
4. The release appears at https://github.com/skm0078/audora/releases/latest with the APK attached.

Pushing a tag `vX.Y.Z` that points at a commit on `prod` does the same as step 3, for anyone using git.

## Installing and updating with Obtainium

The friend-facing steps (Obtainium, installing without it, the Play Protect prompt, first run) are in the
[README](../README.md#install-the-easy-way-obtainium). Obtainium follows real releases only: `dev-build`, `voices`
and `s1-voices` are pre-releases, which it ignores with its default settings, and each release carries exactly one
APK, so no asset filter is needed. Keep it that way: a second `.apk` on a release would make Obtainium ask which one
to install.

The release app (`io.github.skm0078.audora`) and the dev build (`io.github.skm0078.audora.debug`) install side
by side.

## Dev build signing key

Debug and dev APKs (the `dev-build` release, `audora-dev.apk` being the optimised `dev` build type of ADR-072, and every branch's artifact) are signed with the dev key when the repository
secret `AUDORA_DEBUG_KEYSTORE_B64` exists, so a newer dev build installs over an older one (ADR-031). Without the
secret, each CI run signs with a throwaway key, as Android tooling does by default.

- The secret is the base64 of a PKCS12 keystore with store and key password `android` and alias `androiddebugkey`
  (Android's debug-key convention; the password is not secret). It is a repository secret, not in the `release`
  environment, so every branch's build can use it.
- CI logs the dev key's certificate SHA-256 and fails the "Debug signing certificate" step if the debug APK was
  signed with anything else.
- It only signs `io.github.skm0078.audora.debug`, which nobody registers or distributes, so losing it costs one
  uninstall of the test build. Never use it for release builds.

### Add the dev key (once, about 5 minutes)

1. Open PowerShell (Java is already installed from the release-key setup) and create the key next to the release key:
   ```
   keytool -genkeypair -keystore D:\AUDORA\signing\audora-dev-debug.p12 -storetype PKCS12 -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10950 -dname "CN=Audora Dev Build,O=Audora,C=IN"
   ```
2. Copy it to the clipboard as one line of text:
   ```
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("D:\AUDORA\signing\audora-dev-debug.p12")) | Set-Clipboard
   ```
3. Open https://github.com/skm0078/audora/settings/secrets/actions and click **New repository secret** (under
   *Repository secrets*, not *Environment secrets*).
4. **Name:** `AUDORA_DEBUG_KEYSTORE_B64`. Click into **Secret**, press **Ctrl+V**, click **Add secret**.
5. On the phone, uninstall the old dev build once. From the next dev build on, each one installs over the last.

To replace the key, repeat with a new file name and update the secret; the next dev build then needs one uninstall.

## Pinned certificate

The release certificate's SHA-256 is pinned as `EXPECTED_CERT_SHA256` in `.github/workflows/release.yml`:
`a4a558acec075b159872c52a91bd6873b16fa340ba40eafd2608888e0ecef8ea` (also in the release notes and in
`audora-release-passwords.txt`). A build signed with any other key fails before it is published, instead of
reaching phones as an APK that cannot update the installed app. Only a deliberate key change updates the pin.

## Developer verification (Android Developer Console)

Audora's package `io.github.skm0078.audora` is registered with the release certificate above (status
*Registered*, key *Verified*, 2026-10-16) on a free **limited distribution** account in the Android Developer
Console (https://android.google.com/developerconsole). That account lets Audora install on up to **20 authorized
devices** once Google enforces developer verification where the phone is (India: 2027, no date yet). Full
distribution would cost a one-time $25 plus ID, so it is not used. A limited account cannot be upgraded in place.

**Add a friend's phone** (when you give them Audora):
1. Console → **Devices** → add a device and type a name you will recognize (e.g. "Riya, Pixel 8").
2. Your own phone: scan the QR code with it. A friend's phone: send them the link shown. Links expire after 7 days.
3. They open the link on that phone, confirm they know you, and send back the code the phone shows.
4. Enter the code and tap **Add device**.

At 20 devices, remove one before adding another. Removing is permanent for that device: it can no longer install or
update Audora until it is added again.

**Key changes:** a new release key must be added to the package (**Add key**, then the console's ownership
check: a release APK signed with that key and carrying the console's snippet in `assets/adi-registration.properties`)
before phones can install builds signed with it. Without the old key there is no way back, which is one more reason
to keep the backups in step 4.

The dev build (`io.github.skm0078.audora.debug`) is not registered. On a phone where verification is enforced it
installs with `adb install` or after Android's one-time "advanced flow" (Developer options, a one-day wait).

## Google Play

ListenLock is published on Google Play from a personal Play Console account (developer name TelosMind Labs, contact
`support@telosmindlabs.com`; never a personal address). Play installs
and GitHub/Obtainium installs carry the **same signature**, so either one updates the other and nobody loses their
library when they switch.

- **Play App Signing uses this release key.** Before the first upload, Play Console → the app → Test and release →
  App integrity → change the app signing key → *Export and upload a key from Java keystore*, using the `pepk.jar`
  command the console shows, run in `D:\AUDORA\signing`. The same key is the upload key. A separate upload key
  would add a second secret without removing the signing key from CI, where the APK job already needs it.
- **Getting the bundle:** after a Release run, open it under
  https://github.com/skm0078/audora/actions/workflows/release.yml, scroll to **Artifacts**, download
  `audora-play-vX.Y.Z`, unzip it, and upload the `.aab` in Play Console. Artifacts are kept for 90 days. Play refuses
  a versionCode it has already seen, so every upload needs a new `audora.versionName`.
- **Language splits are off** (`bundle.language.enableSplit = false` in `app/build.gradle.kts`): the in-app
  language switch needs the Hindi and Bengali strings on every phone.
- **Privacy policy:** `docs/privacy.html`, served by GitHub Pages at https://listenlock.telosmindlabs.com/privacy.html
  and linked from Settings → Privacy policy (`SiteConfig`). Pages setup (once): https://github.com/skm0078/audora/settings/pages →
  *Source* **Deploy from a branch** → branch **prod**, folder **/docs** → **Save**. The custom domain comes from
  `docs/CNAME` when it reaches `prod`: never type it in that settings page first, because the box commits straight to
  `prod`. After the release that brings it, tick **Enforce HTTPS** there. DNS (Cloudflare, `telosmindlabs.com`): CNAME
  `listenlock` → `skm0078.github.io`, DNS only; the domain is verified for the account at
  https://github.com/settings/pages, so nobody else can claim it (ADR 2026-10-30-company-domain). Pages builds the site with its
  built-in Jekyll: the hand-written pages are copied as they are, recipes and blog posts are built from Markdown
  (docs/CONTENT.md), and the developer docs are left out by `docs/_config.yml`. Update the date on the page with every change,
  and update Play Console's Data safety form in the same release if what Audora sends or keeps changes.
- **Data safety form** (answers from the audit in ADR-061): *Does your app collect or share any of the required user
  data types?* **No**. The app sends nothing about the person or their books anywhere; every network request is one
  the person starts (a voice or language pack from GitHub, a web page they ask it to read, a book from a curated free
  catalogue) and carries only the address, the app's name and the file types it reads. No account, so no deletion
  request flow. Re-check ADR-061's table before every release: a new dependency, a new network call or a new upload
  of any kind makes this answer wrong.
- **Store listing:** the text in English, Hindi and Bengali, the graphics and the category are in
  [store/listing.md](store/listing.md). Change the file first, then paste it into Play Console, so the listing is
  reviewed like code.

### Play Console app content, once

Play Console → the app → Policy and programs → App content. Answers for v0.1.0; re-check them when a feature changes
what they describe.

| Declaration | Answer | Why |
| --- | --- | --- |
| Privacy policy | `https://listenlock.telosmindlabs.com/privacy.html` | `SiteConfig.PRIVACY_PAGE`; live once the release that carries `docs/CNAME` is on `prod` |
| Ads | No ads | None in any module |
| App access | All functionality is available without special access | No account or login |
| Content rating | Category *Reference, News, or Educational*; every content question **No**; users interact or share content in the app **No** (sharing goes through Android's share sheet to an app the person picks); shares location **No**; digital purchases **No** | The app shows only books the person opens; the free catalogues and web pages are content the person chooses |
| Target audience | 13 and over (13–15, 16–17, 18+); not appealing to children | Keeps the app out of the Families policy; the listing does not advertise Kids mode, which is for a parent to hand over a locked-down phone |
| News app | No | |
| Data safety | See the bullet above | ADR-061 |
| Government, financial, health | No to each | |
| Foreground service types | The table below, one short video each | Android 14+ requires a declared type; Play reviews each |

**Foreground service declarations.** For each type: what the person does that starts it, why it cannot wait or be
deferred, and a video (unlisted YouTube link, under 1 minute) that shows the person starting it and the notification.

| Type | Services | What the person does | Video shows |
| --- | --- | --- | --- |
| `mediaPlayback` | `AudoraPlaybackService`, `MadeAudioService` | Taps Play on a book or on a saved audiobook, then locks the phone or opens another app | Play, lock the phone, audio continues, lock-screen controls pause and resume |
| `mediaProcessing` | `AudiobookExportService`, `StudioService`, `BookCopyService`, `ClipService`, `QuoteVideoService` | Taps Save as audiobook, Studio mode, Accessible copy, Share as audio or Share as a video | Start Save as audiobook, leave the app, the progress notification counts up, the file appears when done |
| `dataSync` | The same five services | The same: each writes the rendered audio or book to a file the person keeps or shares | The same video, ending on the saved file in Files or the share sheet |
| `microphone` | `VoiceCommandService` (opt-in) | Turns on screen-off voice commands in the reader, then locks the phone | Turn it on, lock the phone, say "ListenLock, pause", playback pauses |

## When something fails

| Error in the Release run | Meaning | Fix |
|---|---|---|
| `Run this workflow from the prod branch` | *Use workflow from* was not `prod` | Run again and pick `prod` |
| `Tag vX.Y.Z already exists` | This version was already released | Bump `audora.versionName` via dev → prod |
| `Commit ... is not on prod` | A tag was pushed on a commit outside `prod` | Delete the tag; release from `prod` |
| `Missing secrets in the 'release' environment` | Step 2 incomplete | Run the key script again (it reuses the key) |
| Job waits or fails with a deployment protection message | The environment rules from step 1 do not allow this branch or tag | Check the two rules in step 1 |
| dev → prod PR shows conflicts in files nobody changed on prod | A previous dev → prod PR was squashed, so prod's commit is not in dev's history | On a branch from dev run `git merge -s ours origin/prod` (keeps dev's files, records prod as merged), push it, open a PR from that branch into **prod** (not dev) and merge it with **Create a merge commit**. Later dev → prod PRs merge cleanly |
| `AUDORA_DEBUG_KEYSTORE_B64 is not a keystore ...` (Android CI) | The secret was cut short or is another file | Repeat steps 2 to 4 of "Add the dev key" |
| `Debug APK signed with ..., not with the dev key ...` (Android CI) | The build did not pick up the restored keystore | Check the "Stable debug signing key" step's log; the keystore path may have moved in a new AGP |
| `Signed with certificate ... but releases must use ...` | The secrets hold a different key | Restore the original key from backup and re-add the secrets |
| `Bundle signed with certificate ...` | The bundle was signed with another key than the APK | Same fix as the line above; Play would refuse this upload |

## If the key is lost or leaked

- **Lost, with a backup:** copy the backup to `D:\AUDORA\signing` and run the key script to refill the secrets.
- **Lost, no backup:** create a new key with the script, pin the new certificate, and tell
  friends to uninstall and reinstall. Their app data is lost.
- **Leaked** (someone else may have the `.p12` and password): someone could sign an APK that updates
  friends' installs. Remove the secrets from GitHub, tell friends not to install updates from anywhere but the
  official release page, and plan a key rotation (`apksigner rotate`) before the next release.
