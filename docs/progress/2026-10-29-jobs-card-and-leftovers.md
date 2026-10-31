# The Jobs card is redesigned, and unfinished jobs clean up after themselves (2026-10-29, ADR 2026-10-29-jobs-card-and-leftovers)
- One card for every job and download: icon toned by state, status pill, one detail line, rounded bar, buttons on
  the right. Cancelled downloads, unfinished audiobooks and failed talking books keep their files 7 days for
  Continue, then they are deleted at app start; at once if the book is gone, or on Clean up now / Delete unfinished
  files (both ask first). Finished audiobooks, copies, studio audio and anything in Made are never touched. The Jobs
  screen shows what is waiting and what the last clean-up deleted.
- "What's new" entry: `app/src/main/assets/whatsnew/2026-10-29-jobs-card-and-leftovers.txt`. `:pdf` not changed. No
  new dependency.
- **Could affect:** the library's and reader's job cards (same card), audiobook export resume (record format
  unchanged; records are now also read by the clean-up), talking-book Continue, every model download's resume.
- **Verified in CI:** LeftoversTest, DownloadLeftoversTest, AudiobookLeftoversTest, TalkingBookLeftoversTest,
  JobLeftoversTest, JobSlotTest, LongJobsPanelTest; on the emulator JobsAndPitchScreensTest and release journey
  step 55.

**Real-device checks (the test phone):** start a voice download and cancel it, open Made → Jobs: Unfinished work
shows its size; Clean up now asks, then says nothing is left. Save a talking book and let it fail or stop it with the
time limit: its card says how much is kept and for how long; Delete unfinished files asks, then deletes. Start an
audiobook, cancel it, save it again within the week: it goes on from the kept chapters. Light and dark mode cards.
