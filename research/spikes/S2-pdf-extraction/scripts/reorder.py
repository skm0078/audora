# File: research/spikes/S2-pdf-extraction/scripts/reorder.py
# Purpose: Minimal visual-to-logical reorder for pre-base matras (ि ি ে ৈ) and Bengali two-part vowels; measured against OCR of the same page.
import re, sys, subprocess, unicodedata
sys.path.insert(0, "/tmp/s2/scripts")
import numpy as np, cv2, pypdfium2 as pdfium
from metrics import cer, invalid_sign_count

CONS = "[क-हक़-य़ক-হড়-য়ৎ]"
NUKTA = "[़়]?"
VIRAMA = "[्্]"
CLUSTER = f"(?:{CONS}{NUKTA}{VIRAMA})*{CONS}{NUKTA}"
PRE = "[िিেৈ]"
RX = re.compile(f"({PRE})({CLUSTER})")


def reorder(text):
    t = RX.sub(lambda m: m.group(2) + m.group(1), text)
    t = t.replace("ো", "ো").replace("ৌ", "ৌ")  # ে+া → ো, ে+ৗ → ৌ
    return unicodedata.normalize("NFC", t)


def ocr(pdf, i, lang):
    img = np.array(pdfium.PdfDocument(pdf)[i].render(scale=300 / 72).to_pil().convert("RGB"))
    cv2.imwrite("/tmp/s2/_r.png", img)
    return subprocess.run(["tesseract", "/tmp/s2/_r.png", "stdout", "-l", lang, "--tessdata-dir", "/tmp/s2/tessdata_best"], capture_output=True, text=True).stdout


if __name__ == "__main__":
    for pdf, pages, lang in [("real/hindi/hi-us-hud-factsheet-pd.pdf", [1, 2], "hin"), ("real/bengali/bn-us-fda-factsheet-vrinda-pd.pdf", [1, 2], "ben"),
                             ("real/hindi/hi-govreport-jalshakti-annual-2019-20.pdf", [20, 40], "hin"), ("real/hindi/hi-ncert-preschool-curriculum.pdf", [20, 40], "hin"),
                             ("real/tricky/hi-broken-tounicode-planning-commission-ssa.pdf", [20, 60], "hin"), ("real/bengali/bn-gov-bd-cabinet-apa-nikosh.pdf", [10, 30], "ben"),
                             ("real/bengali/bn-us-hud-fairlending-guide-pd.pdf", [5, 20], "ben")]:
        doc = pdfium.PdfDocument(pdf)
        for i in pages:
            raw_t = doc[i].get_textpage().get_text_range(); ref = ocr(pdf, i, lang)
            ro = reorder(raw_t)
            print(f"{pdf.split('/')[-1]} p{i+1}: vs-OCR CER raw={cer(ref, raw_t):.3f} reordered={cer(ref, ro):.3f} | invalid signs raw={invalid_sign_count(raw_t)} reordered={invalid_sign_count(ro)}", flush=True)
