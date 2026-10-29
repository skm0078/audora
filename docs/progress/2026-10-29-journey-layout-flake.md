# Release journey waits survive a read that lands mid layout (2026-10-29, no ADR)
- **Flake fixed:** step 12 (a scan offers OCR and reads after the download) sometimes failed with
  `IllegalArgumentException: performMeasureAndLayout called during measure layout`. This came from inside the wait for
  the OCR card to go. It hit #109 twice; dev passed.
- **Cause:** the test reads the screen's tree while the reader lays out the page that replaces the card. That read can
  land mid layout. The app's own frames are not affected, and the reader code does no layout work of its own when the
  card leaves (`PageOriginNotice` is plain composition; the page view's `onSizeChanged` only stores a size).
- **Fix** (`release/JourneyDevice.kt`):
  - Every journey wait now reads the tree through one helper, `countNow`. It returns "can't tell yet" for that moment,
    as it already did while one activity hands over to the next.
  - `shows` (something is there) and the new `hides` (it has gone) both treat "can't tell yet" as not done, so the next
    poll decides.
  - Waits in `ReleaseJourneyTest`, `DrawerJourney`, `HomeJourney`, `LockJourney`, `MadeJourney`, `SourceJourney`, `StatsJourney` and
    `TourJourney` that read the tree directly, or as `!shows(...)`, now use these.
  - The `!shows(...)` form was a quieter bug: a tree that couldn't be read counted as "gone".
- `:pdf` changed: no. App code changed: no. Performance cost: none (tests only).
- **Verified in CI:** device tests (the release journey) on this branch.
