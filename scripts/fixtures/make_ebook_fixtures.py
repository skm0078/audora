#!/usr/bin/env python3
# File: scripts/fixtures/make_ebook_fixtures.py
# Purpose: Writes the small MOBI, AZW3-style, PalmDOC, CHM and CBZ test books in playback/src/test/resources/ebooks,
#          and copies (plus an FB2) for the release journey in app/src/androidTest/assets/ebooks, from text written
#          here, so the readers are tested on real container bytes we may redistribute
#          (ADR 2026-10-26-more-ebook-formats).
#
# The files follow the published formats (MobileRead wiki for MOBI/PalmDOC/HUFF-CDIC, Microsoft's LZX DS and the
# chmlib notes for CHM). They were checked once with independent readers before they were committed: the "mobi"
# Python package (KindleUnpack) for the Kindle files and libmspack (through pylibmspack) for the CHM.
# Run: python3 make_ebook_fixtures.py

import os
import struct
import sys
import zipfile
import zlib

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "playback", "src", "test", "resources", "ebooks")
JOURNEY = os.path.join(os.path.dirname(__file__), "..", "..", "app", "src", "androidTest", "assets", "ebooks")
JOURNEY_BOOKS = ("kf8.azw3", "help.chm", "comic.cbz")
RECORD_SIZE = 4096

# --------------------------------------------------------------------------------------------------- MOBI and PDB


def palmdoc_compress(data: bytes) -> bytes:
    """PalmDOC LZ77, greedy: back-references of 3-10 bytes within 2047, space pairs, literal runs."""
    out = bytearray()
    i = 0
    n = len(data)
    while i < n:
        best_len, best_dist = 0, 0
        if i >= 1:
            start = max(0, i - 2047)
            for length in range(min(10, n - i), 2, -1):
                found = data.rfind(data[i:i + length], start, i + length - 1)
                if found != -1 and found < i:
                    best_len, best_dist = length, i - found
                    break
        if best_len >= 3:
            pair = 0x8000 | (best_dist << 3) | (best_len - 3)
            out += struct.pack(">H", pair)
            i += best_len
            continue
        c = data[i]
        if c == 0x20 and i + 1 < n and 0x40 <= data[i + 1] <= 0x7F:
            out.append(data[i + 1] ^ 0x80)
            i += 2
            continue
        if c == 0 or 0x09 <= c <= 0x7F:
            out.append(c)
            i += 1
            continue
        # A run of bytes that need escaping: 1..8 literal bytes after a count byte.
        run = bytearray()
        while i < n and len(run) < 8 and not (data[i] == 0 or 0x09 <= data[i] <= 0x7F):
            run.append(data[i])
            i += 1
        out.append(len(run))
        out += run
    return bytes(out)


def huff_cdic_tables(phrase_zero: bytes):
    """A HUFF table where every code is 8 bits and names phrase 255 - code, and one CDIC of 256 phrases: phrase b
    (b > 0) is the byte b, stored plain; phrase 0 is [phrase_zero] stored compressed, so nested phrases are tested."""
    dict1 = [8 | 0x80 | (255 << 8)] * 256
    dict2 = [0] * 64
    huff = b"HUFF\x00\x00\x00\x18" + struct.pack(">LL", 0x18, 0x18 + 1024) + b"\x00" * 8
    huff += struct.pack(">256L", *dict1) + struct.pack(">64L", *dict2)
    phrases = []
    for b in range(256):
        if b == 0:
            body = huff_encode(phrase_zero, allow_zero=False)
            phrases.append(struct.pack(">H", len(body)) + body)
        else:
            phrases.append(struct.pack(">H", 0x8000 | 1) + bytes([b]))
    offsets, blob = [], b""
    for p in phrases:
        offsets.append(256 * 2 + len(blob))
        blob += p
    cdic = b"CDIC\x00\x00\x00\x10" + struct.pack(">LL", 256, 8) + struct.pack(">256H", *offsets) + blob
    return huff, cdic


def huff_encode(data: bytes, allow_zero=True, phrase_zero: bytes = b"") -> bytes:
    """Each byte as its own 8-bit code; [phrase_zero] wherever it occurs becomes the code of phrase 0."""
    out = bytearray()
    i = 0
    while i < len(data):
        if allow_zero and phrase_zero and data.startswith(phrase_zero, i):
            out.append(255 - 0)
            i += len(phrase_zero)
        else:
            out.append(255 - data[i])
            i += 1
    return bytes(out)


def trailing(record: bytes, flags: int) -> bytes:
    """Adds what a Kindle writer puts after a record: a multibyte count byte (flag 1) and a 3-byte TBS entry
    (flag 2), so the reader must cut them off."""
    out = record
    if flags & 1:
        out += b"\x00"  # no multibyte character carried over
    if flags & 2:
        out += b"\x01\x02" + bytes([0x80 | 3])  # an entry of 3 bytes, its size last
    return out


def exth(records):
    body = b""
    for kind, value in records:
        data = value.encode("utf-8")
        body += struct.pack(">LL", kind, len(data) + 8) + data
    pad = (4 - len(body) % 4) % 4
    return b"EXTH" + struct.pack(">LL", 12 + len(body) + pad, len(records)) + body + b"\x00" * pad


def mobi(text: str, title: str, *, version=6, compression=2, encryption=0, extra_flags=3, author="", lang="en",
         huff_phrase=b"") -> bytes:
    raw = text.encode("utf-8")
    chunks = [raw[i:i + RECORD_SIZE] for i in range(0, len(raw), RECORD_SIZE)] or [b""]
    huff_records = []
    if compression == 2:
        records = [trailing(palmdoc_compress(c), extra_flags) for c in chunks]
    elif compression == 17480:
        huff, cdic = huff_cdic_tables(huff_phrase)
        huff_records = [huff, cdic]
        records = [trailing(huff_encode(c, phrase_zero=huff_phrase), extra_flags) for c in chunks]
    else:
        records = [trailing(c, extra_flags) for c in chunks]
    name = title.encode("utf-8")
    exth_block = exth([(100, author), (503, title), (524, lang)] if author else [(503, title), (524, lang)])
    header_len = 0xE8
    mobi_header = bytearray(header_len)
    mobi_header[0:4] = b"MOBI"
    struct.pack_into(">L", mobi_header, 0x04, header_len)
    struct.pack_into(">L", mobi_header, 0x08, 2)  # book
    struct.pack_into(">L", mobi_header, 0x0C, 65001)
    struct.pack_into(">L", mobi_header, 0x10, 0x1234)
    struct.pack_into(">L", mobi_header, 0x14, version)
    for off in range(0x18, 0x40, 4):
        struct.pack_into(">L", mobi_header, off, 0xFFFFFFFF)
    first_non_text = 1 + len(records)
    huff_index = first_non_text if huff_records else 0
    struct.pack_into(">L", mobi_header, 0x40, first_non_text + len(huff_records))  # first non-book record
    full_name_offset = 16 + header_len + len(exth_block)
    struct.pack_into(">L", mobi_header, 0x44, full_name_offset)
    struct.pack_into(">L", mobi_header, 0x48, len(name))
    struct.pack_into(">L", mobi_header, 0x4C, 9)  # English
    struct.pack_into(">L", mobi_header, 0x58, version)  # min version
    struct.pack_into(">L", mobi_header, 0x5C, first_non_text + len(huff_records))  # first image
    struct.pack_into(">L", mobi_header, 0x60, huff_index)
    struct.pack_into(">L", mobi_header, 0x64, len(huff_records))
    struct.pack_into(">L", mobi_header, 0x70, 0x50)  # EXTH present
    struct.pack_into(">L", mobi_header, 0xA8 - 16, 0xFFFFFFFF)  # no DRM
    struct.pack_into(">HH", mobi_header, 0xC0 - 16, 1, len(records))  # first and last content record
    struct.pack_into(">H", mobi_header, 0xF2 - 16, extra_flags)
    struct.pack_into(">L", mobi_header, 0xF4 - 16, 0xFFFFFFFF)  # no NCX index
    palmdoc = struct.pack(">HHLHHHH", compression, 0, len(raw), len(records), RECORD_SIZE, encryption, 0)
    record0 = palmdoc + bytes(mobi_header) + exth_block + name + b"\x00\x00"
    record0 += b"\x00" * ((4 - len(record0) % 4) % 4)
    eof = b"\xe9\x8e\r\n"
    return pdb(title, b"BOOKMOBI", [record0] + records + huff_records + [eof])


def palmdoc_book(text: str, title: str) -> bytes:
    raw = text.encode("cp1252")
    chunks = [raw[i:i + RECORD_SIZE] for i in range(0, len(raw), RECORD_SIZE)]
    records = [palmdoc_compress(c) for c in chunks]
    record0 = struct.pack(">HHLHHHH", 2, 0, len(raw), len(records), RECORD_SIZE, 0, 0)
    return pdb(title, b"TEXtREAd", [record0] + records)


def pdb(title: str, type_creator: bytes, records) -> bytes:
    name = title.encode("ascii", "replace")[:31].replace(b" ", b"_")
    header = name.ljust(32, b"\x00")
    header += struct.pack(">HHLLLLLL", 0, 0, 0, 0, 0, 0, 0, 0) + type_creator
    header += struct.pack(">LLH", 2 * len(records) - 1, 0, len(records))
    offset = len(header) + 8 * len(records) + 2
    table = b""
    for i, r in enumerate(records):
        table += struct.pack(">LL", offset, 2 * i)
        offset += len(r)
    return header + table + b"\x00\x00" + b"".join(records)


MOBI6_TEXT = (
    "<html><head><guide></guide></head><body>"
    "<h2>Chapter One</h2><p>It was a bright cold day in April, and the clocks were striking.</p>"
    "<p>Winston Smith slipped quickly through the glass doors.</p>"
    "<mbp:pagebreak/>"
    "<h2>অধ্যায় দুই</h2><p>আমার সোনার বাংলা, আমি তোমায় ভালোবাসি।</p>"
    "<p>मन जहाँ डर से परे है।</p>"
    + "".join(f"<p>Line {i} of a long chapter repeats the long chapter text so records split.</p>" for i in range(120))
    + "</body></html>"
)

KF8_TEXT = (
    '<?xml version="1.0"?><html xmlns="http://www.w3.org/1999/xhtml"><head><title>Ignored head</title></head>'
    '<body aid="0"></body></html>'
    '<div aid="1"><h1>Part One</h1><p>The first file\'s fragment.</p></div>'
    '<?xml version="1.0"?><html xmlns="http://www.w3.org/1999/xhtml"><head><title>Ignored</title></head>'
    '<body aid="2"></body></html>'
    '<div aid="3"><h1>Part Two</h1><p>The second file\'s fragment.</p></div>'
)


def write(name: str, data: bytes, folder: str = OUT):
    with open(os.path.join(folder, name), "wb") as f:
        f.write(data)
    if folder == OUT and name in JOURNEY_BOOKS:
        write(name, data, JOURNEY)
    print(f"{os.path.relpath(folder, OUT)}/{name}: {len(data)} bytes")


FB2 = """<?xml version="1.0" encoding="utf-8"?>
<FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0" xmlns:l="http://www.w3.org/1999/xlink">
<description><title-info><author><first-name>Ann</first-name><last-name>Writer</last-name></author>
<book-title>A Journey Book</book-title><lang>en</lang></title-info></description>
<body><section><title><p>Chapter One</p></title><p>The first chapter of a small FictionBook.</p>
<p>It has a note<a l:href="#n1" type="note">[1]</a> and a second sentence.</p></section>
<section><title><p>Chapter Two</p></title><p>The second chapter ends the book.</p></section></body>
<body name="notes"><section id="n1"><title><p>1</p></title><p>The note's own words.</p></section></body>
</FictionBook>
"""


def main():
    os.makedirs(OUT, exist_ok=True)
    os.makedirs(JOURNEY, exist_ok=True)
    write("book.fb2", FB2.encode("utf-8"), JOURNEY)
    write("mobi6-palmdoc.mobi", mobi(MOBI6_TEXT, "A Mobi Book", author="Ann Writer", lang="en"))
    write("kf8.azw3", mobi(KF8_TEXT, "A KF8 Book", version=8, extra_flags=0))
    write("huffcdic.mobi", mobi(MOBI6_TEXT, "Huff Book", compression=17480, extra_flags=2,
                                huff_phrase=b"long chapter"))
    write("drm.azw", mobi(MOBI6_TEXT, "Locked Book", encryption=2))
    write("palmdoc.pdb", palmdoc_book("Chapter 1\n\nA plain PalmDOC book, café and all.\n", "Palm Book"))
    import chm_fixture
    write("help.chm", chm_fixture.build())
    write("comic.cbz", comic())


def comic() -> bytes:
    """Three tiny pictures out of name order (page10 after page2), a folder and ComicInfo.xml, as tools write."""
    import io
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_STORED) as z:
        z.writestr("ComicInfo.xml", "<ComicInfo><Title>Tiny</Title></ComicInfo>")
        z.writestr("pages/", b"")
        for name, shade in (("pages/page10.png", 30), ("pages/page2.png", 120), ("pages/page1.png", 200)):
            z.writestr(name, png(4, 3, shade))
        z.writestr("__MACOSX/pages/._page1.png", b"junk")
    return buf.getvalue()


def png(width: int, height: int, shade: int) -> bytes:
    def chunk(kind, data):
        return struct.pack(">L", len(data)) + kind + data + struct.pack(">L", zlib.crc32(kind + data) & 0xFFFFFFFF)
    raw = b"".join(b"\x00" + bytes([shade]) * width for _ in range(height))
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">LLBBBBB", width, height, 8, 0, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))


if __name__ == "__main__":
    sys.path.insert(0, os.path.dirname(__file__))
    main()
