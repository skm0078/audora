# Release journey waits: the mid-layout read arrives wrapped (2026-10-29, no ADR)
- **Still failing after #112:** step 12 failed again on #109 with the same
  `performMeasureAndLayout called during measure layout`.
- **Cause:** Espresso's idle sync wraps the error as RuntimeException > ExecutionException > IllegalArgumentException,
  so `countNow`'s `catch (IllegalArgumentException)` never matched.
- **Fix** (`release/JourneyDevice.kt`): `countNow` catches RuntimeException and returns "can't tell yet" only when the
  cause chain holds that IllegalArgumentException; anything else is rethrown. A layout that never settles still fails
  as a timed-out wait.
- `:pdf` changed: no. App code changed: no. Performance cost: none (tests only).
