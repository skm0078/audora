# File: research/spikes/S2-pdf-extraction/scripts/analyze_corpus.py
# Purpose: Run the proposed per-page quality router over real-world PDFs: OK / LEGACY / OCR, with the signals that drove each decision.
import json, sys, time, statistics as st, os, traceback
from collections import Counter
from pathlib import Path
sys.path.insert(0, "/tmp/s2/scripts")
import pypdfium2 as pdfium
from ext_pdfium import char_stats
from metrics import invalid_sign_count, script_share, LEGACY_FONT_PAT

# Router thresholds (candidate QualityConfig defaults)
MIN_CHARS_PER_PAGE = 40          # below: treat as image-only page
MAX_MAP_ERR_RATE = 0.02          # FPDFText_HasUnicodeMapError share of glyph chars
MAX_INVALID_SIGNS_PER_1K = 8     # dependent vowel signs without a base, per 1000 Indic chars
MAX_GARBAGE_RATE = 0.15          # U+FFFD / private-use / control chars share
SAMPLE_PAGES = 40


def garbage_rate(text):
    bad = sum(1 for c in text if c == "�" or 0xE000 <= ord(c) <= 0xF8FF or (ord(c) < 32 and c not in "\r\n\t"))
    return bad / max(1, len(text))


def route(text, cs):
    real = cs["chars"] - cs["generated"]
    if real < MIN_CHARS_PER_PAGE:
        return "OCR", "no text layer"
    if any(LEGACY_FONT_PAT.search(f.split("+")[-1]) for f in cs["page_fonts"]):
        return "LEGACY", "legacy font name"
    if cs["map_err"] / max(1, real) > MAX_MAP_ERR_RATE:
        return "OCR", f"unicode map errors {cs['map_err']}/{real}"
    if garbage_rate(text) > MAX_GARBAGE_RATE:
        return "OCR", "garbage chars"
    indic = sum(1 for c in text if 0x0900 <= ord(c) <= 0x09FF)
    if indic and invalid_sign_count(text) * 1000 / indic > MAX_INVALID_SIGNS_PER_1K:
        return "REORDER", f"invalid signs {invalid_sign_count(text)}/{indic}"
    return "OK", ""


def analyze(path):
    t0 = time.perf_counter(); doc = pdfium.PdfDocument(str(path)); open_ms = (time.perf_counter() - t0) * 1000
    n = len(doc); idx = sorted(set(range(0, n, max(1, n // SAMPLE_PAGES)))) if n > SAMPLE_PAGES else range(n)
    routes, why, ms, fonts, sample = Counter(), Counter(), [], Counter(), ""
    share = Counter()
    for i in idx:
        t = time.perf_counter(); pg = doc[i]; tp = pg.get_textpage(); text = tp.get_text_range(); ms.append((time.perf_counter() - t) * 1000)
        cs = char_stats(tp); cs["page_fonts"] = list(cs["fonts"]); fonts.update(cs["fonts"])
        r, w = route(text, cs); routes[r] += 1
        if w: why[w.split(" ")[0] + " " + w.split(" ")[1] if " " in w else w] += 1
        for k, v in script_share(text).items(): share[k] += v
        if not sample and len(text.strip()) > 80: sample = text.strip()[:160].replace("\r", "").replace("\n", " ")
        tp.close(); pg.close()
    doc.close()
    return {"file": str(path).split("test-pdfs/")[-1], "pages": n, "mb": round(os.path.getsize(path) / 2**20, 1), "open_ms": round(open_ms, 1),
            "ms_page_median": round(st.median(ms), 2), "routes": dict(routes), "why": dict(why),
            "script": {k: round(v / len(idx), 2) for k, v in share.items()},
            "fonts": [f.split("+")[-1] for f, _ in fonts.most_common(4)], "sample": sample}


if __name__ == "__main__":
    root = Path(sys.argv[1]); out = []
    for p in sorted(root.rglob("*.pdf")):
        if "/_" in str(p):
            continue
        try:
            r = analyze(p)
        except Exception as e:  # report and continue: one bad file must not hide the rest
            r = {"file": str(p), "error": f"{type(e).__name__}: {e}"}
        out.append(r); print(json.dumps(r, ensure_ascii=False), flush=True)
    json.dump(out, open(sys.argv[2], "w", encoding="utf-8"), ensure_ascii=False, indent=1)
