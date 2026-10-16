# Releasing Audora

How a signed APK gets from `prod` to friends' phones, and how the signing key is kept (ticket P0-02, Build Spec §7).

## How it works

- `gradle.properties` holds `audora.versionName` (for example `0.0.1`). It is the only version number: the
  Android `versionCode` is derived from it (`MAJOR*1000000 + MINOR*1000 + PATCH`), so a newer release always
  updates an older one.
- `.github/workflows/release.yml` builds `:app:assembleRelease`, signs it with the release key, checks
  16 KB alignment and the signature, and publishes `audora-vX.Y.Z-arm64.apk` plus `SHA256SUMS.txt` to
  GitHub Releases under the tag `vX.Y.Z`.
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

## Pinned certificate

The release certificate's SHA-256 is pinned as `EXPECTED_CERT_SHA256` in `.github/workflows/release.yml`:
`a4a558acec075b159872c52a91bd6873b16fa340ba40eafd2608888e0ecef8ea` (also in the release notes and in
`audora-release-passwords.txt`). A build signed with any other key fails before it is published, instead of
reaching phones as an APK that cannot update the installed app. Only a deliberate key change updates the pin.

## When something fails

| Error in the Release run | Meaning | Fix |
|---|---|---|
| `Run this workflow from the prod branch` | *Use workflow from* was not `prod` | Run again and pick `prod` |
| `Tag vX.Y.Z already exists` | This version was already released | Bump `audora.versionName` via dev → prod |
| `Commit ... is not on prod` | A tag was pushed on a commit outside `prod` | Delete the tag; release from `prod` |
| `Missing secrets in the 'release' environment` | Step 2 incomplete | Run the key script again (it reuses the key) |
| Job waits or fails with a deployment protection message | The environment rules from step 1 do not allow this branch or tag | Check the two rules in step 1 |
| dev → prod PR shows conflicts in files nobody changed on prod | A previous dev → prod PR was squashed, so prod's commit is not in dev's history | On a branch from dev run `git merge -s ours origin/prod` (keeps dev's files, records prod as merged), push it, open a PR from that branch into **prod** (not dev) and merge it with **Create a merge commit**. Later dev → prod PRs merge cleanly |
| `Signed with certificate ... but releases must use ...` | The secrets hold a different key | Restore the original key from backup and re-add the secrets |

## If the key is lost or leaked

- **Lost, with a backup:** copy the backup to `D:\AUDORA\signing` and run the key script to refill the secrets.
- **Lost, no backup:** create a new key with the script, pin the new certificate, and tell
  friends to uninstall and reinstall. Their app data is lost.
- **Leaked** (someone else may have the `.p12` and password): someone could sign an APK that updates
  friends' installs. Remove the secrets from GitHub, tell friends not to install updates from anywhere but the
  official release page, and plan a key rotation (`apksigner rotate`) before the next release.
