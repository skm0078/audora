#!/usr/bin/env python3
# File: scripts/pdf/legacy_golden.py
# Purpose: Generates the golden corpus's legacy-font PDFs (P3-04): UDHR Hindi in Kruti Dev 010 codes and UDHR
#          Bengali in Bijoy (SutonnyMJ) codes, so the converter is scored on every push.
#
# No legacy font may be redistributed, so the PDFs name the font without embedding it: PDFium extracts exactly what
# it extracts from a real Kruti Dev or SutonnyMJ file (the font name and Windows-1252 codes), while a viewer draws
# Latin gibberish with a substitute font. The codes come from the app's own tables
# (pdf/src/main/kotlin/.../legacy/LegacyTables.kt), read back to front: this checks that the converter undoes what
# the tables say, not that the tables match the real fonts. Real files are checked by hand (ADR-023).
#
#   python3 scripts/pdf/legacy_golden.py      # rewrite pdf/golden/pdfs/{hi-udhr-krutidev,bn-udhr-bijoy}.pdf and
#                                             # their expected text, then run golden_corpus.py dump and goldenTest
#
# Deterministic: the same tables and text give the same bytes.

import re
import unicodedata
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
TABLES = REPO / "pdf/src/main/kotlin/io/github/skm0078/audora/pdf/legacy/LegacyTables.kt"
GOLDEN = REPO / "pdf/golden"

# id, source expected text, table object in LegacyTables.kt, PDF font name, script's reph, pre-base signs, and
# whether typists put the reph after the vowel signs (Kruti Dev "dk;ksaZ") or right after the cluster (Bijoy "Kvh©v").
DOCUMENTS = [
    ("hi-udhr-krutidev", "hi-udhr-noto", "KrutiDevCodes", "KrutiDev010", "र्", "ि", True),
    ("bn-udhr-bijoy", "bn-udhr-noto", "BijoyCodes", "SutonnyMJ", "র্", "িেৈ", False),
]
PARAGRAPHS = 5          # of the source expected text, one per line there
LINE_CODES = 80         # codes per printed line
PAGE_LINES = 48
FONT_SIZE = 11
LEADING = 15
MARGIN = 56
PAGE_WIDTH, PAGE_HEIGHT = 595, 842
CODE_WIDTH = 500        # every code drawn 0.5 em wide: boxes stay simple and deterministic

VIRAMA = {"्", "্"}
NUKTA = {"़", "়"}
ZERO_WIDTH = {"‌", "‍"}


def kotlin_strings(body):
    """The "code" to "text" pairs of one Kotlin mapOf block."""
    pairs = re.findall(r'"((?:[^"\\]|\\.)*)" to "((?:[^"\\]|\\.)*)"', body)
    unescape = lambda s: re.sub(r"\\u([0-9A-Fa-f]{4})|\\(.)",
                                lambda m: chr(int(m.group(1), 16)) if m.group(1) else m.group(2), s)
    return [(unescape(a), unescape(b)) for a, b in pairs]


def table(name):
    source = TABLES.read_text(encoding="utf-8")
    block = re.search(r"object " + name + r" \{.*?val ENTRIES[^=]*= mapOf\((.*?)\n    \)", source, re.S).group(1)
    return kotlin_strings(block)


def is_consonant(c):
    return unicodedata.category(c) == "Lo" and ("क" <= c <= "ह" or "क़" <= c <= "य़"
                                                 or "ক" <= c <= "হ" or "ড়" <= c <= "য়"
                                                 or c == "ৎ")


def is_sign(c):
    return unicodedata.category(c) in ("Mn", "Mc") and c not in VIRAMA and c not in NUKTA


def visual_order(word, reph, prebase, reph_last):
    """Unicode (NFD) word → drawing order: pre-base signs before their cluster, rephs after their syllable."""
    out, i = [], 0
    while i < len(word):
        start = i
        has_reph = word.startswith(reph, i) and i + 2 < len(word) and is_consonant(word[i + 2])
        if has_reph:
            i += 2
        if i < len(word) and is_consonant(word[i]):
            j = i + 1
            while j < len(word) and (word[j] in NUKTA or (word[j] in VIRAMA and j + 1 < len(word)
                                                         and is_consonant(word[j + 1]))):
                j += 2 if word[j] in VIRAMA else 1
            cluster = word[i:j]
            signs = ""
            while j < len(word) and (is_sign(word[j]) or word[j] in VIRAMA):
                signs += word[j]
                j += 1
            pre = "".join(s for s in signs if s in prebase)
            post = "".join(s for s in signs if s not in prebase)
            mark = reph if has_reph else ""
            out.append(pre + cluster + (post + mark if reph_last else mark + post))
            i = j
        else:
            out.append(word[start:i + 1])
            i = max(i + 1, start + 1)
    return "".join(out)


def encoder(pairs):
    """Unicode → code, longest Unicode first; ties prefer plain ASCII codes, then the first listed."""
    best = {}
    for order, (code, text) in enumerate(pairs):
        if not text or code in ("\u0002", "\u00ad"):
            continue
        key = unicodedata.normalize("NFD", text)
        rank = (not code.isascii(), len(code), order)
        if key not in best or rank < best[key][0]:
            best[key] = (rank, code)
    longest = max(len(k) for k in best)
    def encode(text):
        codes, i, skipped = [], 0, 0
        while i < len(text):
            for n in range(min(longest, len(text) - i), 0, -1):
                hit = best.get(text[i:i + n])
                if hit:
                    codes.append(hit[1])
                    i += n
                    break
            else:
                if text[i] == " " or (text[i].isascii() and not text[i].isalpha()):
                    codes.append(text[i])
                elif text[i] not in ZERO_WIDTH:
                    skipped += 1
                i += 1
        return "".join(codes), skipped
    return encode


def pdf_bytes(pages, font):
    objects = []
    def add(body):
        objects.append(body)
        return len(objects)
    widths = " ".join([str(CODE_WIDTH)] * (255 - 32 + 1))
    descriptor = add(f"<< /Type /FontDescriptor /FontName /{font} /Flags 32 /FontBBox [0 -250 1000 900] "
                     "/ItalicAngle 0 /Ascent 900 /Descent -250 /CapHeight 700 /StemV 80 >>".encode())
    font_id = add(f"<< /Type /Font /Subtype /TrueType /BaseFont /{font} /FirstChar 32 /LastChar 255 "
                  f"/Widths [{widths}] /Encoding /WinAnsiEncoding /FontDescriptor {descriptor} 0 R >>".encode())
    page_ids = []
    pages_id = len(objects) + 2 * len(pages) + 1
    for lines in pages:
        stream = [f"BT /F1 {FONT_SIZE} Tf {LEADING} TL {MARGIN} {PAGE_HEIGHT - MARGIN} Td".encode()]
        for line in lines:
            data = line.encode("cp1252")
            escaped = data.replace(b"\\", b"\\\\").replace(b"(", b"\\(").replace(b")", b"\\)")
            stream.append(b"(" + escaped + b") Tj T*")
        stream.append(b"ET")
        content = b"\n".join(stream)
        content_id = add(b"<< /Length %d >>\nstream\n" % len(content) + content + b"\nendstream")
        page_ids.append(add(f"<< /Type /Page /Parent {pages_id} 0 R /MediaBox [0 0 {PAGE_WIDTH} {PAGE_HEIGHT}] "
                            f"/Resources << /Font << /F1 {font_id} 0 R >> >> /Contents {content_id} 0 R >>"
                            .encode()))
    kids = " ".join(f"{p} 0 R" for p in page_ids)
    assert add(f"<< /Type /Pages /Kids [{kids}] /Count {len(page_ids)} >>".encode()) == pages_id
    catalog = add(f"<< /Type /Catalog /Pages {pages_id} 0 R >>".encode())
    out = bytearray(b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n")
    offsets = []
    for number, body in enumerate(objects, start=1):
        offsets.append(len(out))
        out += f"{number} 0 obj\n".encode() + body + b"\nendobj\n"
    xref = len(out)
    out += f"xref\n0 {len(objects) + 1}\n0000000000 65535 f \n".encode()
    for offset in offsets:
        out += f"{offset:010d} 00000 n \n".encode()
    out += f"trailer\n<< /Size {len(objects) + 1} /Root {catalog} 0 R >>\nstartxref\n{xref}\n%%EOF\n".encode()
    return bytes(out)


def wrap(words):
    lines, line = [], ""
    for word in words:
        if line and len(line) + 1 + len(word) > LINE_CODES:
            lines.append(line)
            line = word
        else:
            line = f"{line} {word}" if line else word
    if line:
        lines.append(line)
    return lines


def main():
    for doc_id, source, table_name, font, reph, prebase, reph_last in DOCUMENTS:
        paragraphs = (GOLDEN / "expected" / f"{source}.txt").read_text(encoding="utf-8").split("\n\n")[:PARAGRAPHS]
        paragraphs = [p.strip() for p in paragraphs]
        encode = encoder(table(table_name))
        lines, skipped = [], 0
        for paragraph in paragraphs:
            words = []
            for word in unicodedata.normalize("NFD", paragraph).split():
                codes, missing = encode(visual_order(word, reph, prebase, reph_last))
                skipped += missing
                words.append(codes)
            lines += wrap(words) + [""]
        pages = [lines[i:i + PAGE_LINES] for i in range(0, len(lines), PAGE_LINES)]
        (GOLDEN / "pdfs" / f"{doc_id}.pdf").write_bytes(pdf_bytes(pages, font))
        (GOLDEN / "expected" / f"{doc_id}.txt").write_text("\n\n".join(paragraphs) + "\n", encoding="utf-8")
        print(f"wrote {doc_id}: {len(pages)} pages, {skipped} characters with no code (left out)")


if __name__ == "__main__":
    main()
