# Quote video, part 2b: the screen (2026-10-28, ADR 2026-10-28-quote-video)
- "Share as a video" in the sentence menu's Share choices; the card screen gets Picture / Video chips, Standard 720p
  or Sharp 1080p, a live preview of the video's first moment, and Make the video.
- `QuoteVideoService` (`:playback`): reads the quote in the book's voice (`QuoteVoice`), lights each word
  (`QuoteReading`, `QuoteVideoPainter`), writes the MP4 over the captured card, progress in a notification, then the
  share sheet. The Tiro fonts moved from `:app` to `:playback` so both draw the quote with them.
- What's new entry `2026-10-28-quote-video`. `:pdf` not changed (PIPELINE_VERSION unchanged).
- **Could affect:** the share card screen (new chips; picture cards unchanged), the sentence menu's Share choices.
- **Verified in CI:** `QuoteReadingTest`, `QuoteVideoPainterDeviceTest`, `QuoteVideoWriterDeviceTest` (now with
  progress), `ShareCardScreenTest` (video mode records the card without its words, Bengali at 200 %),
  `SentenceMenuTest`, release journey step 25 (a real video from the card screen in the book's voice).

**Cost:** about 25 KB of code and strings; 3.7 MB (720p) or 8.3 MB (1080p) backdrop while a video is made, plus 2a's
encoder memory; nothing at cold start.
