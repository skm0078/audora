# ADR-044: Watched folders

**Status:** Accepted (2026-10-17). Plan item 26.

## Context
Listeners who keep their books in one folder (a folder inside Download, a Telegram or WhatsApp documents folder, a synced Drive
folder) want new books there to appear in Audora without adding each one. Audora has no storage permission and must
not get one (`MANAGE_EXTERNAL_STORAGE` is refused for apps like this and reads everything); it opens books through the
Storage Access Framework with a lasting grant per document. WorkManager is kept for downloads (AGENTS.md), and nothing
may run while the app is closed unless the listener started it (C7).

## Decision
1. **The listener picks folders** in the library's ⋮ menu (*Watched folders*, `OpenDocumentTree`). Audora takes a
   lasting read grant on the folder (tree URI) and keeps the list in `filesDir/watched-folders.txt` (`WatchedFolderStore`,
   written whole: temp file, then rename).
2. **Scan when the library opens**, not in the background: when the library screen is shown (at most every 3
   minutes, so returning to the tab or rotating the phone does not rescan) and always right after a folder is added,
   `WatchedFolders.scan` lists every watched folder breadth first (`FolderWalker`: 4 levels, 5,000 entries per scan,
   logged when the cap stops it; `WatchedFolderConfig`) and adds files whose type is PDF or EPUB or whose name ends
   in `.pdf` or `.epub`. Hidden entries (a leading "."), such as MediaStore's `.trashed-` and `.pending-` copies,
   are skipped, so a trashed book does not come back and a download in progress is not added twice. Text files are
   left out: book folders hold READMEs and notes. A toast says how many books joined. Android's folder picker cannot
   grant the Download folder itself, the storage root or `Android/data` (API 30+), so the listener picks a folder
   inside Download.
3. **Each file is added once** (`FolderScan`). A folder remembers every book file it has handled; a book the
   listener removes from the library stays seen and is not added back, also when the folder is removed and watched
   again (its record is kept, marked not watched). A file that fails to add (offline, still downloading, empty, not
   a book) is retried after 1 hour, doubling up to a week, and every file tried counts toward the 50 per scan, so
   failing cloud files cannot make each open slow. After a walk that listed everything, files no longer in the
   folder are forgotten, so a new book saved under a removed file's name joins. Books are added through
   `Library.add`, so duplicates by fingerprint, titles and covers behave as for a picked file.
4. **Books are read through the folder's grant.** A book found in a folder keeps its tree-based document URI, so it
   needs no grant of its own; `SafDocumentAccess` treats a document under a granted folder as accessible (keep,
   release and availability). Removing a folder keeps its books readable: the folder's grant stays while any library
   book lives under it. Audora records which folder grants it took (`granted`) and gives back only those, once the
   folder is not watched and no library book needs it; other grants are never touched, and nothing is given back
   when the record cannot be read.
5. **Logs** name the provider's authority and counts, never folder or file names or URIs.
6. **Unreachable folders ask to be picked again.** A watched folder without a lasting grant (revoked, its app
   uninstalled, or restored from a backup on a new phone, ADR-046) or whose listing fails shows as unreachable in
   the list with *Pick again*, and a toast says so after a scan. Picking the same folder again restores its record,
   so books already in the library under it become readable again and nothing removed comes back.
7. **Provider failures never crash the library.** Lost grants, gone folders and providers crashing mid-call reach
   the app as several runtime exceptions; each becomes an IOException in the adapter, and the folder is skipped.

## Alternatives considered
- **A periodic WorkManager or JobScheduler scan:** books would appear without opening the app, but it is background
  work the listener did not start, costs battery on a schedule, and AGENTS.md keeps WorkManager for downloads.
  Rejected; opening the library is when a new book matters.
- **`ContentObserver` on the folder:** only works while the app runs and few providers notify for tree children.
  Rejected.
- **`MANAGE_EXTERNAL_STORAGE` or `READ_MEDIA_*`:** broad access to every file, refused for this kind of app, and
  `READ_MEDIA_*` does not cover documents. Rejected.
- **Including `.txt` files:** Audora reads them (ADR-042), but they are rarely books in such folders. Rejected; a text
  book can still be picked by hand.

## Consequences
- Each library open lists the watched folders: a few hundred milliseconds for a folder of a few hundred files on the
  phone's storage; longer for cloud providers, bounded by the entry limit. It runs off the main thread and never
  blocks the list.
- A book moved out of a watched folder shows as missing, like any moved file, until the listener locates it.
- Android allows an app 512 lasting grants; each watched folder uses one, however many books it holds.
- A folder in a provider that gives no lasting grant cannot be watched; the listener is told.
