# Lighter file checks for big libraries (2026-10-31, ADR 2026-10-31-lighter-file-checks)
- The library's file check reads Android's list of lasting grants once per check, not once per book
  (`DocumentAccess.availability(documentUris)`, `SafDocumentAccess`, `Library.availability`).
- Returning to the library while a check runs queues one more check instead of starting another
  (`LibraryController.refreshAvailability`, a conflated channel); a check that finds nothing new leaves the list
  alone.
- `SafDocumentAccess.guarded` moved to file level (same code), keeping the class under detekt's function count.
- **Could affect:** the "File moved or deleted" and "access lost" warnings on library rows.
- **Verified in CI:** `LibraryTest`, `LibraryRecheckTest`, `AvailabilityBatchDeviceTest`, release journey step 64.

**Cost:** none at launch; less work on every return to the library, most for a big library of picked files.

**Real-device checks (the test phone):** with a library of books picked from Downloads, leave the app and come back:
the list should not flicker or jump. Delete one of those files in Files, come back: its row says "File moved or
deleted. Tap to find it."
