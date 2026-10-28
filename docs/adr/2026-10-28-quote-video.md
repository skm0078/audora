# ADR 2026-10-28-quote-video: a quote video from the phone's own encoders, its frames timed through OpenGL

**Status:** Accepted (2026-10-28). Part 2a of the share plan (share cards, quote video, page flips), approved by the
maintainer for v0.1.0. 2b (below) adds the screen that makes one from a share card.

## Context
A share card is a still picture. A quote read aloud, with each word lit as the voice says it, says what the app does
in a few seconds, and short videos are what people post. The maintainer's rules: up to 60 s, 720 x 1280 at 30 fps,
the hardware H.264 encoder with AAC through MediaMuxer, no FFmpeg, an "AI voice" label always on, the ListenLock mark
always on (the card frame already draws it), and edge-device limits respected without cutting quality silently.

## Decision
1. **Platform encoders only.** `MediaCodec` H.264 for the picture and AAC-LC for the voice, `MediaMuxer` for the MP4.
   No new dependency, no native code of ours, nothing added to the APK beyond about 15 KB of code.
2. **Frames timed through EGL.** The encoder's input surface is an EGL window; each frame is drawn on the CPU into one
   reused 720 x 1280 bitmap, uploaded as a texture, drawn over the picture and stamped with its time
   (`eglPresentationTimeANDROID`). A canvas locked on the surface cannot stamp a time: frames drawn faster than real
   time would get the wrong times, and some encoders' rate control would starve them. With EGL the video takes as long
   as the phone needs and still plays at real speed.
3. **Draw only what changes.** `QuoteVideoFrames.lookOf` names a frame's look (for a quote, the word lit). A frame
   that looks like the one before shows the same texture again: about one frame in ten is drawn and uploaded.
4. **The voice first, in memory.** The AAC frames of a minute of mono speech are about 480 KB, so the voice is encoded
   whole before the first picture and written between the video's frames by time, as players expect.
5. **The label is the writer's.** `AiVoiceLabel` is a required argument and the writer draws it on every frame after
   the caller's picture, so no video can leave it out.
6. **The cap is the timeline's.** `VideoTimeline` (pure JVM, `:core`) cuts a reading at `maxDurationMs` and says so
   (`isCut`), so 2b can tell the listener; it picks each frame's word from the voice's own word times (ADR
   2026-10-26-word-timings).

## Alternatives
- **FFmpeg (mobile-ffmpeg or a build of our own):** 5 to 30 MB per ABI, an LGPL/GPL build to audit, and slower than
  the hardware encoder. Ruled out by the maintainer.
- **Canvas on the input surface (`lockHardwareCanvas`):** simpler, but no control of frame times (see 2).
- **Media3 Transformer:** handles timing, but adds about 1 MB and is built for editing existing media, not for frames
  we draw.
- **Draw every frame:** simpler, ten times the CPU and upload work for the same video.

## Consequences
- Memory while writing: one 3.7 MB bitmap, one texture of the same size, the encoders' own buffers and the voice's
  AAC (under 0.5 MB a minute). Nothing is held before or after. Cold start is unchanged: nothing loads until a video
  is made.
- A dropped frame (none seen) still plays and is logged, not failed.
- The writer blocks and must run on one background thread, the one its GL context is bound to.
- `PIPELINE_VERSION` is unchanged: `:pdf` is not touched.

## 2b: the screen (2026-10-28)
1. **One card screen, two kinds.** A quote card has Picture and Video chips; "Share as a video" in the sentence menu
   opens it on Video. A video is always a story (9:16), Standard 720 x 1280 by default, Sharp 1080 x 1920 to choose.
2. **The card is the backdrop.** In video mode the card is drawn without its quote's words; Make the video captures
   it (look, cover or picture, credit, ListenLock mark) as a PNG in `cacheDir/shared-videos/`. The words are drawn
   by `QuoteVideoPainter` (StaticLayout, the same Tiro fonts, moved to `:playback`) over that backdrop, so the
   service needs no Compose. The screen's preview draws the same painter as an unrecorded overlay of the card frame,
   so what the listener sees is the video's first moment, label included.
3. **The book's own voice, no book open.** `QuoteVoice` builds the book's `BookVoice` over a stand-in text holding
   only the quote (no password, no page parsing), renders it a sentence at a time with the book's voices and
   settings, times its words (`WordTimes`), joins the sentences with a short gap and applies the book's pitch.
4. **A short foreground service** (`QuoteVideoService`, one at a time through `JobSlots.VIDEO`), like the audio
   clip's: progress in a notification, then the share sheet (and a Share button on the notification for when Android
   blocks opening it from the background). The three newest videos are kept for an app that reads one late.

### Not done yet (flagged, not dropped)
- Dragging an own picture to position it: the picture is centre-cropped, as on the picture card.

### Consequences of 2b
- Memory while making: the backdrop bitmap (3.7 MB at 720p, 8.3 MB at 1080p) on top of 2a's; the voice's model is
  the one reading uses, loaded only for the video and closed after. Sharp is about 2.25 times the pixels to draw and
  encode. Nothing new at cold start; the fonts moved module, the APK does not grow by them.
