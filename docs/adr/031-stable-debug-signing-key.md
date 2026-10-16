# ADR-031: One signing key for dev builds, from a repository secret

**Status:** Accepted (2026-10-16).

## Context
GitHub runners start empty, so the Android Gradle plugin creates a new `~/.android/debug.keystore` in every CI run.
Every debug APK, including the `dev-build` pre-release that the maintainer and testers install, was therefore signed
with a different key (the published APK's certificate is dated inside the run that built it). Android refuses an
update signed with another key, so each new dev build needed an uninstall first, losing the library and reading
positions, which costs time in every phone check.

## Decision
1. CI restores a fixed debug keystore from the repository secret `AUDORA_DEBUG_KEYSTORE_B64` (base64 of a PKCS12
   keystore) to AGP's default location before building. AGP then signs all debug APKs with it; no Gradle change.
2. The keystore follows Android's debug convention: store and key password `android`, alias `androiddebugkey`.
   The password is not secret, so one secret is enough.
3. CI reads the certificate SHA-256 from the restored keystore and fails if the debug APK carries another one, so a
   bad paste or a silently regenerated key never publishes an un-updatable dev build. No pin in the repo: the
   maintainer makes the key on their own PC and it never passes through anyone else.
4. Without the secret (not added yet, or a fork) CI behaves exactly as before.

## Alternatives considered
- **Commit the debug keystore:** simplest and common in open-source apps, but AGENTS.md forbids committing keystores
  and anyone could then sign an update to installed test builds. Rejected.
- **Sign debug builds with the release key:** would make test builds able to replace the friends' app if the package
  suffix were ever dropped, and would expose the release key to every branch. Rejected.
- **Put the secret in the `release` environment:** only `prod` and tags could read it, so branch builds would still
  differ. Rejected; a repository secret is right for a key that guards only the `.debug` package.

## Consequences
- Dev builds update in place once the secret is added; the first install after that still needs one uninstall.
- Anyone with write access to the repo can sign as the dev key; acceptable, since it signs only
  `io.github.skm0078.audora.debug`, which is never distributed or registered for developer verification.
- Losing the key costs one uninstall of the test build; replacing it is a new secret (`docs/RELEASING.md`).
