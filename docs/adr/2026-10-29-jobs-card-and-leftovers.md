# ADR 2026-10-29-jobs-card-and-leftovers: one job card, and unfinished jobs clean up after themselves

**Status:** Accepted (2026-10-29).

## Context
Phone testing found the Jobs screen's cards (ADR-067) hard to read: three loose lines of text, a thin bar, text-only
buttons, and downloads drawn differently from book jobs. It also found that jobs which stop part way leave files
nobody deletes. Every kind keeps its leftovers on purpose, so the job can go on later:
- a cancelled or failed model download keeps `<owner>/downloads/<name>.partial` under the no-backup folder for a
  resume, hundreds of MB for a voice;
- a stopped audiobook export (ADR-034) keeps its finished chapters in Audiobooks/<title>/ and, if cut off mid-chapter,
  a hidden half-written MediaStore entry; none of them is in Made, which lists an audiobook only once it is complete;
- a talking book that failed or met Android's time limit keeps its staged chapters in `cacheDir/talking-book/`
  (about 30 MB an hour of book).

Nothing deleted any of these unless the same job was started again, so a job the listener gave up on kept its files
for good. Studio's cache already cleans itself (`StudioStore.dropBooksNotIn`, its size cap) and is not changed.

## Decision
- **One card** (`JobCard`) for long jobs and downloads: the kind's icon in a circle toned by how it is going, the
  job's title, a status pill (percent, Starting, Waiting, Done, Didn't finish, Stopped), one detail line
  ("34 MB of 120 MB · 12 min left"), an 8 dp rounded bar, and buttons on the right (outlined Cancel or Stop, text OK
  and Details). Colours come from the theme, so light mode is forest green and dark is Direction B's amber.
- **Leftovers are kept for 7 days** (`LeftoverConfig.keepMs`), counted from their last write, then deleted. One rule
  for every kind (`LeftoverPolicy`), said in one sentence on the screen. 7 days matches Android's own expiry of
  hidden half-written shared files, and keeps Continue, a resumed download and saving the book again working.
- **Deleted at once** when the book is gone from the library (nothing can continue it) or when the listener asks:
  Clean up now on the Jobs screen's Unfinished work card, or Delete unfinished files on a stopped or failed
  audiobook or talking-book card. Both ask first.
- **When:** at every app start, 20 s after launch on the app's background thread (`JobLeftovers.cleanAtStart`), and
  on the listener's tap. Not on a schedule: the app has no periodic work, and a start is frequent enough.
- **Never touched:** a finished audiobook's chapters, any file Made lists (checked against the outputs table whatever
  a record says), copies in Documents, studio audio, installed models, and anything of a kind whose job is running.
  Each owner runs holding its job's slot (`JobSlot.runIfFree`: an export's files under `JobSlots.EXPORT`, a talking
  book's under `JobSlots.COPY`), so a job cannot start while its files are cleaned; no download's file is touched
  while any download is queued or running (`DownloadJobs.anyActive`; when WorkManager cannot answer, the files stay).
- **What was cleaned is shown:** the Unfinished work card says what is waiting, the rule, and the last clean-up that
  deleted anything ("Last cleaned 29 Oct: 1.2 GB from 3 downloads, 1 audiobook"), kept in a small preferences file.
  A failed job's card names what it kept and for how long.
- **No `:pdf` change:** PIPELINE_VERSION is unchanged; corpus and listening scores are untouched.

## Alternatives considered
- **Delete on Cancel or failure at once:** simplest, but breaks Continue after Android's time limit and resuming a
  download, both deliberate (ADR-034, item 76, ADR-067). The grace period keeps them.
- **A periodic WorkManager clean-up:** runs with the app closed, but adds a scheduled job for housekeeping that a
  start does as well; a phone that never opens the app also never makes new leftovers.
- **Each owner cleaning its own folder at its own start:** downloads already drop other pins' partials when the same
  model downloads again; that never runs for a download nobody starts again. One clean-up keeps the rule in one place.
- **Finding downloads through each owner's API:** eight owners, each a singleton with its own lifecycle. They all keep
  `<folder>/downloads/<name>.partial`; one scan of that layout covers a new kind too, and `DownloadLeftoversTest`
  checks the owners' paths keep it.

## Consequences
- **Cost:** at start, one listing of the no-backup folder's top two levels, one read per export record (one per book
  ever exported), one WorkManager query, and one MediaStore call per file actually deleted, all off the main thread
  after the first screen. Opening Jobs measures the same plus one MediaStore size query per unfinished chapter (tens
  of ms for a long book). Nothing runs during playback beyond that; no new dependency.
- A job continued after 7 days starts from the beginning. The card says so before then.
- A download started in the same millisecond as the clean-up's check could have its file deleted; the downloader
  then fails its checksum and downloads again. Accepted: it costs time, never a damaged model.
- New strings in `strings_job_leftovers.xml` (en, hi, bn); `jobs_download_kept` is replaced by a plural that names
  the days.
- Tests: `LeftoversTest` (:core), `DownloadLeftoversTest` (:speech), `AudiobookLeftoversTest`,
  `TalkingBookLeftoversTest`, `JobLeftoversTest`, `JobSlotTest` (:playback), `LongJobsPanelTest` (:app); on a device
  `JobsAndPitchScreensTest` (asks before deleting, screenshots in `player-screens`) and release journey step 55.
