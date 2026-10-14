# File: research/spikes/S2-pdf-extraction/scripts/objtext.py
# Purpose: Rebuild page text from PDFium page objects, honouring /ActualText spans and real space glyphs (prototype for the font-info/ActualText JNI).
import ctypes, json, sys
sys.path.insert(0, "/tmp/s2/scripts")
import pypdfium2 as pdfium, pypdfium2.raw as raw
from metrics import cer, prebase_word_recall, strip_furniture

BUF = (ctypes.c_ubyte * 4096)()


def actual_text(obj):
    for m in range(raw.FPDFPageObj_CountMarks(obj)):
        mk = raw.FPDFPageObj_GetMark(obj, m)
        outlen = ctypes.c_ulong()
        if raw.FPDFPageObjMark_GetParamBlobValue(mk, b"ActualText", BUF, 4096, ctypes.byref(outlen)):
            raw_b = bytes(BUF[:outlen.value])
            txt = raw_b[2:].decode("utf-16-be", "replace") if raw_b[:2] == b"\xfe\xff" else raw_b.decode("latin-1")
            return ctypes.cast(mk, ctypes.c_void_p).value, txt
    return None, None


def obj_text(obj, tp):
    n = raw.FPDFTextObj_GetText(obj, tp, None, 0)
    b = ctypes.create_string_buffer(n)
    raw.FPDFTextObj_GetText(obj, tp, ctypes.cast(b, ctypes.POINTER(raw.FPDF_WCHAR)), n)
    return b.raw[: max(0, n - 2)].decode("utf-16-le", "replace")


def page_text(page, tp, wrap_back=20.0):
    """Content-order rebuild: one ActualText per span, zero-width empty objects are space glyphs, a big leftward jump is a new line."""
    out, last_span, last_r, base = [], None, None, None
    l, b, r, t = (ctypes.c_float() for _ in range(4))
    for i in range(raw.FPDFPage_CountObjects(page)):
        o = raw.FPDFPage_GetObject(page, i)
        if raw.FPDFPageObj_GetType(o) != raw.FPDF_PAGEOBJ_TEXT:
            continue
        raw.FPDFPageObj_GetBounds(o, l, b, r, t)
        span, at = actual_text(o)
        if span is not None and span == last_span:
            last_r = max(last_r, r.value); continue
        if last_r is not None and l.value < last_r - wrap_back and abs(b.value - base) > 2:
            out.append("\n"); base = b.value
        if base is None:
            base = b.value
        if span is not None:
            out.append(at)
        else:
            txt = obj_text(o, tp).rstrip(" ")
            out.append(txt if (txt or r.value - l.value > 0.01) else " ")
        last_span, last_r = span, r.value
    return "".join(out)


if __name__ == "__main__":
    for name in sys.argv[1:]:
        gt = json.load(open(f"/tmp/s2/html/{name}.gt.json", encoding="utf-8"))["pages"]
        doc = pdfium.PdfDocument(f"/tmp/s2/pdf/{name}.pdf")
        hyps = []
        for i in range(len(doc)):
            pg = doc[i]; tp = pg.get_textpage(); hyps.append(strip_furniture(page_text(pg, tp))); tp.close()
        tot = sum(map(len, gt))
        c = sum(cer(g, h) * len(g) for g, h in zip(gt, hyps)) / tot
        cn = sum(cer(g, h, True) * len(g) for g, h in zip(gt, hyps)) / tot
        pb = sum(prebase_word_recall(g, h) or 0 for g, h in zip(gt, hyps)) / len(gt)
        print(f"{name}: cer={c:.4f} cer_nows={cn:.4f} prebase_recall={pb:.3f}")
        print("   ", hyps[1][:160].replace("\n", " | "))
