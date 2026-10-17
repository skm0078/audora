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
   - `manifest.properties` first: format 1, the database's schema version, the date;
   - `databases/audora.db`: a consistent snapshot of every table (books, resume anchors, bookmarks, book settings,
     pronunciations, highlights, voice notes) taken with SQLite's `VACUUM INTO` while the app runs
     (`AudoraStorage.snapshotTo`), WAL contents included;
   - the reading-text, Up next, speech-style and sleep-option files, the reader, reading-look and appearance
     preferences, each voice's speed, the voice-note recordings and the books shared into the app.
   Left out: voices, OCR models and the dictionary (large, downloaded again), which voice each language uses (it may
   name a voice the new phone lacks), covers and search indexes (rebuilt), logs, export records and studio audio
   (they point at files on the old phone), watched folders (folder grants do not move), the widget, and the first-run
   flag, so a restored phone still goes through first run (voice download, battery setup).
3. **Restore is staged, then applied at the next start.** The picked file is unpacked into
   `no_backup/restore-pending/` and marked ready; the app restarts itself, and `AudoraApplication.onCreate` applies it
   (`Backups.applyPending`, `PendingRestore`) before Hilt builds anything that opens the database or preferences.
   Applying deletes every allowed path (and the database's `-wal`, `-shm` and `-journal`, which would otherwise be
   replayed over the restored file), copies the staged files in, and only then removes the marker, so a crash
   part-way applies the whole backup again at the next start. A staging without a database is never applied.
4. **A picked file is untrusted.** The manifest must be the first entry, so a foreign or newer file is refused before
   anything is unpacked. A newer format or schema version is refused ("update the app first"); an older schema is
   accepted and Room's migrations bring it up to date. Only allow-listed names are unpacked (no `..`, `.`, empty or
   backslash parts), so a crafted entry cannot write outside the staging folder; limits count the bytes actually
   unpacked: 2 GiB per file, 8 GiB and 20,000 files in all (`BackupConfig`).
5. **Books relink by content.** Books opened through the Storage Access Framework come back with positions and notes
   but show as needing their file: grants do not move between phones. Adding the same file again relinks the book
   by its fingerprint (`BookPlacement`, unchanged). Books shared into the app are inside the backup and open at once.
6. **Logs** say counts, bytes and the refusal reason, never file names, URIs or exception messages (which can carry
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
- A restore needs up to twice the backup's size free for a moment (staging plus the copied files); the staging is
  deleted after.
- Books shared into the app make a backup large (each is kept whole). Restored shared books keep their file paths,
  so a backup from the debug build ("Audora Dev", package suffix `.debug`) restored into the release build shows
  those books as missing; positions and notes are kept and relink when the same file is added again.
- A settings file that is renamed silently drops out of backups: `BackupContents.DEFAULT` repeats each owner's file
  name, so a rename updates it too. Settings added later (for example a new preferences file) join the list then.
- Playback stops when the app restarts to apply a restore; nothing resumes on its own (AGENTS.md).
