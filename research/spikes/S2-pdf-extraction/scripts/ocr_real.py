# File: research/spikes/S2-pdf-extraction/scripts/ocr_real.py
# Purpose: OCR real-world pages (no ground truth) with Tesseract fast/best and PaddleOCR, for hand-checking against the rendered image.
import sys, time, subprocess, json
sys.path.insert(0, "/tmp/s2/scripts")
import cv2, numpy as np, pypdfium2 as pdfium
from ocr_paddle import PaddleOcr

def render(pdf, idx, dpi=300):
    pg = pdfium.PdfDocument(pdf)[idx]
    img = np.array(pg.render(scale=dpi / 72).to_pil().convert("RGB"))[:, :, ::-1]
    return img

def tess(path, lang, td):
    t = time.perf_counter()
    r = subprocess.run(["tesseract", path, "stdout", "-l", lang, "--psm", "3", "--tessdata-dir", td], capture_output=True, text=True, env={"OMP_THREAD_LIMIT": "4", "PATH": "/usr/bin"})
    return r.stdout, (time.perf_counter() - t) * 1000

pdf, idx, lang = sys.argv[1], int(sys.argv[2]), sys.argv[3]
img = render(pdf, idx); p = f"/tmp/s2/real_{idx}.png"; cv2.imwrite(p, img)
out = {}
for k, td in (("tess_fast", "/usr/share/tesseract-ocr/5/tessdata"), ("tess_best", "/tmp/s2/tessdata_best")):
    out[k] = tess(p, lang, td)
if lang in ("hin", "eng"):
    o = PaddleOcr(*(("devanagari_pp-ocrv5_mobile_rec.onnx", "ppocrv5_devanagari_dict.txt") if lang == "hin" else ("en_pp-ocrv5_mobile_rec.onnx", "ppocrv5_en_dict.txt")))
    t = time.perf_counter(); txt, _ = o.page_text(img); out["paddle"] = (txt, (time.perf_counter() - t) * 1000)
for k, (txt, ms) in out.items():
    print(f"===== {k} {ms:.0f} ms"); print(txt.strip()[: int(sys.argv[4]) if len(sys.argv) > 4 else 400])
