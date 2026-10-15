# File: research/spikes/S2-pdf-extraction/scripts/rss_probe.py
# Purpose: Separate PDFium's own memory growth from the harness: extract without keeping text, optionally reopening the document every N pages.
import sys, psutil, pypdfium2 as pdfium
P = psutil.Process(); MB = 2**20
path, reopen = sys.argv[1], int(sys.argv[2])
doc = pdfium.PdfDocument(path); n = len(doc); r0 = P.memory_info().rss; peak = r0; marks = []
for i in range(n):
    if reopen and i and i % reopen == 0:
        doc.close(); doc = pdfium.PdfDocument(path)
    pg = doc[i]; tp = pg.get_textpage(); tp.get_text_range(); tp.close(); pg.close()
    rss = P.memory_info().rss; peak = max(peak, rss)
    if i in (n // 4, n // 2, 3 * n // 4, n - 1): marks.append(round((rss - r0) / MB, 1))
doc.close()
print(path.split('/')[-1], f"reopen={reopen}", "pages", n, "growth@25/50/75/100% MB", marks, "peak+", round((peak - r0) / MB, 1), "after close+", round((P.memory_info().rss - r0) / MB, 1))
