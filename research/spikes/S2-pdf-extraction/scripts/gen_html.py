# File: research/spikes/S2-pdf-extraction/scripts/gen_html.py
# Purpose: Build HTML sources + ground-truth JSON for the self-generated S2 corpus (rendered to PDF by print.js).
import json, re, sys, unicodedata
from pathlib import Path

GT = Path("/tmp/s2/gt")
OUT = Path("/tmp/s2/html")
OUT.mkdir(parents=True, exist_ok=True)
PAGE_CHARS = {"hin": 1100, "ben": 1100, "eng": 1500}


def udhr_paras(lang):
    text = (GT / "udhr2" / f"{lang}.txt").read_text("utf-8")
    text = unicodedata.normalize("NFC", text)
    return [re.sub(r"\s+", " ", p).strip() for p in text.split("\n") if p.strip()]


def chunk(paras, limit):
    pages, cur = [], []
    for p in paras:
        if cur and sum(len(x) for x in cur) + len(p) > limit:
            pages.append(cur); cur = []
        cur.append(p)
    if cur:
        pages.append(cur)
    return pages


def esc(s):
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


CSS = """
@page { size: A4; margin: 22mm 18mm; }
body { font-family: '%s'; font-size: %spt; line-height: 1.55; %s }
.pg { page-break-after: always; }
p { margin: 0 0 0.6em 0; text-align: justify; }
h1 { font-size: 16pt; }
"""


def paged_doc(name, lang, font, size=12):
    pages = chunk(udhr_paras(lang), PAGE_CHARS[lang])
    body = "".join('<div class="pg">' + "".join(f"<p>{esc(p)}</p>" for p in pg) + "</div>" for pg in pages)
    html = f"<html lang='{lang[:2]}'><head><meta charset='utf-8'><style>{CSS % (font, size, '')}</style></head><body>{body}</body></html>"
    (OUT / f"{name}.html").write_text(html, "utf-8")
    (OUT / f"{name}.gt.json").write_text(json.dumps({"mode": "paged", "pages": [" ".join(pg) for pg in pages]}, ensure_ascii=False), "utf-8")


def flow_doc(name, lang, font, paras, cols=1, size=11, title=""):
    extra = ""
    inner = "".join(f"<p>{esc(p)}</p>" for p in paras)
    if cols > 1:
        inner = f"<div style='column-count:{cols}; column-gap:8mm; column-fill:auto'>{inner}</div>"
    body = (f"<h1>{esc(title)}</h1>" if title else "") + inner
    html = f"<html lang='{lang[:2]}'><head><meta charset='utf-8'><style>{CSS % (font, size, extra)} p {{ hyphens: auto; }}</style></head><body>{body}</body></html>"
    (OUT / f"{name}.html").write_text(html, "utf-8")
    (OUT / f"{name}.gt.json").write_text(json.dumps({"mode": "flow", "text": " ".join(paras)}, ensure_ascii=False), "utf-8")


def WS_JOIN(t):
    return re.sub(r"\s+", " ", t)


def gutenberg_paras(fname):
    t = (GT / "gutenberg" / fname).read_text("latin-1")
    return [re.sub(r"\s+", " ", p).strip() for p in re.split(r"\n\s*\n", t) if p.strip()]


if __name__ == "__main__":
    for lang, tag in (("hin", "hi"), ("ben", "bn"), ("eng", "en")):
        fam = {"hin": "Devanagari", "ben": "Bengali", "eng": "Latin"}[lang]
        paged_doc(f"{tag}-udhr-noto", lang, f"Noto Serif {fam}" if lang != "eng" else "Noto Serif")
        if lang != "eng":
            paged_doc(f"{tag}-udhr-lohit", lang, f"Lohit {fam}")
    flow_doc("en-mobydick-1col-long", "eng", "Noto Serif", gutenberg_paras("melville-moby_dick.txt"), title="Moby Dick")
    flow_doc("en-kjv-2col-long", "eng", "Noto Serif", [v.strip() for v in re.split(r"(?=\b\d+:\d+ )", WS_JOIN((GT / "gutenberg" / "bible-kjv.txt").read_text("latin-1"))) if v.strip()], cols=2, size=9.5, title="The King James Bible")
    # Very long Indic docs: UDHR repeated to reach ~1000+ pages (memory/time test, not quality).
    for lang, tag, fam in (("hin", "hi", "Devanagari"), ("ben", "bn", "Bengali")):
        flow_doc(f"{tag}-udhr-x200-long", lang, f"Noto Serif {fam}", udhr_paras(lang) * 200, size=12)
    # Bilingual: interleave English and Hindi UDHR paragraphs
    en, hi = udhr_paras("eng"), udhr_paras("hin")
    mixed = [x for pair in zip(en, hi) for x in pair]
    flow_doc("enhi-udhr-bilingual", "hin", "Noto Serif Devanagari', 'Noto Serif", mixed)
    print("ok", sorted(p.name for p in OUT.glob("*.html")))
