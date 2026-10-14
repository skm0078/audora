# File: research/spikes/S2-pdf-extraction/scripts/make_scans.py
# Purpose: Simulated scans of ground-truth pages (300 dpi clean, 200 dpi degraded) + image-only PDFs for the corpus.
import sys, io
import numpy as np, cv2, img2pdf
import pypdfium2 as pdfium
from pathlib import Path

OUT = Path("/tmp/s2/scans"); OUT.mkdir(exist_ok=True)
rng = np.random.default_rng(7)

def degrade(img, angle, noise, jpeg_q):
    g = cv2.cvtColor(img, cv2.COLOR_RGB2GRAY)
    h, w = g.shape
    M = cv2.getRotationMatrix2D((w / 2, h / 2), angle, 1.0)
    g = cv2.warpAffine(g, M, (w, h), borderValue=255)
    g = cv2.GaussianBlur(g, (3, 3), 0.8)
    g = np.clip(g.astype(np.float32) * 0.92 + 12 + rng.normal(0, noise, g.shape), 0, 255).astype(np.uint8)
    ok, enc = cv2.imencode(".jpg", g, [cv2.IMWRITE_JPEG_QUALITY, jpeg_q])
    return enc.tobytes()

for name in sys.argv[1:]:
    doc = pdfium.PdfDocument(f"/tmp/s2/pdf/{name}.pdf")
    for variant, dpi, angle, noise, q in (("scan300", 300, 0.6, 6, 75), ("scan200bad", 200, -1.2, 14, 45)):
        jpgs = []
        for i in range(len(doc)):
            img = np.array(doc[i].render(scale=dpi / 72).to_pil().convert("RGB"))
            b = degrade(img, angle, noise, q)
            (OUT / f"{name}.{variant}.p{i+1:03d}.jpg").write_bytes(b)
            jpgs.append(b)
        Path(f"/tmp/s2/pdf/{name}-{variant}.pdf").write_bytes(img2pdf.convert(jpgs, layout_fun=img2pdf.get_fixed_dpi_layout_fun((dpi, dpi))))
    print("scanned", name, len(doc))
