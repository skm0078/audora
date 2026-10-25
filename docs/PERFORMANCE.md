# Performance: how speed is measured (ADR-072)

Judge speed only on the **dev build** (`audora-dev.apk`, "ListenLock Dev" with version `-dev`), never on
`listenlock-debug.apk`. The debug build is several times slower by design (ADR-072).

## On every push (CI, no phone)
The device-tests job times the debug build against the dev build on the same x86_64 emulator
(`scripts/perf/time_builds.sh`). The table is on the run's summary page, under "Speed numbers". Emulator numbers are
not phone numbers. Read the ratio between the two builds, and compare against the last run on `dev`.

## On a phone (5 minutes, no PC)
1. **Share logs after a normal session** (Settings → Share logs). The lines to look for:
   - `Audora.RenderSpeed  Rendered voice=… audio=3200ms in 1900ms rtf=0.59 first`: one per sentence. `rtf` is render
     time divided by audio length; above 1.0 the voice is slower than real time and playback will stall. `first`
     marks a sentence whose time includes loading the voice, so its `in …ms` is the wait after opening a book or
     changing voice.
   - `Start held … ms`: how long the first sentence waited for enough audio before playing.
   - `Audora.Stall  Main thread stuck for 2000+ ms at …`: a freeze, with the code that caused it.
2. **Record a trace with System Tracing** (built into Android, nothing to install):
   - Turn on Developer options: Settings → About phone → tap Build number 7 times.
   - Settings → System → Developer options → System Tracing. Turn on "Show Quick Settings tile".
   - Pull down Quick Settings, tap "Record trace", do the slow thing (open a book and press play; scroll the
     library), then tap the tile again to stop.
   - The trace is saved as a notification. Share it (Drive, or email to yourself), then open it at
     https://ui.perfetto.dev (it runs in the browser; the file never leaves the PC). Send the file in the thread.
     The ListenLock Dev rows show main-thread work, frames (long ones are red) and the `audora-` worker threads.

## On a phone with a PC (adb, PowerShell)
Install platform-tools (https://developer.android.com/tools/releases/platform-tools), turn on USB debugging, then:

```
# Cold start, 5 times: TotalTime is launch to first frame, in ms
1..5 | % { adb shell am start -S -W -n io.github.skm0078.audora.debug/io.github.skm0078.audora.MainActivity | Select-String TotalTime }

# Frames: reset, use the app for 30 s (scroll the library, play a book), then read the summary
adb shell dumpsys gfxinfo io.github.skm0078.audora.debug reset
adb shell dumpsys gfxinfo io.github.skm0078.audora.debug | Select-String "Janky|percentile|Total frames"

# Memory
adb shell dumpsys meminfo io.github.skm0078.audora.debug | Select-String "TOTAL PSS"
```

The frame budget is about 7 ms at 144 Hz and about 16 ms at 60 Hz. Janky frames above about
5 % are visible stutter.
