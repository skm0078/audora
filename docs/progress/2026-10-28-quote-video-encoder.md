# Quote video, part 2a: the encoder (2026-10-28, ADR 2026-10-28-quote-video)
- `QuoteVideoWriter` (`:playback`): the phone's H.264 and AAC encoders into one MP4 at 720 x 1280, 30 fps, frames
  timed through EGL, only changed frames drawn, the voice woven in by time, "AI voice" on every frame.
- `VideoTimeline` and `QuoteVideoConfig` (`:core`): the frames, the one-minute cap, the word each frame lights.
- No screen yet (2b), so no "What's new" entry. `:pdf` not changed (PIPELINE_VERSION unchanged).
- **Could affect:** nothing existing; new code only.
- **Verified in CI:** `VideoTimelineTest`, `QuoteVideoWriterDeviceTest` (sizes, tracks, every frame written, the right
  picture at each moment, the label drawn, the cap), release journey step 21 in the installed app.

**Cost:** about 15 KB of code; 3.7 MB bitmap plus the encoders' buffers only while a video is written; no change at
cold start. The device test logs how long two seconds of video took.
