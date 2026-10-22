# ADR-046: Backup and restore

**Status:** Accepted (2026-10-17). Plan item 28.

## Context
A listener moving to a new phone loses their library, reading positions, bookmarks, highlights, voice notes,
pronunciation fixes, per-book settings, Up next and reading settings: Audora keeps everything on the phone, has no
account and no server, and may never get one (zero spend, offline-first). Android's own backup is not a fit: it is
off on many phones, its Google Drive copy is capped at 25 MB, it restores only from the same Google account, and the
listener cannot see or keep the file. The database is Room schema version 4; there is one database file plus a few
small settings files.

## Decision
1. **One zip file the listener keeps.** *Back up and restore* in the library's ⋮ menu: *Save a backup* writes
   `audora-backup-<date>.zip` wherever the listener chooses (`CreateDocument`; Drive, a USB stick, a computer);
   *Restore* picks one (`OpenDocument`) after a warning that it replaces this phone's library, notes and settings.
2. **What it holds** (`BackupContents.DEFAULT`, paths relative to the app's data folder, so an entry's name is also
   where it is restored):
   - `manifest.properties` first: format 1, the database's schema version, the date and the writer's list of units
     (each file or folder it backs up);
   - `databases/audora.db`: a consistent snapshot of every table (books, resume anchors, bookmarks, book settings,
     pronunciations, highlights, voice notes) taken with SQLite's `VACUUM INTO` while the app runs
     (`AudoraStorage.snapshotTo`), WAL contents included;
   - the reading-text, Up next, speech-style, sleep-option, watched-folder and focus-sound files, per-book character
     names (character voices), the reader, reading-look and appearance
     preferences, each voice's speed, the voice-note recordings and the books shared into the app.
   Left out: voices, OCR models and the dictionary (large, downloaded again), which voice each language uses (it may
   name a voice the new phone lacks), covers and search indexes (rebuilt), logs, export records and studio audio
   (they point at files on the old phone), the widget, and the first-run flag, so a restored phone still goes
   through first run (voice download, battery setup).
3. **Restore is staged, checked, then applied at the next start by renames.** The picked file is unpacked into
   `no_backup/restore-pending/` (refused if it would leave less than 128 MiB free), its database is opened
   read-only and checked (schema version no newer than this build, `PRAGMA quick_check`, and at the same version
   Room's identity hash equal to the live database's), and it is marked ready with the units to replace. The app
   restarts itself, and `AudoraApplication.onCreate` applies it (`Backups.applyPending`, `PendingRestore`) before
   Hilt builds anything that opens the database or preferences. Applying is renames only, inside one volume: each
   unit (a file, a whole folder, the database with its `-wal`, `-shm` and `-journal`, which would otherwise be
   replayed over the restored file) moves to this attempt's own folder `no_backup/restore-aside/<attempt>/`, or
   leaves a `.none` note if absent, and the staged one moves in. The marker records each step (`ready`, `applying`,
   `committed`) and is written by a synced temp file and a rename; every rename phase is synced too, so a power cut
   cannot reorder them. Writing `committed` is the commit point; a start that finds it only tidies up. On any
   failure, and at a start that finds `applying` (a crash, or a torn marker), every unit moves back, each on its own,
   the database and its journals as one group; a unit that cannot go back keeps the marker, so the next start tries
   again and nothing new can be staged meanwhile (the library says the restore is unfinished). Only when everything
   is back is the restore dropped, so a bad restore is tried once, never at every start; the library then says the
   library is as it was. What was moved aside goes to a trash folder that a later start empties, after the restored
   library has opened. An aside folder from another attempt is never put back.
   Unpacking checks free space against the manifest's total before it starts and again as it writes, and syncs each
   file. A staged database must carry the Room identity hash of its schema version (`SchemaHashes`, one entry per
   version; a new schema version adds one), not just the live one's.
   A unit the writer knew but did not hold goes back to its default; a unit it did not know (added in a later app
   version) is left as it is. A staging without a database is never applied. A restore is refused while an export,
   copy, clip or studio run is going, since the restart would cut it off (`JobSlots`, one per kind, checked again
   just before the restart), and those jobs refuse to start while a restore is being staged; the job itself runs in a
   process-wide scope (`Backups`), so turning the phone keeps it. Rows and copies arrive together: the database and
   `files/shared-pdfs` are renamed into place before Room opens, so the shared-copy prune never sees a restored copy
   without its book row.
4. **A picked file is untrusted.** The manifest must be the first entry, so a foreign or newer file is refused before
   anything is unpacked. A newer format or schema version is refused ("update the app first"); an older schema is
   accepted and Room's migrations bring it up to date. Only allow-listed names are unpacked (no `..`, `.`, empty or
   backslash parts), so a crafted entry cannot write outside the staging folder; limits count the bytes actually
   unpacked: 2 GiB per file, 8 GiB and 20,000 files in all (`BackupConfig`).
5. **Books relink by content.** Books opened through the Storage Access Framework come back with positions and notes
   but show as needing their file: grants do not move between phones. Adding the same file again relinks the book
   by its fingerprint (`BookPlacement`, unchanged). Books shared into the app are inside the backup and open at once.
   Watched folders come back as unreachable with *Pick again* (ADR-044 §6); picking the same folder again makes
   its books readable without re-adding each one.
6. **Start order.** No worker, service or store opens Room or writes a backup unit before `applyPending` returns.
   WorkManager's `androidx.startup` initializer runs before `Application.onCreate`, so a worker left over from the
   last run could in principle start while units are being renamed. No worker touches a backup unit today (workers
   only download voices, models and dictionaries into `noBackupFilesDir`), so nothing guards it: a restore-only
   content provider ordered before `androidx.startup` would add a component to the manifest for a gap that cannot
   happen yet, and removing the app-wide initializer would break app basics' downloads. A future worker that writes
   a backup unit must check `Backups.restoreUnderWay` first.
7. **An unfinished restore blocks the app.** When a start finds a restore it could neither finish nor put back
   (`Backups.restoreUnfinishedAtStart`), the activity shows only *Finish restoring* (restart Audora to try again)
   instead of the library, so nothing opens Room or writes a unit over a half-moved set; long jobs already refuse
   while `restoreUnderWay`. The playback service, quick-settings tile and widgets are started by the system without
   the activity and honour the same flag at landing.
8. **Logs** say counts, bytes and the refusal reason, never file names, URIs or exception messages (which can carry
   a URI).

## Alternatives considered
- **Android Auto Backup / `BackupAgent`:** invisible to the listener, 25 MB, same Google account only, off on many
  phones. Rejected as the mechanism; it may still copy the app's files where the phone has it on.
- **Merging a backup into an existing library:** needs conflict rules for every table (two positions for one book,
  duplicate bookmarks) and is not what moving phones needs. Rejected; restore replaces.
- **Copying the live database file:** a copy taken during a write can be torn and misses WAL pages. `VACUUM INTO`
  gives a consistent, compact copy without closing Room. Chosen.
- **Applying the restore in place without a restart:** Room, DataStore and several stores hold open files and
  in-memory copies; replacing them underneath corrupts the database or loses the restore at the next write.
  Rejected.
- **Exporting JSON per table:** readable, but every schema change would need its own export and import code; the
  database file plus Room's migrations already covers versions. Rejected.
- **Including the voice choice and voices:** voices are hundreds of MB and download again; a choice naming a voice the
  new phone lacks would leave a language silent. Rejected; speeds are kept.

## Consequences
- A restore needs the backup's unpacked size free (plus 128 MiB) while staged; applying moves files and needs no
  more space. The replaced files are deleted after the restart.
- Books shared into the app make a backup large (each is kept whole). Restored shared books keep their file paths,
  so a backup from the debug build ("Audora Dev", package suffix `.debug`) restored into the release build shows
  those books as missing; positions and notes are kept and relink when the same file is added again.
- A settings file that is renamed silently drops out of backups: `BackupContents.DEFAULT` repeats each owner's file
  name, so a rename updates it too. Settings added later (for example a new preferences file) join the list then.
- Playback stops when the app restarts to apply a restore; nothing resumes on its own (AGENTS.md).
