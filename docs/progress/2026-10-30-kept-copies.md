# Past 500 single-file books, new books are kept as copies so old ones keep opening (2026-10-30, no ADR)
- Edge-case pass, gap E5. Android lets an app hold 512 lasting file grants and silently drops the oldest past that,
  so the oldest single-file book stopped opening ("access lost") with no warning.
- `SafDocumentAccess.keepAccess` now refuses a new grant once the app holds `LibraryConfig.maxKeptFileGrants` (500;
  the gap leaves room for watched-folder and folder-sync grants, which count against the same 512) with
  `DocumentError.GrantsFull` (`LibraryError.GrantsFull`). A file already granted, Audora's own copy and a file in a
  watched folder need no new grant and are never refused.
- `LibraryController` adds such a file through `importShared` (Audora's own copy, `SharedPdfCopies`), for a picked file,
  a shared file and a missing book found again alike. A picked file whose app lends it only for a moment is now kept
  the same way instead of being refused. The "Added" snackbar says a copy is kept, as it takes phone space. No count,
  limit or warning is shown before that point. Only a copy that fails (most likely a full phone) asks the reader to
  free space or remove a finished book.
- No silent limit: the library has no cap on books. Books in watched folders are not counted.
- Perf cost: one extra binder call (`persistedUriPermissions`) per add, none per open. Past the limit each new
  single-file book takes its size again in app storage (shown on the Storage screen; removing the book deletes it).
- "What's new" entry: `app/src/main/assets/whatsnew/2026-10-30-kept-copies.txt`. `:pdf` unchanged. No new dependency.
- **Could affect:** adding a book from the picker, "Open with ListenLock", finding a missing book again; the snackbar
  after an add.
- **Verified in CI:** `LibraryControllerSharedTest` (past the limit and lent-for-a-moment picks are added as copies and
  say so, a grant-kept add says nothing, a failed copy reports `GrantsFull`, a found-again book points at a copy), device
  test `GrantLimitDeviceTest` (at the limit no grant is taken; Audora's copy needs none; the limit is under Android's).
  No new journey step: an emulator run cannot hold 500 grants in reasonable time.

**Real-device checks (the test phone):** add a PDF from Downloads: the snackbar says "Added", with no copy. Share a PDF
from a chat app: it opens and the snackbar says a copy is kept. The 500 limit itself is covered by the tests.
