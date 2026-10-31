# ADR 2026-10-31-lighter-file-checks: check a big library's files in one batch, and only when the result can change

**Status:** Accepted (2026-10-31). PR4 of the on-phone speed audit. No feature removed: every book's file is still
checked each time the library comes back on screen, and a moved or deleted file is still flagged before a tap.

## Context

Each time the library comes on screen (`LifecycleResumeEffect` → `LibraryController.refreshAvailability`), every
book's file is checked so a file moved or deleted outside the app shows "File moved or deleted" before a tap. Three
costs grew with the library's size:

1. **The grant list was read once per book.** `SafDocumentAccess.availability` asked Android for
   `persistedUriPermissions`, and again for a book in a watched folder. That binder call returns every lasting grant
   the app holds (up to 512). A library of N picked files read N lists of up to N grants, so the check grew with the
   square of the library's size.
2. **Checks stacked.** Switching apps quickly (back and forth from a file manager) started a new full check on each
   return while the last one was still running.
3. **Every finished check rebuilt the list.** `publish()` filters, sorts and groups every book on the main thread,
   and ran even when no book's state had changed, which is nearly always.

## Decision

1. **One batch per check** (`DocumentAccess.availability(documentUris)`): `Library.availability` asks for all the
   books' files at once. `SafDocumentAccess` reads the grant list once per batch and checks each file against that
   set. The interface's default makes one call per file, so other access layers (tests, fakes) need no change.
2. **No stacked checks** (`LibraryController.refreshAvailability`): each return to the screen sends a request into a
   conflated channel that one loop works through. Returns while a check runs collapse into one check after it,
   however many there were, and that later check still sees a file deleted while the first one ran. No flags or job
   bookkeeping: the channel's conflation is the whole rule.
3. **The list is rebuilt only on a change**: a check whose results match what is shown leaves the list as it is.

What stays: one provider call per file (`openFileDescriptor`) to see that it is really there. That is the check
itself; dropping it would let a deleted file look fine until a tap.

## Consequences

- **Speed:** the grant work goes from N reads of the whole list to one. Nothing changes for Audora's own copies
  (shared and imported books), which never needed a grant. Most returns to the app no longer re-sort the library.
- **Cost:** no new memory beyond one set of granted URIs per check, no dependency, no new work at launch.
- **Behaviour kept:** the same three states (available, moved or deleted, access lost), the same moment (each return
  to the library).
- **Tests:** `LibraryTest` (the check is one batch), `LibraryRecheckTest` (quick returns run one more check, not one
  each; an unchanged check keeps the list; a file deleted while away is flagged), `AvailabilityBatchDeviceTest` (the
  batch answers exactly as single checks do, on a device), release journey step 64 (a file deleted while another tab
  is open is flagged when the library shows again, which runs the same check as a return from another app).

## Alternatives considered

- **Skip the check when the last one was recent.** Cheaper still, but a file deleted in a file manager and the app
  reopened within the window would look fine. Rejected: it trades a visible warning for speed.
- **Check only the books on screen.** Saves the most on very big libraries, but "Continue reading" and the app's
  search skip books whose file is gone, wherever they sit in the list, so they need every book's state. Rejected for
  now; worth a look if the batch is still slow on the test phone with a thousand picked files.
- **Run the provider calls in parallel.** Most books share one or two providers, so the gain is unmeasured and the
  code harder to follow. Not done until the test phone shows the batch is still slow.
