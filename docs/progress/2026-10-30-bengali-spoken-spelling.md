# Bengali voices say words the way they are said (2026-10-30, ADR 2026-10-30-bengali-spoken-spelling)
- SYSPIN male and female and Rasa read Bengali letters, so they guessed how a word ends and whether its "এ" is wide:
  "সব" came out "sobo", "চোর" "choro", "কাণ্ড" "kand", "এমন" "emon". Each Bengali word they read is now respelled as
  said (`BengaliSpelling`, word lists in `BengaliSpellingWords`): a hasanta for a silent end (সব্), "ো" for a spoken
  one (কাণ্ডো, করলো), "্যা" for the wide "e" (অ্যামন্). A word the rules are unsure of stays as written.
- These voices speak at 0.9 of the chosen speed (`SpeechConfig.bengaliPace`), and Rasa reads with calmer noise
  (`VoiceShapingConfig.styledNoiseScales` and `styledNoiseWidths`), so its pitch and pace no longer jump about. Every
  other voice is unchanged. Studio audio version 9.
- "What's new" entry: `app/src/main/assets/whatsnew/2026-10-30-bengali-spoken-spelling.txt`. `:pdf` unchanged
  (PIPELINE_VERSION unchanged). No new dependency.
- **Cost:** 0.37 µs a word on a desktop JVM (about a microsecond on a phone), for these voices only; a few KB of APK
  and well under 100 KB of memory for the lists, built on the first Bengali sentence. Bengali audio from these voices
  is about 11 % longer. Saved studio audio is deleted once and made again (version 9).
- **Could affect:** every Bengali sentence SYSPIN or Rasa reads (word ends, the wide "e", pace), Rasa at each
  expressiveness step, Bengali characters read by Rasa's male speaker, the measured speed of these voices (it sees the
  slower pace as theirs), studio audio.
- **Verified in CI:** `BengaliSpellingTest`, `LetterVoicesTest`, `SherpaSpeechEngineTest`, `ExpressionNoiseTest`,
  `VoiceShapingConfigTest`, `SpeechConfigTest`, release journey step 66 (on the phone's own Unicode library, a Bengali
  letter voice is handed "সব্ চোর্ বড়ো হলো।" for "সব চোর বড় হল।", at 0.9 of the asked rate).

**Real-device checks (the test phone):** with SYSPIN male, then SYSPIN female, then Rasa, play a Bengali story page:
"সব" is "shob", "চোর" "chor", "কাণ্ড" "kando", "এমন" "æmon", "গেল" "gælo", "করল" "korlo". The word highlight follows
every word, and the pace is a little slower than before at 1x and still follows the speed control. With Rasa, move
expressiveness from newsreader to storyteller: the voice stays steady (no sudden shrill jumps) and livens up towards
storyteller. An English and a Hindi book sound as before.
