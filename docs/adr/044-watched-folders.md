# ADR-044: Watched folders

**Status:** Accepted (2026-10-17). Plan item 26.

## Context
Listeners who keep their books in one folder (Downloads, a Telegram or WhatsApp documents folder, a synced Drive
folder) want new books there to appear in Audora without adding each one. Audora has no storage permission and must
not get one (`MANAGE_EXTERNAL_STORAGE` is refused for apps like this and reads everything); it opens books through the
Storage Access Framework with a lasting grant per document. WorkManager is kept for downloads (AGENTS.md), and nothing
may run while the app is closed unless the listener started it (C7).

## Decision
1. **The listener picks folders** in the library's ⋮ menu (*Watched folders*, `OpenDocumentTree`). Audora takes a
   lasting read grant on the folder (tree URI) and keeps the list in `filesDir/watched-folders.txt` (`WatchedFolderStore`,
   written whole: temp file, then rename).
2. **Scan when the library opens**, not in the background: each time the library screen is shown, and right after a
   folder is added, `WatchedFolders.scan` lists every watched folder breadth first (`FolderWalker`: 4 levels, 5,000
   entries per scan, 50 new books per scan; `WatchedFolderConfig`) and adds files whose type is PDF or EPUB or whose
   name ends in `.pdf` or `.epub`. Text files are left out: book folders hold READMEs and notes. A toast says how
   many books joined.
3. **Each file is added once.** A folder remembers every book file it has seen; a book the listener removes from the
   library stays seen and is not added back. A file that is not a book stays seen; one that cannot be read now (a
   cloud file offline) is tried again next time. Books are added through `Library.add`, so duplicates by fingerprint,
   titles and covers behave as for a picked file.
4. **Books are read through the folder's grant.** A book found in a folder keeps its tree-based document URI, so it
   needs no grant of its own; `SafDocumentAccess` treats a document under a granted folder as accessible (keep,
   release and availability). Removing a folder keeps its books readable: the folder's grant stays while any library
   book lives under it, and each scan gives back folder grants that neither a watched folder nor a book needs.
5. **Logs** name the provider's authority and counts, never folder or file names or URIs.

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
