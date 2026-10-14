# File: research/spikes/S2-pdf-extraction/scripts/spacefix.py
# Purpose: Test re-deciding PDFium's generated spaces from glyph-box gaps (fix for split Indic words).
import ctypes, json, sys
sys.path.insert(0, "/tmp/s2/scripts")
import pypdfium2 as pdfium, pypdfium2.raw as raw
from metrics import cer, prebase_word_recall, strip_furniture


def page_text(tp, k):
    n = raw.FPDFText_CountChars(tp)
    l, r, b, t = (ctypes.c_double() for _ in range(4))
    out, max_right, pending = [], None, False
    for i in range(n):
        u = raw.FPDFText_GetUnicode(tp, i)
        ch = chr(u) if u else ""
        gen = raw.FPDFText_IsGenerated(tp, i) == 1
        if ch in "\r\n":
            out.append("\n"); max_right = None; pending = False; continue
        if ch == " ":
            if not gen:
                out.append(" "); max_right = None
            else:
                pending = True
            continue
        raw.FPDFText_GetCharBox(tp, i, l, r, b, t)
        fs = raw.FPDFText_GetFontSize(tp, i) or 10
        if pending:
            if max_right is None or l.value - max_right > k * fs:
                out.append(" ")
            pending = False
        out.append(ch)
        if r.value > l.value:  # zero-width marks keep the cluster's right edge
            max_right = r.value if max_right is None else max(max_right, r.value)
    return "".join(out)


for name in sys.argv[1:]:
    gt = json.load(open(f"/tmp/s2/html/{name}.gt.json", encoding="utf-8"))["pages"]
    doc = pdfium.PdfDocument(f"/tmp/s2/pdf/{name}.pdf")
    for k in (0.0, 0.1, 0.15, 0.2, 0.25, 0.3):
        hyps = []
        for i in range(len(doc)):
            tp = doc[i].get_textpage(); hyps.append(strip_furniture(page_text(tp, k))); tp.close()
        tot = sum(map(len, gt))
        c = sum(cer(g, h) * len(g) for g, h in zip(gt, hyps)) / tot
        pb = sum(prebase_word_recall(g, h) or 0 for g, h in zip(gt, hyps)) / len(gt)
        print(f"{name} k={k}: cer={c:.4f} prebase_recall={pb:.3f}")
