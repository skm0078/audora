#!/usr/bin/env python3
# File: scripts/fixtures/make_chapter_list_fixtures.py
# Purpose: Writes two small EPUBs whose chapter lists the app used to lose, for the release journey in
#          app/src/androidTest/assets/ebooks (ADR 2026-11-15-epub-contents):
#          - contents-scanned.epub: a scanned book as the Internet Archive makes it: one file per page, a contents
#            that names only a "Notice", and each chapter's "CHAPTER 2 The Second Road THE…" in the middle of a page;
#          - contents-one-file.epub: every chapter in one file, the contents nested and linking to "#ids".
# Run: python3 make_chapter_list_fixtures.py

import io
import os
import zipfile

JOURNEY = os.path.join(os.path.dirname(__file__), "..", "..", "app", "src", "androidTest", "assets", "ebooks")
FIXED_TIME = (2026, 11, 15, 12, 0, 0)

# Text the journey looks for in the chapter list (ChapterListJourney).
SCANNED_CHAPTER = "Chapter 2: The Second Road"
ONE_FILE_SECTION = "A Section Inside"

CONTAINER = ('<?xml version="1.0"?><container version="1.0" '
             'xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles>'
             '<rootfile full-path="EPUB/content.opf" media-type="application/oebps-package+xml"/>'
             '</rootfiles></container>')

FILLER = ("The road went on through the hills and the travellers walked on, talking of the day behind them "
          "and of the towns ahead. ") * 6


def entry(name: str) -> zipfile.ZipInfo:
    info = zipfile.ZipInfo(name, FIXED_TIME)
    info.compress_type = zipfile.ZIP_STORED if name == "mimetype" else zipfile.ZIP_DEFLATED
    return info


def xhtml(title: str, body: str) -> str:
    return ('<?xml version="1.0" encoding="utf-8"?><html xmlns="http://www.w3.org/1999/xhtml" '
            f'xmlns:epub="http://www.idpf.org/2007/ops"><head><title>{title}</title></head>'
            f'<body>{body}</body></html>')


def package(book_id: str, title: str, files: list, nav: bool) -> str:
    items = "".join(f'<item id="f{i}" href="{name}" media-type="application/xhtml+xml"/>'
                    for i, name in enumerate(files))
    toc = ('<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>' if nav else
           '<item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>')
    spine = "".join(f'<itemref idref="f{i}"/>' for i in range(len(files)))
    return ('<?xml version="1.0" encoding="utf-8"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0" '
            'unique-identifier="id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/">'
            f'<dc:identifier id="id">{book_id}</dc:identifier><dc:title>{title}</dc:title>'
            f'<dc:language>en</dc:language></metadata><manifest>{items}{toc}</manifest>'
            f'<spine toc="ncx">{spine}</spine></package>')


def zipped(files: dict) -> bytes:
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as z:
        z.writestr(entry("mimetype"), "application/epub+zip")
        z.writestr(entry("META-INF/container.xml"), CONTAINER)
        for name, text in files.items():
            z.writestr(entry(name), text)
    return buf.getvalue()


def scanned() -> bytes:
    pages = {
        "notice.html": xhtml("Notice", "<p>This book was produced in EPUB format from scanned pages.</p>"),
        "page_1.html": xhtml("Page 1", "<p>CONTENTS CHAPTER I. The First Road - 2. The Second Road - "
                                       "3. The Last Road</p>"),
        "page_2.html": xhtml("Page 2", f"<p>CHAPTER I The First Road ONCE upon a time. {FILLER}</p>"),
        "page_3.html": xhtml("Page 3", f"<p>{FILLER} 3 CHAPTER 2 The Second Road THE morning came. {FILLER}</p>"),
        "page_4.html": xhtml("Page 4", f"<p>{FILLER} 4 CHAPTER 3 The Last Road AT last they were home.</p>"),
    }
    ncx = ('<?xml version="1.0" encoding="utf-8"?><ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" '
           'version="2005-1"><navMap><navPoint id="n0"><navLabel><text>Notice</text></navLabel>'
           '<content src="notice.html"/></navPoint></navMap></ncx>')
    files = {f"EPUB/{name}": text for name, text in pages.items()}
    files["EPUB/content.opf"] = package("contents-scanned", "Scanned Roads", list(pages), nav=False)
    files["EPUB/toc.ncx"] = ncx
    return zipped(files)


def one_file() -> bytes:
    # About a printed page of text after each heading, so each row starts on a page of its own.
    page = f"<p>{FILLER}</p>" * 4
    body = (f'<h1 id="part">Part One</h1><h2 id="c1">The First Chapter</h2>{page}'
            f'<h2 id="c2">The Second Chapter</h2>{page}'
            f'<h3 id="s2">{ONE_FILE_SECTION}</h3>{page}')
    nav = xhtml("Contents", '<nav epub:type="toc"><ol><li><a href="book.xhtml">Part One</a><ol>'
                            '<li><a href="book.xhtml#c1">The First Chapter</a></li>'
                            '<li><a href="book.xhtml#c2">The Second Chapter</a><ol>'
                            f'<li><a href="book.xhtml#s2">{ONE_FILE_SECTION}</a></li></ol></li>'
                            '</ol></li></ol></nav>')
    files = {
        "EPUB/book.xhtml": xhtml("Book", body),
        "EPUB/nav.xhtml": nav,
        "EPUB/content.opf": package("contents-one-file", "One File Roads", ["book.xhtml"], nav=True),
    }
    return zipped(files)


def write(name: str, data: bytes):
    with open(os.path.join(JOURNEY, name), "wb") as f:
        f.write(data)
    print(f"{name}: {len(data)} bytes")


def main():
    os.makedirs(JOURNEY, exist_ok=True)
    write("contents-scanned.epub", scanned())
    write("contents-one-file.epub", one_file())


if __name__ == "__main__":
    main()
