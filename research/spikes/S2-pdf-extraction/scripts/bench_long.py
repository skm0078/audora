# File: research/spikes/S2-pdf-extraction/scripts/bench_long.py
# Purpose: No-page-cap evidence: page-at-a-time PDFium + PdfBox over 400-1200 page books; ms/page, RSS/heap curve, random page access, word recall.
import json, subprocess, sys, time, statistics as st, os
from pathlib import Path
import psutil
sys.path.insert(0, "/tmp/s2/scripts")
import pypdfium2 as pdfium
from metrics import word_recall, strip_furniture

S2 = Path("/tmp/s2"); PROC = psutil.Process()
PDFBOX = ["java", "-Xmx256m", "-cp", f"{S2}/tools/pdfbox-app-2.0.27.jar:{S2}/java", "PdfBoxBench"]
MB = 1024 * 1024


def pct(xs, q):
    xs = sorted(xs); return xs[min(len(xs) - 1, int(q * len(xs)))]


def pdfium_pass(path):
    rss0 = PROC.memory_info().rss
    t0 = time.perf_counter(); doc = pdfium.PdfDocument(str(path)); t_open = (time.perf_counter() - t0) * 1000
    n = len(doc); times, rss, texts = [], [], []
    for i in range(n):
        t = time.perf_counter()
        pg = doc[i]; tp = pg.get_textpage(); txt = tp.get_text_range(); tp.close(); pg.close()
        times.append((time.perf_counter() - t) * 1000); rss.append(PROC.memory_info().rss); texts.append(txt)
    doc.close()
    # random access: reopen, jump to last/middle page (start-from-any-page)
    t = time.perf_counter(); doc = pdfium.PdfDocument(str(path)); pg = doc[n - 1]; tp = pg.get_textpage(); tp.get_text_range()
    jump_ms = (time.perf_counter() - t) * 1000; tp.close(); pg.close(); doc.close()
    first_q = st.mean(rss[: n // 4]) / MB; last_q = st.mean(rss[-n // 4:]) / MB
    return {"pages": n, "open_ms": round(t_open, 1), "ms_median": round(st.median(times), 2), "ms_p95": round(pct(times, .95), 2),
            "ms_max": round(max(times), 1), "total_s": round(sum(times) / 1000, 2), "jump_to_last_ms": round(jump_ms, 1),
            "rss_start_mb": round(rss0 / MB, 1), "rss_peak_mb": round(max(rss) / MB, 1),
            "rss_growth_firstq_to_lastq_mb": round(last_q - first_q, 1)}, texts


def pdfbox_pass(path, max_pages):
    out = S2 / "results" / "pdfbox_long.tmp.jsonl"
    t = time.perf_counter()
    p = subprocess.run(PDFBOX + [str(path), str(out), str(max_pages)], capture_output=True, text=True)
    wall = time.perf_counter() - t
    if p.returncode != 0:
        return {"error": p.stderr.strip().splitlines()[-1][:200]}
    lines = [json.loads(l) for l in open(out, encoding="utf-8")]
    pages = lines[1:]; ms = [x["ms"] for x in pages]; heap = [x["heap"] for x in pages]
    return {"pages_run": len(pages), "open_ms": round(lines[0]["open_ms"], 1), "ms_median": round(st.median(ms), 2), "ms_p95": round(pct(ms, .95), 2),
            "heap_peak_mb": round(max(heap) / MB, 1), "wall_s_incl_jvm": round(wall, 1)}


if __name__ == "__main__":
    res = {}
    for name in sys.argv[1:]:
        path = S2 / "pdf" / f"{name}.pdf"
        r, texts = pdfium_pass(path)
        gtf = S2 / "html" / f"{name}.gt.json"
        if gtf.exists():
            gt = json.load(open(gtf, encoding="utf-8"))
            ref = gt.get("text") or " ".join(gt["pages"])
            r["word_recall"] = round(word_recall(ref, strip_furniture("\n".join(texts))), 4)
        r["size_mb"] = round(os.path.getsize(path) / MB, 2)
        res[name] = {"pdfium": r, "pdfbox": pdfbox_pass(path, r["pages"])}
        print(name, json.dumps(res[name]), flush=True)
    json.dump(res, open(S2 / "results" / "long.json", "w"), indent=1)
