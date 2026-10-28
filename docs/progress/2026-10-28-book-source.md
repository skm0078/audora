# Drive books are marked, and can be kept on this phone (2026-10-28, ADR 2026-10-28-book-source)
- The library says where a book lives: a cloud icon and "Google Drive", "OneDrive", "Dropbox" or "From another app"
  on rows and cover tiles (phone books stay unlabelled), and "Where: …" in every book's details. Read from the
  document URI's authority only (`BookSources`), so nothing is stored and existing books are covered.
- "Keep on this phone" in a cloud book's details (and in search) copies it into ListenLock's own files and moves the
  book there, keeping its place, notes and studio audio; the Drive grant is given back.
- "What's new" entry: `app/src/main/assets/whatsnew/2026-10-28-book-source.txt`. `:pdf` not changed. No new dependency.
- **Could affect:** library rows and tiles (one more line or a corner badge on cloud books), the book details sheet,
  action search (one more book action), shared copies (a kept book is one), backup restore of such copies.
- **Verified in CI:** `BookSourceTest` (authorities, work-profile URIs, case), `KeepOnPhoneTest` (kept, already on the
  phone, no copy, copy refused, book removed mid-copy), `BookSourceActionTest`, `BookMenuChoicesTest`;
  emulator `BookSourceScreensTest` (row, tile description, details, Keep tapped; screenshots `book-source-*`);
  release journey step 42 (a phone book's details say "On this phone", no copy offered).

**Real-device checks (the test phone):** add a book from Google Drive and one from Downloads: only the Drive one shows
the cloud and "Google Drive" in the list and grid. Open the Drive book's details: "Where: Google Drive" and
"Keep on this phone". Tap it: a toast says it is copying, then that it is on this phone; the cloud mark goes, the
book opens in airplane mode at the same sentence, and its bookmarks are still there. Try TalkBack on its tile.
