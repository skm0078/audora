# Full screen in the reader, and Text size one tap away (2026-11-13, no ADR)
- **Full screen:** a button beside the page number hides the phone's status and navigation bars, the reader's top
  bar, the bottom controls, the page scrubber and the time left. The page row stays as the way out: page turns, the
  page number, play/pause and Exit full screen. Back leaves full screen before it leaves the book; a swipe in from an
  edge shows the phone's bars for a moment. Kept across rotation, not across reopening the book (a book that opened
  with no controls would look broken). Only while a book is open: a password prompt or an error keeps the bars.
- **Text size:** the size slider already existed, but only inside ⋮ › Page and text, in the Text view, labelled
  "Size", so it was not found. Now: ⋮ › Text size opens a small sheet (it turns to the Text view, where the size
  shows), Settings › Reading and look has a Text size row, and search finds it by "font size", "bigger text" and
  their Hindi and Bengali words. All three use one block: Smaller and Larger buttons (10 % a press) either side of the
  slider, the size in percent, and a sample line. The range is unchanged (85 % to 160 % on top of the phone's font
  size), so saved sizes and the text pages' layout are untouched.
- "What's new" entry: `app/src/main/assets/whatsnew/2026-11-13-reader-full-screen-text-size.txt`. `:pdf` unchanged.
  No new dependency (WindowInsetsControllerCompat is in androidx.core, already used by the lock saver).
- **Perf:** none measurable. Full screen is one window call on toggle; the reader draws fewer composables while it is
  on. The text size block is the existing slider plus two icon buttons.
- **Could affect:** the reader's page row (it now has one more button, weighted so the page number wraps at large font
  sizes), the ⋮ menu, Page and text's Size row (same control, now with buttons), Settings › Reading and look.
- **Verified in CI:** TextSizeTest (button steps, ends), FoundIndexTest (font size finds Text size in en/hi/bn, every
  setting has words in all three), release journey step 68 (full screen hides the status bar and top bar, Back brings
  them back in the book, ⋮ › Text size › Larger text saves a larger size).

**Real-device checks (the test phone):** see the PR.
