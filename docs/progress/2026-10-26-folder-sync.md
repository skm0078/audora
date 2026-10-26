# Sync through a folder (2026-10-26, ADR 2026-10-26-folder-sync)
- **Sync through a folder** (Library ⋮): the listener picks a folder (OpenDocumentTree, lasting read and write
  grant), typically one a cloud app (Drive, Dropbox, OneDrive) or Syncthing keeps the same on every device, and picks
  the same folder on the other devices. ListenLock writes "ListenLock sync/device-<random id>.txt" (places, bookmarks,
  highlights and notes by book fingerprint) and, when "Copy books into the folder" is on, "ListenLock sync/Books".
- Each sync: copy new books in, read the other devices' files, newest place wins, bookmarks and highlights are
  joined, a mark removed on one device is removed on the others (remembered 180 days), books another device copied in
  are added here (unless removed from this library), then this device's file is written again.
- Runs when the library opens and when the listener leaves the app (at most once a minute), and on Sync now. No
  server, no account, no network use by ListenLock; the folder's app moves the files.
- Privacy policy: a bullet under "Your files, your responsibility". The sheet itself says what is written there.
- "What's new" entry: `2026-10-26-folder-sync`. No `:pdf` change, PIPELINE_VERSION unchanged.
- **Could affect:** the library ⋮ menu, watched folders (a book added from the sync folder reads through its grant,
  like a watched folder's), the library opening (one sync when a folder is picked), the release build.
- **Verified in CI:** `SyncFileTest`, `SyncMergeTest`, `SyncSettingsTest`, `SyncBookFilesTest` (unit),
  `FolderSyncTest` (device: two devices' syncs through one folder, books, places, marks and removals) and
  `FolderSyncScreenTest` (device: the menu entry opens the sheet), `ReleaseJourneyTest`.

**Real-device checks (the test phone and a second device or emulator, one cloud app on both):**
1. On the test phone: Library ⋮ > Sync through a folder > Pick a folder > a new folder inside the cloud app's
   storage (or Download/ListenLock sync for a USB test). "Synced" shows; the folder now holds "ListenLock sync".
2. Read a book a few pages in, add a bookmark and a highlight, press Home. Let the cloud app upload.
3. On the second device: pick the same folder. The book is added from the folder; open it: it resumes at the same
   sentence, with the bookmark and the highlight.
4. Delete the bookmark on the second device, press Home, wait for the upload; open the library on the test phone:
   the bookmark is gone there too.
5. Stop syncing: the folder and its files stay; the books added from it still open.
