# File: research/spikes/S2-pdf-extraction/scripts/ext_pdfium.py
# Purpose: Page-at-a-time PDFium extraction (same engine as PdfiumAndroidKt) with per-page timing, RSS, generated-char and font-info stats.
import ctypes, json, sys, time, os
import psutil
import pypdfium2 as pdfium
import pypdfium2.raw as raw

PROC = psutil.Process()


def char_stats(tp):
    n = raw.FPDFText_CountChars(tp)
    gen = map_err = font_named = font_sized = 0
    fonts = {}
    buf = ctypes.create_string_buffer(256)
    flags = ctypes.c_int()
    kept = []
    for i in range(n):
        u = raw.FPDFText_GetUnicode(tp, i)
        g = raw.FPDFText_IsGenerated(tp, i)
        if g == 1:
            gen += 1
        else:
            kept.append(chr(u) if u else "")
        if raw.FPDFText_HasUnicodeMapError(tp, i) == 1:
            map_err += 1
        if g != 1:
            ln = raw.FPDFText_GetFontInfo(tp, i, buf, 256, ctypes.byref(flags))
            if ln > 1:
                font_named += 1
                name = buf.value.decode("utf-8", "replace")
                fonts[name] = fonts.get(name, 0) + 1
            if raw.FPDFText_GetFontSize(tp, i) > 0:
                font_sized += 1
    real = n - gen
    return {"chars": n, "generated": gen, "map_err": map_err,
            "font_name_rate": round(font_named / real, 4) if real else None,
            "font_size_rate": round(font_sized / real, 4) if real else None,
            "fonts": fonts, "text_nogen": "".join(kept)}


def run(path, stats=True, max_pages=None):
    t0 = time.perf_counter()
    doc = pdfium.PdfDocument(path)
    t_open = (time.perf_counter() - t0) * 1000
    n = len(doc)
    pages, times, rss = [], [], []
    rss0 = PROC.memory_info().rss
    fonts_all = {}
    for i in range(n if max_pages is None else min(n, max_pages)):
        t = time.perf_counter()
        page = doc[i]
        tp = page.get_textpage()
        text = tp.get_text_range()
        times.append((time.perf_counter() - t) * 1000)
        rec = {"page": i + 1, "text": text}
        if stats:
            cs = char_stats(tp)
            for k, v in cs.pop("fonts").items():
                fonts_all[k] = fonts_all.get(k, 0) + v
            rec.update(cs)
        tp.close(); page.close()
        rss.append(PROC.memory_info().rss)
        pages.append(rec)
    doc.close()
    return {"file": os.path.basename(path), "pages_total": n, "open_ms": round(t_open, 2),
            "ms_per_page": times, "rss_start": rss0, "rss_per_page": rss, "fonts": fonts_all, "pages": pages}


if __name__ == "__main__":
    out = run(sys.argv[1], stats=(sys.argv[3] != "nostats") if len(sys.argv) > 3 else True)
    json.dump(out, open(sys.argv[2], "w", encoding="utf-8"), ensure_ascii=False)
