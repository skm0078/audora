# DjVuLibre 3.5.28 (vendored)

The `libdjvu` sources of DjVuLibre 3.5.28, unchanged, from
`djvulibre-3.5.28.tar.gz` (sha256 `fcd009ea7654fde5a83600eb80757bd3a76998e47d13c66b54c8db849f8f2edc`,
<https://sourceforge.net/projects/djvu/files/DjVuLibre/3.5.28/>). `COPYING`, `COPYRIGHT` and `AUTHORS` are the
release's own. Only `config.h` is ours, written by hand for Android (the release's `configure` cannot run in the
NDK build): pthreads, GCC atomics, no iconv, no JPEG decoder (DjVu's own codecs need none).

**Licence:** GNU GPL version 2 or later. ListenLock's community app is GPL-3.0, so it may include it. The paid Pro
app must never include or link it (ADR 2026-10-27-djvu-books).

To update: replace `libdjvu/` and the three notice files from a new release, check its sha256 against the release
page, update this file and the ADR, and run the DjVu tests (`DjvuFileTest` on a device, the release journey's step 15).
