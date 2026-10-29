# Search and You open the player's options (2026-10-29, ADR 2026-10-29-find-player-options)
- The library search now finds the player's options for the book it names: Voice and speed (also "speed", "pitch",
  "faster", "slower"), Voices, What to read, Sound, Cast, Study and Focus sound. A row opens that book with its Voice
  and speed sheet at that place, once.
- You › Listening has a "Voice and speed" row under Voices: it opens the book read last at the sheet's top. With no
  book yet it is shown greyed, saying "Open a book first, then its speed and voice are here".
- "What's new" entry: `app/src/main/assets/whatsnew/2026-10-29-find-player-options.txt`. `:pdf` unchanged. No new
  dependency, no new icon. New strings: `found_terms_speech`, `settings_speech_body`, `settings_speech_no_book`
  (three languages).
- **Cost:** seven more in-memory search entries while a book is named, compared as strings per keystroke (about a
  hundred entries in all); one `LaunchedEffect` in the player's controls that does nothing without a request. No
  layout change on the player. A few KB of APK.
- **Could affect:** the library search's "Actions and settings" rows (more rows for "voice" or "sound"); You's
  Listening group (one row more); opening a book from search or You.
- **Verified in CI:** `FoundIndexTest`, `SpeechRequestTest`, `SettingsSpeechDoorTest`, release journey step 54.

**Real-device checks (the test phone):** with a book read before, type "focus" in the library search: a "Focus sound"
row says "The book you're reading · Voice and speed"; tap it: the book opens with the sheet's Focus sound group open.
Back closes the sheet and it does not come back; leave and reopen the book: no sheet. Type "faster": "Voice and speed"
opens the sheet's top. On You, Listening's "Voice and speed" opens the same book's sheet. On a fresh install with no
books, the row is greyed and says to open a book first. Repeat the search in Hindi ("स्पीड") and Bengali ("গতি").
