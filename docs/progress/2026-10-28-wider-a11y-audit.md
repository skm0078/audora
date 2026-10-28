# The screen tour finds look-alike buttons and buttons under buttons (2026-10-28, no ADR)
- From the accessibility and code structure audit (findings A1 and A5).
- The UI tour's TalkBack audit (`ui/tour/TalkBackAudit.kt`) now also reports, per screen and window:
  - controls that all read the same ("Delete", "Delete"), which TalkBack users cannot tell apart;
  - two controls where one covers the middle of the other (a list row under a floating button), neither inside the
    other.
- `LampContrastTest` now checks text on every card, sheet and dialog surface too (onSurface, onSurfaceVariant and
  primary on surfaceContainerLow, surfaceContainer, surfaceContainerHigh and surfaceContainerHighest), in the forest
  green day palette, the lamp at night and true black.
- App code changed: no (tests only). `:pdf` changed: no. New dependency: no. Performance cost: none in the app; the
  tour does one more pass over each screen's controls (about a hundred).
- **Verified in CI:** unit tests (contrast), the UI tour on this branch.
