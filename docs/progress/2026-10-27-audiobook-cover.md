# Cover, title and author inside exported audiobooks (2026-10-27)
- Every exported `.m4a` chapter now has iTunes-style tags in `moov/udta/meta/ilst`: chapter title (`©nam`, the file
  stem), book title (`©alb`), author (`©ART`, `aART`, when known), track n/total (`trkn`), genre `Audiobook`, and the
  library's cover (`covr`, JPEG). MediaMuxer cannot write these, so `Mp4Tags` adds them after the muxer stops and
  before the file is published to MediaStore, so the media scanner indexes album and artist on first sight.
- `Mp4Tags` only rewrites `moov`; the audio (`mdat`) never moves, so no sample offset (`stco`/`co64`) needs fixing.
  `moov` last: rewritten in place. `moov` + `free` with room: in place, rest stays `free`. Otherwise the old `moov`
  becomes `free` and the new one is appended. Tagging failures are logged and skipped; they never fail an export.
- Cover: `CoverFolders.shownFile` (the listener's kept picture, chosen page or first page, the same one the library,
  notification and lock screen show), decoded with power-of-two sampling and scaled to
  `AudiobookConfig.coverMaxSidePx` (600) at `coverJpegQuality` (85): about 40-80 KB per file. WebP is converted
  because MP4 players read only JPEG and PNG covers.
- EPUB 3 talking books (`CopyJob`) are unchanged: their audio sits inside the EPUB, which has its own cover.
- Files exported before this change keep no tags; export again to get them.
- "What's new" entry: `app/src/main/assets/whatsnew/2026-10-27-audiobook-cover.txt`. `:pdf` not changed
  (PIPELINE_VERSION unchanged).
- **Could affect:** the end of each exported chapter (a few hundred KB more written), MediaStore's album/artist for
  exported files.
- **Verified in CI:** `Mp4TagsTest` (four box layouts, existing `udta`, missing artist/cover, no `moov`),
  `AudiobookExportDeviceTest` (MediaMetadataRetriever reads album, title, artist, `3/12`, the exact cover bytes; the
  file still decodes at the right length; a big WebP becomes a 200 px JPEG), release journey step 17.

**Real-device checks (the test phone):** set your own cover on a book in Edit details, export it as an audiobook,
then open the Audiobooks folder in an audiobook player (Voice, Smart AudioBook Player) or the Files app: the book
shows under its title with your picture, chapters in order, the author as artist.
