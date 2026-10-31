#!/usr/bin/env python3
# File: scripts/fixtures/make_zip_name_fixtures.py
# Purpose: Writes an EPUB and a CBZ whose zips store one name in the PC's code page (CP437) without the UTF-8 flag,
#          as Windows' built-in zip and older tools do, for the release journey in app/src/androidTest/assets/ebooks.
#          Java's zip readers throw at such a name; the app must still open both (ZipNames).
# Run: python3 make_zip_name_fixtures.py

import os
import sys
import zipfile

JOURNEY = os.path.join(os.path.dirname(__file__), "..", "..", "app", "src", "androidTest", "assets", "ebooks")
FIXED_TIME = (2026, 10, 29, 12, 0, 0)

# The sentence the journey looks for in the reader.
SENTENCE = "A book zipped with names that are not Unicode still opens."


class CodePageName(zipfile.ZipInfo):
    """A name stored in CP437 with the UTF-8 flag clear; Python itself would store it as flagged UTF-8."""

    def _encodeFilenameFlags(self):  # noqa: N802 (zipfile's own name)
        return self.filename.encode("cp437"), self.flag_bits & ~0x800


def entry(name: str, code_page: bool = False) -> zipfile.ZipInfo:
    info = (CodePageName if code_page else zipfile.ZipInfo)(name, FIXED_TIME)
    info.compress_type = zipfile.ZIP_STORED if name == "mimetype" else zipfile.ZIP_DEFLATED
    return info


def epub() -> bytes:
    import io
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as z:
        z.writestr(entry("mimetype"), "application/epub+zip")
        z.writestr(entry("META-INF/container.xml"),
                   '<?xml version="1.0"?><container version="1.0" '
                   'xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles>'
                   '<rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>'
                   '</rootfiles></container>')
        z.writestr(entry("OEBPS/content.opf"),
                   '<?xml version="1.0" encoding="UTF-8"?><package xmlns="http://www.idpf.org/2007/opf" '
                   'version="3.0" unique-identifier="id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/">'
                   '<dc:identifier id="id">zip-names</dc:identifier><dc:title>Zip Names</dc:title>'
                   '<dc:language>en</dc:language></metadata><manifest>'
                   '<item id="c1" href="chapter.xhtml" media-type="application/xhtml+xml"/></manifest>'
                   '<spine><itemref idref="c1"/></spine></package>')
        z.writestr(entry("OEBPS/chapter.xhtml"),
                   '<?xml version="1.0" encoding="UTF-8"?><html xmlns="http://www.w3.org/1999/xhtml">'
                   f'<head><title>One</title></head><body><h1>Chapter One</h1><p>{SENTENCE}</p></body></html>')
        # The picture's name is the one Java cannot read as UTF-8.
        z.writestr(entry("OEBPS/images/café.png", code_page=True), png(2, 2, 90))
    return buf.getvalue()


def comic() -> bytes:
    import io
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as z:
        z.writestr(entry("página1.png", code_page=True), png(4, 3, 200))
        z.writestr(entry("página2.png", code_page=True), png(4, 3, 120))
    return buf.getvalue()


def write(name: str, data: bytes):
    with open(os.path.join(JOURNEY, name), "wb") as f:
        f.write(data)
    print(f"{name}: {len(data)} bytes")


def main():
    os.makedirs(JOURNEY, exist_ok=True)
    write("names-cp437.epub", epub())
    write("names-cp437.cbz", comic())


if __name__ == "__main__":
    sys.path.insert(0, os.path.dirname(__file__))
    from make_ebook_fixtures import png  # noqa: E402 (the same tiny PNG writer)
    main()
