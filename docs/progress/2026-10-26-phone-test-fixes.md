# Four phone-test bugs: sample crash, background warning, phone maker on screen, privacy link (2026-10-26)
- **Sample crash (root cause found on CI):** `MarkdownParser` had one pattern with a bare `}`
  (`\s*\{#[\w-]+}\s*$`). The desktop JVM accepts it, Android's ICU regex engine does not, so the parser's class
  failed to load on the phone and every text book crashed the app when opened: "Try a sample", shared text and any
  `.txt`/`.md` file. Unit tests passed because they run on the JVM. Fixed by escaping the brace; the other `Regex`
  literals in the main sources were scanned and none has a bare brace.
- **Background warning stayed after allowing:** on Android 14+ "Allow background usage" on leaves the app
  **Optimized**; only **Unrestricted** makes `isIgnoringBatteryOptimizations` true, and the app showed no
  difference. New `BackgroundAccess` (core) reads both Android answers (with `ActivityManager.isBackgroundRestricted`):
  Restricted, Optimized, Unrestricted. Optimized now says so and its button opens App info, where Unrestricted is
  picked. Changes are logged (`Audora.Battery`, "Background access: A -> B").
- **Phone maker on screen:** the battery steps heading ("On your Motorola phone", from `Build.MANUFACTURER`) is
  now "Steps for this phone" in en/hi/bn; the brand still picks the steps in code. A test fixture and a comment that
  named a test phone are generic now.
- **Privacy policy 404:** no code change. The link is right; GitHub Pages is not serving `docs/privacy.html` yet
  (prod has no copy). Pages setup is in `docs/RELEASING.md`.
- "What's new" entry: `app/src/main/assets/whatsnew/2026-10-26-background-optimized.txt`. `:pdf` not changed. No
  new dependency.
- **Could affect:** opening text books (Markdown heading ids `{#id}` are still removed), the battery screen, the
  first-run background step, the library's background card.
- **Verified in CI:** `MarkdownParserDeviceTest` (patterns compile and parse on the emulator), `ReleaseJourneyTest`
  step 01 (plays a voice sample on first run, then Try a sample opens the reader), `FirstRunScreenTest`
  (Optimized is not done, Unrestricted is), `BackgroundAccessTest`, `ListenLockNameTest.noShownTextNamesAPhone`.

**Real-device checks (the test phone):** fresh install, tap Try a sample on the last welcome screen: the reader opens
and plays. Settings, Background reading: with Allow background usage on but Optimized, the card says Optimized; pick
Unrestricted, come back, it says allowed.
