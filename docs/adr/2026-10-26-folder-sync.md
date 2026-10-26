# ADR 2026-10-26-folder-sync: sync through a folder the listener picks

**Status:** Accepted (2026-10-26). Competitor gap CF-16; the last Kotlin feature before the feature freeze.

## Context
Listeners with a phone and a tablet (or two phones) want the same place, bookmarks and highlights on both, as Moon+
Reader and Librera offer. ListenLock has no server and no accounts, and online features belong to the paid Flutter
app; the Kotlin app must stay offline and never upload anything itself (AGENTS.md, privacy policy).

## Decision
Sync through a folder the listener picks with the Storage Access Framework (OpenDocumentTree, a lasting read and
write grant). Getting the folder to the other devices is the job of whatever app owns it: Google Drive, Dropbox,
OneDrive, Syncthing, or a USB stick carried by hand. ListenLock only reads and writes files in it.

1. **Layout.** `ListenLock sync/device-<random id>.txt` per device (the id is random per install, never the device's
   name) and, when "Copy books into the folder" is on (default), `ListenLock sync/Books/<title> [<tag>].<ext>`, the tag
   cut from the book's fingerprint so every device finds the same book whatever title it gave it. Books over 200 MB are
   not copied (`FolderSyncConfig`).
2. **Format.** Plain text, a record per line, tab-separated, escaped (`SyncFile`); a header with a version, so a newer
   format is skipped rather than half-read. Each device writes only its own file: two devices never write the same
   file, so a cloud app's "conflict copy" cannot happen to a sync file.
3. **Merge** (`SyncMerge`, pure, in `:core`). Books are matched by content fingerprint, since each device gives a book
   its own id. Newest place wins (by save time). Bookmarks and highlights are named by their place and creation time,
   so the same mark is the same everywhere; marks are joined. A mark this device had at its last sync and no longer
   has was removed here; the removal is written and other devices remove it too, remembered 180 days. A book removed
   from one library is never removed elsewhere, and is not added back to that library from the folder.
4. **When.** When the library opens and when the listener leaves the app, at most once a minute, and on Sync now. No
   background job: nothing runs while ListenLock is closed.
5. **Adding books from the folder** reads them through the folder's grant, like watched folders (ADR-044). A copy
   whose content is not the book it names (still arriving from the cloud) is removed again and tried next sync.

## Alternatives considered
- **Our own server or a cloud API (Drive REST, Dropbox API).** Needs accounts, keys and network code in the Kotlin
  app, against the offline promise. Belongs to the Flutter Pro app if anywhere.
- **One shared file for all devices.** Two devices writing it at once makes cloud apps keep conflict copies and lose
  changes. One file per device has no write conflicts by design.
- **Syncing the Room database or a backup file.** Whole-file replacement loses one side's changes; a backup also
  carries voice notes and settings that should stay per device.
- **Last-write-wins for marks.** Cannot tell "removed here" from "never seen here"; the per-device memory
  (`folder-sync-memory.txt`) does that.

## Consequences
- No network use by ListenLock; the privacy policy says the folder's app moves the files under its own terms.
- An edited note on a mark stays as each device has it (marks are matched by place and time, not by note).
- A mark deleted while a sync runs can come back from another device once; harmless and rare.
- Places use the resume anchor, so a device on another pipeline version finds the sentence again as resume does.
- If the same folder is also a watched folder and is later unwatched, watched folders gives back its read grant;
  picking the sync folder again restores it.
- Voice notes, settings and reading statistics are not synced (backups cover those).
