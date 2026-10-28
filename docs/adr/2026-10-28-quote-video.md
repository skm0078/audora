# ADR 2026-10-28-quote-video: a quote video from the phone's own encoders, its frames timed through OpenGL

**Status:** Accepted (2026-10-28). Part 2a of the share plan (share cards, quote video, page flips), approved by the
maintainer for v0.1.0. 2b adds the screen that makes one from a share card.

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
