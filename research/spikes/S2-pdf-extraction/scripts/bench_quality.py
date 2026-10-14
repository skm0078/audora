# File: research/spikes/S2-pdf-extraction/scripts/bench_quality.py
# Purpose: S2 quality bench on ground-truth docs: PDFium vs PdfBox extraction, Tesseract (fast/best) vs PaddleOCR on simulated scans.
import json, subprocess, sys, time, statistics as st
from pathlib import Path
import cv2
sys.path.insert(0, "/tmp/s2/scripts")
from metrics import cer, invalid_sign_count, prebase_word_recall, strip_furniture
from ext_pdfium import run as pdfium_run
from ocr_paddle import PaddleOcr

S2 = Path("/tmp/s2"); RES = S2 / "results"; RES.mkdir(exist_ok=True)
DIGITAL = ["hi-udhr-noto", "hi-udhr-lohit", "hi-udhr-fpdf2", "bn-udhr-noto", "bn-udhr-lohit", "bn-udhr-fpdf2", "en-udhr-noto"]
SCANNED = ["hi-udhr-noto", "bn-udhr-noto", "en-udhr-noto"]
PDFBOX = ["java", "-Xmx512m", "-cp", f"{S2}/tools/pdfbox-app-2.0.27.jar:{S2}/java", "PdfBoxBench"]


def gt_pages(name):
    return json.load(open(S2 / "html" / f"{name}.gt.json", encoding="utf-8"))["pages"]


def score(gt, hyps):
    rows = [dict(cer=cer(g, strip_furniture(h)), cer_nows=cer(g, strip_furniture(h), True),
                 bad_signs=invalid_sign_count(strip_furniture(h)), gt_bad=invalid_sign_count(g),
                 prebase=prebase_word_recall(g, strip_furniture(h))) for g, h in zip(gt, hyps)]
    tot = sum(len(g) for g in gt)
    agg = lambda k: sum(r[k] * len(g) for r, g in zip(rows, gt)) / tot
    pb = [r["prebase"] for r in rows if r["prebase"] is not None]
    return {"cer": round(agg("cer"), 4), "cer_nows": round(agg("cer_nows"), 4),
            "bad_signs": sum(r["bad_signs"] for r in rows) - sum(r["gt_bad"] for r in rows),
            "prebase_recall": round(st.mean(pb), 4) if pb else None}


def pdfbox_pages(pdf):
    out = S2 / "results" / "pdfbox.tmp.jsonl"
    subprocess.run(PDFBOX + [str(pdf), str(out)], check=True, capture_output=True)
    lines = [json.loads(l) for l in open(out, encoding="utf-8")]
    return lines[1:]


def extraction():
    res = {}
    for name in DIGITAL:
        gt = gt_pages(name)
        pr = pdfium_run(str(S2 / "pdf" / f"{name}.pdf"))
        pb = pdfbox_pages(S2 / "pdf" / f"{name}.pdf")
        res[name] = {
            "pdfium": {**score(gt, [p["text"] for p in pr["pages"]]), "ms_page": round(st.median(pr["ms_per_page"]), 2)},
            "pdfium_nogen": score(gt, [p["text_nogen"] for p in pr["pages"]]),
            "pdfbox": {**score(gt, [p["text"] for p in pb]), "ms_page": round(st.median(p["ms"] for p in pb[1:] or pb), 2)},
            "generated_share": round(sum(p["generated"] for p in pr["pages"]) / max(1, sum(p["chars"] for p in pr["pages"])), 4),
            "map_err": sum(p["map_err"] for p in pr["pages"]),
            "font_name_rate": min(p["font_name_rate"] or 0 for p in pr["pages"]),
            "font_size_rate": min(p["font_size_rate"] or 0 for p in pr["pages"]),
            "fonts": list(pr["fonts"])[:4],
            "sample_pdfium": pr["pages"][1]["text"][:240], "sample_pdfbox": pb[1]["text"][:240],
        }
        print(name, json.dumps({k: v for k, v in res[name].items() if not k.startswith("sample")}, ensure_ascii=False), flush=True)
    return res


def tesseract(img, lang, tessdata):
    t = time.perf_counter()
    r = subprocess.run(["tesseract", str(img), "stdout", "-l", lang, "--psm", "3", "--tessdata-dir", tessdata],
                       capture_output=True, text=True, env={"OMP_THREAD_LIMIT": "4", "PATH": "/usr/bin"})
    return r.stdout, (time.perf_counter() - t) * 1000


def ocr():
    res = {}
    paddle = {"hi": PaddleOcr("devanagari_pp-ocrv5_mobile_rec.onnx", "ppocrv5_devanagari_dict.txt"),
              "en": PaddleOcr("en_pp-ocrv5_mobile_rec.onnx", "ppocrv5_en_dict.txt")}
    for name in SCANNED:
        tag = name[:2]; lang = {"hi": "hin", "bn": "ben", "en": "eng"}[tag]
        gt = gt_pages(name)
        for variant in ("scan300", "scan200bad"):
            imgs = sorted((S2 / "scans").glob(f"{name}.{variant}.p*.jpg"))
            key = f"{name}.{variant}"; res[key] = {}
            for eng, td in (("tess_fast", "/usr/share/tesseract-ocr/5/tessdata"), ("tess_best", str(S2 / "tessdata_best"))):
                outs = [tesseract(i, lang, td) for i in imgs]
                res[key][eng] = {**score(gt, [o[0] for o in outs]), "ms_page": round(st.median(o[1] for o in outs))}
            if tag in paddle:
                outs = []
                for i in imgs:
                    t = time.perf_counter(); txt, _ = paddle[tag].page_text(cv2.imread(str(i)))
                    outs.append((txt, (time.perf_counter() - t) * 1000))
                res[key]["paddle_v5_mobile"] = {**score(gt, [o[0] for o in outs]), "ms_page": round(st.median(o[1] for o in outs))}
            print(key, json.dumps(res[key]), flush=True)
    return res


if __name__ == "__main__":
    which = sys.argv[1]
    out = extraction() if which == "extraction" else ocr()
    json.dump(out, open(RES / f"{which}.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)
