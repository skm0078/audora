#!/usr/bin/env python3
# File: scripts/fixtures/make_djvu_fixtures.py
# Purpose: Writes the DjVu test books (ADR 2026-10-27-djvu-books) from text written here, so they may be redistributed:
#          text.djvu (a text layer, an outline and a title) and scan.djvu (page pictures only, read by OCR).
#
# Needs DjVuLibre's command-line tools (cjb2, djvm, djvused; any 3.5.x) on PATH and Pillow. Run from the repo root:
#   python3 scripts/fixtures/make_djvu_fixtures.py
# Each page is drawn with DejaVu Sans; the text layer is written from the boxes of the words as drawn, so it sits
# where a scanner's OCR would put it.

from __future__ import annotations

import os
import shutil
import subprocess
import sys
import tempfile

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
UNIT = os.path.join(ROOT, "playback", "src", "test", "resources", "djvu")
DEVICE = os.path.join(ROOT, "playback", "src", "androidTest", "assets", "djvu")
JOURNEY = os.path.join(ROOT, "app", "src", "androidTest", "assets", "ebooks")
FONTS = ("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", "/usr/share/fonts/truetype/freefont/FreeSans.ttf")
WIDTH, HEIGHT, DPI = 850, 1100, 100
MARGIN, LINE_GAP, PARA_GAP = 60, 14, 34

# A page is a list of paragraphs; a paragraph a list of lines; "#" starts a heading. Page 2 has no paragraph level
# in its text layer (as some OCR tools write it) and a page number at its foot.
TEXT_PAGES = [
    [["# Chapter One"],
     ["The first page of a small DjVu book has a", "remarkable sentence that is split by a hyph-", "en at the line end."],
     ["A second paragraph follows the first one."]],
    [["Chapter Two starts on the second page."],
     ["Its lines carry no paragraph marks, so the", "reader joins them by their spacing."],
     ["2"]],
    [["मन जहाँ डर से परे है।"]],
]
SCAN_PAGES = [
    [["# A Scanned Page"], ["This page has no text layer.", "Reading it needs OCR."]],
    [["The second scanned page."]],
]
TITLE, AUTHOR = "A DjVu Journey Book", "ListenLock Tests"
OUTLINE = '(bookmarks ("Chapter One" "#1") ("Chapter Two" "#2" ("Hindi page" "#3")))'


def font_for(text: str, heading: bool) -> ImageFont.FreeTypeFont:
    path = FONTS[1] if any("ऀ" <= ch <= "ॿ" for ch in text) else FONTS[0]
    return ImageFont.truetype(path, 34 if heading else 26)


def draw_page(page, folder: str, name: str):
    """Draws [page] as a bilevel picture and returns its words' boxes: [(paragraph, [(line, [(word, box)])])]."""
    image = Image.new("1", (WIDTH, HEIGHT), 1)
    pen = ImageDraw.Draw(image)
    y = MARGIN
    layout = []
    for para in page:
        lines = []
        for raw in para:
            heading = raw.startswith("# ")
            text = raw[2:] if heading else raw
            font = font_for(text, heading)
            x = MARGIN
            words = []
            for word in text.split(" "):
                left, top, right, bottom = (round(v) for v in pen.textbbox((x, y), word, font=font))
                pen.text((x, y), word, font=font, fill=0)
                words.append((word, (left, top, right, bottom)))
                x = right + pen.textlength(" ", font=font)
            lines.append((text, words))
            y += font.size + LINE_GAP
        layout.append(lines)
        y += PARA_GAP - LINE_GAP
    pbm = os.path.join(folder, name + ".pbm")
    image.save(pbm)
    return pbm, layout


def quoted(text: str) -> str:
    return '"' + text.replace("\\", "\\\\").replace('"', '\\"') + '"'


def box(left: int, top: int, right: int, bottom: int) -> str:
    """DjVu text boxes count y up from the page's foot."""
    return f"{left} {HEIGHT - bottom} {right} {HEIGHT - top}"


def union(boxes):
    return (min(b[0] for b in boxes), min(b[1] for b in boxes), max(b[2] for b in boxes), max(b[3] for b in boxes))


def text_layer(layout, with_paragraphs: bool) -> str:
    """The page's hidden text as djvused's s-expression: page, then paragraphs (or not), lines and words."""
    paras = []
    for lines in layout:
        line_exprs = []
        for _, words in lines:
            word_exprs = " ".join(f"(word {box(*b)} {quoted(w)})" for w, b in words)
            line_exprs.append(f"(line {box(*union([b for _, b in words]))} {word_exprs})")
        para_box = union([union([b for _, b in words]) for _, words in lines])
        paras.append(f"(para {box(*para_box)} {' '.join(line_exprs)})" if with_paragraphs else " ".join(line_exprs))
    return f"(page 0 0 {WIDTH} {HEIGHT} {' '.join(paras)})"


def run(*args: str) -> None:
    subprocess.run(args, check=True)


def make_book(pages, name: str, folder: str, with_text: bool) -> str:
    singles = []
    for index, page in enumerate(pages):
        pbm, layout = draw_page(page, folder, f"{name}-{index + 1}")
        single = os.path.join(folder, f"{name}-{index + 1}.djvu")
        run("cjb2", "-dpi", str(DPI), pbm, single)
        if with_text:
            layer = os.path.join(folder, f"{name}-{index + 1}.txt")
            with open(layer, "w", encoding="utf-8") as out:
                out.write(text_layer(layout, with_paragraphs=index != 1))
            run("djvused", single, "-e", f"select 1; set-txt {layer}", "-s")
        singles.append(single)
    book = os.path.join(folder, name + ".djvu")
    run("djvm", "-c", book, *singles)
    if with_text:
        outline = os.path.join(folder, "outline.txt")
        with open(outline, "w", encoding="utf-8") as out:
            out.write(OUTLINE)
        meta = os.path.join(folder, "meta.txt")
        with open(meta, "w", encoding="utf-8") as out:
            out.write(f"title {quoted(TITLE)}\nauthor {quoted(AUTHOR)}\n")
        run("djvused", book, "-e", f"set-outline {outline}; set-meta {meta}", "-s")
    return book


def page_texts(book: str) -> list[str]:
    """Each page's text layer as djvused prints it, UTF-8 left as it is: what the unit tests parse."""
    count = int(subprocess.run(["djvused", book, "-e", "n"], check=True, capture_output=True, text=True).stdout)
    return [subprocess.run(["djvused", "-u", book, "-e", f"select {page}; print-txt"], check=True,
                           capture_output=True, text=True).stdout for page in range(1, count + 1)]


def main() -> None:
    for tool in ("cjb2", "djvm", "djvused"):
        if shutil.which(tool) is None:
            sys.exit(f"{tool} not found: install DjVuLibre's tools")
    for folder in (UNIT, DEVICE, JOURNEY):
        os.makedirs(folder, exist_ok=True)
    with tempfile.TemporaryDirectory() as work:
        text = make_book(TEXT_PAGES, "text", work, with_text=True)
        scan = make_book(SCAN_PAGES, "scan", work, with_text=False)
        for folder in (DEVICE, JOURNEY):
            shutil.copy(text, os.path.join(folder, "text.djvu"))
            shutil.copy(scan, os.path.join(folder, "scan.djvu"))
        for index, layer in enumerate(page_texts(text)):
            with open(os.path.join(UNIT, f"text-page{index + 1}.sexp"), "w", encoding="utf-8") as out:
                out.write(layer)
        outline = subprocess.run(["djvused", "-u", text, "-e", "print-outline"], check=True, capture_output=True,
                                 text=True).stdout
        with open(os.path.join(UNIT, "text-outline.sexp"), "w", encoding="utf-8") as out:
            out.write(outline)
        anno = subprocess.run(["djvused", "-u", text, "-e", "select-shared-ant; print-ant"], check=True, capture_output=True,
                              text=True).stdout
        with open(os.path.join(UNIT, "text-annotations.sexp"), "w", encoding="utf-8") as out:
            out.write(anno)


if __name__ == "__main__":
    main()
