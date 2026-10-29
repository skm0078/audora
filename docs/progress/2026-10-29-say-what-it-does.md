# Say what it does: a line under every switch (2026-10-29, ADR 2026-10-29-say-what-it-does)
- Every switch in Settings, Voice and speed (Sound, Cast, Study), What to read and Page and text has one line under
  its name saying what it changes and what it costs. Long help stays behind ⓘ.
- Download packs (scanned pages, pauses in scans, acted Hindi, story brain, voice commands) say what they add before
  "Not downloaded · N MB".
- Renamed: "Prepare ahead (studio mode)" in the book menu; "Practise reading aloud (beta)" in the reader's ⋮.
- "What's new" entry: `app/src/main/assets/whatsnew/2026-10-29-say-what-it-does.txt`. `:pdf` unchanged. No new
  dependency.
- **Cost:** none at run time beyond drawing one more text line per row; about 21 short strings in three languages
  (a few KB of APK). No start-up, scroll or memory change worth measuring.
- **Could affect:** the height of option rows in Settings and the player's sheets; the two renamed menu entries.
- **Verified in CI:** `RowLinesTest`, release journey step 47.

**Real-device checks (the test phone):** open Settings: "Keep screen on while reading aloud" says "Uses more battery
while it reads" and True black says it saves battery on OLED. In a book, Speed › Sound: each switch has its line. In
Hindi and Bengali, the lines fit on one or two lines at the largest text size. A not-downloaded pack shows what it
adds above its size. TalkBack reads the line after each switch's name.
