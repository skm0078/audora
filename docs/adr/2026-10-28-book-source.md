# ADR 2026-10-28-book-source: say where a book lives, and keep a cloud book on the phone

**Status:** Accepted (2026-10-28). Item 5 of "Still to build before v0.1.0": on the test phone, books added from
Google Drive and books on the phone looked the same in the library, and a Drive book failed to open offline with
nothing to say why.

## Context

A book is a Storage Access Framework URI with a lasting read grant (`Book.documentUri`). The URI's authority names
the app that serves the file: `com.android.externalstorage.documents` for phone storage and SD cards,
`com.google.android.apps.docs.storage` for Google Drive, and so on. A Drive file may be online only: reading it then
needs the internet, and the Drive app decides when it is cached.

## Decision

- **`BookSource` from the authority, never the path** (`core/library/BookSource.kt`): PHONE (phone storage, SD card,
  Downloads, the media store, ListenLock's own copies), GOOGLE_DRIVE, ONEDRIVE, DROPBOX, OTHER_APP (an app we do not
  know: a file manager, another cloud). Pure string work, no I/O, no new column: the URI already says it, so nothing
  is stored and old books get it for free. A new app is one line in `BookSources.KNOWN`.
- **Mark the exception, not the rule.** A row gets a cloud icon and the app's name under the author; a cover tile gets
  a small cloud in its top corner and the name in its TalkBack description. Books on the phone, most of a library,
  get no label there. The details sheet says "Where: …" for every book, and for a book read through another app adds
  that it may need the internet.
- **Keep on this phone** (`core/library/KeepOnPhone.kt`), in the book's details (and found by search): ListenLock's
  own copy through `SharedPdfCopies.copy`, then `Library.relocate` to it. Same bytes, same fingerprint, so it is the
  same book: reading position, notes, studio audio and collections stay, and the other app's grant is given back. A
  copy the book cannot take (removed meanwhile, file changed) is deleted at once. One copy at a time, in the app's
  scope, with a toast when it starts and when it ends. From then on removing the book deletes the copy, like any
  shared copy; backups and folder sync already handle such copies.

## Alternatives

- **Store the source in a Room column:** a schema bump and a migration for something the URI already says.
- **"On this phone" on every row:** clutter on hundreds of rows to say what is true of most of them. Flagged to the
  maintainer; one line to add if wanted.
- **Ask the provider (`DocumentsContract`) whether a file is cloud-only:** a binder call per book on every library
  show, and providers do not report it consistently.
- **Copy cloud books automatically when added:** doubles storage without asking; the listener chooses per book.

## Consequences

- Cost: two string checks per book per library change (no I/O), one 16 dp vector icon, about 2 KB of strings.
  The copy costs the book's size in storage, only when the listener asks; the hint says so.
- A file manager's URIs show as "From another app" even when the file is on the phone: honest, since we cannot tell.
- `BookSource` is ready for a "Drive" filter chip later if listeners want one.
