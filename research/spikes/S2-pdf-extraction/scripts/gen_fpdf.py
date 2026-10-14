# File: research/spikes/S2-pdf-extraction/scripts/gen_fpdf.py
# Purpose: Second PDF producer (fpdf2 + HarfBuzz shaping) for the same paged UDHR ground truth, to compare ToUnicode behaviour across producers.
import json, sys
from fpdf import FPDF

FONTS = {"hi": "/usr/share/fonts/truetype/noto/NotoSerifDevanagari-Regular.ttf",
         "bn": "/usr/share/fonts/truetype/noto/NotoSerifBengali-Regular.ttf"}

for tag, font in FONTS.items():
    pages = json.load(open(f"/tmp/s2/html/{tag}-udhr-noto.gt.json", encoding="utf-8"))["pages"]
    pdf = FPDF(format="A4")
    pdf.add_font("ind", fname=font)
    pdf.set_font("ind", size=12)
    pdf.set_text_shaping(True)
    for text in pages:
        pdf.add_page()
        pdf.multi_cell(0, 8, text, align="J")
    pdf.output(f"/tmp/s2/pdf/{tag}-udhr-fpdf2.pdf")
    json.dump({"mode": "paged", "pages": pages}, open(f"/tmp/s2/html/{tag}-udhr-fpdf2.gt.json", "w", encoding="utf-8"), ensure_ascii=False)
    print(tag, len(pages))
