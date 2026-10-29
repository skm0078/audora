# A side drawer on phones, and Settings by name (2026-10-28, ADR 2026-10-28-phone-drawer)
- The library's new menu button (top left) opens a side drawer: shelves with their counts, your collections, Made by
  you (audiobooks and clips, listening stats), Show me around and Settings. The bottom bar stays.
- Settings is easy to find: the You page is now headed "Settings", and the drawer has a Settings row.
- "What's new" entry: `app/src/main/assets/whatsnew/2026-10-28-phone-drawer.txt`. `:pdf` unchanged. No new
  dependency.
- **Cost:** four small icons; the closed drawer stays composed off screen (a dozen rows). APK about 6 KB.
- **Could affect:** the library's top bar (a button at the start), the app shell on phones, the You page's heading.
- **Verified in CI:** `PhoneDrawerTest`, `AppShellTest`, release journey step 45.

**Real-device checks (the test phone):** tap the menu button: the drawer slides in over the bar. Tap Finished: the
drawer closes and the library shows finished books. Open it, tap Listening stats: the drawer closes first, then the
stats sheet opens. Tap Settings: the Settings page opens. Back with the drawer open closes it. Swipe from the left
edge with it closed: nothing opens. TalkBack: the menu button says "Open menu"; closed, the drawer's rows are not read.
