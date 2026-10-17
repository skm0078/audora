#!/usr/bin/env python3
# File: scripts/pdf/golden_corpus.py
# Purpose: Builds and checks the golden corpus in pdf/golden/ (P2-01) and the listening corpus in pdf/golden/listening/
#          (RI-8a): page excerpts of the source PDFs, the PDFium page dumps the JVM tests read, recorded OCR of scanned
#          pages, and first drafts of the expected text.
#
# The JVM test (./gradlew :pdf:goldenTest) cannot run PDFium, so every golden page is dumped once with the production
# page reader (pdf/src/main/cpp/page_reader.cpp, through scripts/pdf/rawpage_dump.cpp) against pypdfium2's desktop
# libpdfium.so, and the dumps are committed. CI re-dumps them and fails if they differ, so a change to the PDFs or to
# page_reader.cpp cannot leave stale dumps behind.
#
#   python3 scripts/pdf/golden_corpus.py dump            # rewrite both corpora's rawpages/*.rawpages.gz
#   python3 scripts/pdf/golden_corpus.py dump --check    # CI: exit 1 if a committed dump is stale or missing
#   python3 scripts/pdf/golden_corpus.py excerpt --sources DIR [--corpus listening]  # copy a new document's pages
#   python3 scripts/pdf/golden_corpus.py draft --sources DIR [--corpus listening]    # first expected-text draft
#   python3 scripts/pdf/golden_corpus.py ocr --corpus listening   # record Tesseract hOCR of the scanned documents
#
# Needs: pypdfium2 (pinned below; its libpdfium.so is what the dumps are made with), g++, and for excerpt/draft also
# pypdf; for ocr, the tesseract command with eng/hin/ben. DIR is the shared test-PDF folder (audora/test-pdfs, see
# pdf/golden/README.md).

import argparse
import gzip
import io
import json
import os
import subprocess
import sys
import tempfile
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
GOLDEN = REPO / "pdf" / "golden"


class Corpus:
    """A corpus folder: corpus.tsv, pdfs/, rawpages/ and the expected texts (expected/ or, when spoken, spoken/)."""

    def __init__(self, name, root, expected):
        self.name = name
        self.index = root / "corpus.tsv"
        self.pdfs = root / "pdfs"
        self.rawpages = root / "rawpages"
        self.expected = root / expected
        self.ocr = root / "ocr"


CORPORA = {
    "golden": Corpus("golden", GOLDEN, "expected"),
    # What a listener should hear (RI-8a, pdf/golden/listening/README.md): scored by :playback:listeningTest.
    "listening": Corpus("listening", GOLDEN / "listening", "spoken"),
}
CPP = REPO / "pdf" / "src" / "main" / "cpp"
DUMPER_SOURCE = REPO / "scripts" / "pdf" / "rawpage_dump.cpp"

# The dumps depend on the exact PDFium build. Bump together with a full `dump` and a new goldenTest run.
PYPDFIUM2_VERSION = "5.13.0"
COLUMNS = ["id", "category", "source", "pages", "text", "status"]
# A source that names a script of this repo: the PDF and its expected text are generated here (legacy_golden.py).
GENERATED_HERE = "repo:"
# A listening source that reuses a golden PDF (pdf/golden/pdfs/<id>.pdf), so generated golden files need no copy.
FROM_GOLDEN = "golden:"
STATUSES = {"exact", "draft", "checked"}
# Listening documents whose category starts with this are scans: `ocr` records their pages for the JVM test.
SCAN_CATEGORY = "scan"
# Tesseract languages for a listening scan, from the language prefix of its id (the app's OCR packs, ADR-025).
OCR_LANGS = {"en": "eng", "hi": "hin+eng", "bn": "ben+eng"}
OCR_DPI = 300


def read_corpus(corpus):
    lines = corpus.index.read_text(encoding="utf-8").splitlines()
    if lines[0].split("\t") != COLUMNS:
        sys.exit(f"{corpus.index}: header must be {COLUMNS}")
    docs = []
    for number, line in enumerate(lines[1:], start=2):
        fields = line.split("\t")
        if len(fields) != len(COLUMNS):
            sys.exit(f"{corpus.index}:{number}: expected {len(COLUMNS)} tab-separated fields")
        doc = dict(zip(COLUMNS, fields))
        if doc["status"] not in STATUSES:
            sys.exit(f"{corpus.index}:{number}: status must be one of {sorted(STATUSES)}")
        docs.append(doc)
    return docs


def page_range(spec, count):
    if spec == "all":
        return list(range(count))
    first, last = (int(x) for x in spec.split("-"))
    if not 0 <= first <= last < count:
        sys.exit(f"page range {spec} outside 0-{count - 1}")
    return list(range(first, last + 1))


def libpdfium_path():
    try:
        import pypdfium2
        import pypdfium2_raw
    except ImportError:
        sys.exit(f"pip install pypdfium2=={PYPDFIUM2_VERSION}")
    if pypdfium2.version.PYPDFIUM_INFO.api_tag[:3] != tuple(int(x) for x in PYPDFIUM2_VERSION.split(".")):
        sys.exit(f"pypdfium2 {pypdfium2.version.PYPDFIUM_INFO} found; the dumps need {PYPDFIUM2_VERSION}")
    return str(Path(pypdfium2_raw.__file__).parent / "libpdfium.so")


def build_dumper(workdir):
    binary = Path(workdir) / "rawpage_dump"
    subprocess.run(
        ["g++", "-std=c++17", "-O1", f"-I{CPP}", str(DUMPER_SOURCE), str(CPP / "page_reader.cpp"),
         str(CPP / "pdfium_api.cpp"), "-o", str(binary), "-ldl"],
        check=True,
    )
    return binary


def page_count(pdf):
    import pypdfium2
    document = pypdfium2.PdfDocument(str(pdf))
    try:
        return len(document)
    finally:
        document.close()


def dump_document(dumper, libpdfium, pdf):
    """All pages of [pdf] as one gzip stream of rawpage records. gzip mtime 0 keeps the bytes reproducible."""
    text = io.StringIO()
    for index in range(page_count(pdf)):
        result = subprocess.run([str(dumper), libpdfium, str(pdf), str(index)], capture_output=True, text=True)
        if result.returncode != 0:
            sys.exit(f"dump failed for {pdf.name} page {index}: {result.stderr.strip()}")
        text.write(result.stdout)
    raw = io.BytesIO()
    with gzip.GzipFile(fileobj=raw, mode="wb", compresslevel=9, mtime=0) as out:
        out.write(text.getvalue().encode("utf-8"))
    return raw.getvalue()


def command_dump(args):
    libpdfium = libpdfium_path()
    stale = []
    with tempfile.TemporaryDirectory() as workdir:
        dumper = build_dumper(workdir)
        for corpus in CORPORA.values():
            corpus.rawpages.mkdir(parents=True, exist_ok=True)
            for doc in read_corpus(corpus):
                pdf = corpus.pdfs / f"{doc['id']}.pdf"
                if not pdf.is_file():
                    sys.exit(f"missing {pdf.relative_to(REPO)}: run `excerpt` first")
                target = corpus.rawpages / f"{doc['id']}.rawpages.gz"
                data = dump_document(dumper, libpdfium, pdf)
                if args.check:
                    if not target.is_file() or target.read_bytes() != data:
                        stale.append(str(target.relative_to(REPO)))
                else:
                    target.write_bytes(data)
                    print(f"wrote {target.relative_to(REPO)} ({len(data)} bytes)")
    if stale:
        print("Golden page dumps are stale. Run `python3 scripts/pdf/golden_corpus.py dump`, then "
              "`./gradlew :pdf:goldenTest`, and commit the results:", file=sys.stderr)
        print("\n".join(stale), file=sys.stderr)
        sys.exit(1)
    if args.check:
        print("golden page dumps are current")


def command_excerpt(args):
    from pypdf import PdfReader, PdfWriter
    corpus = CORPORA[args.corpus]
    corpus.pdfs.mkdir(parents=True, exist_ok=True)
    for doc in read_corpus(corpus):
        target = corpus.pdfs / f"{doc['id']}.pdf"
        if doc["source"].startswith(GENERATED_HERE) or (target.exists() and not args.force):
            continue
        if doc["source"].startswith(FROM_GOLDEN):
            source = CORPORA["golden"].pdfs / f"{doc['source'][len(FROM_GOLDEN):]}.pdf"
        else:
            source = Path(args.sources) / doc["source"]
        reader = PdfReader(str(source))
        if reader.is_encrypted:
            sys.exit(f"{doc['id']}: encrypted sources are not golden documents (open paths are device-tested)")
        pages = page_range(doc["pages"], len(reader.pages))
        if doc["pages"] == "all":
            target.write_bytes(source.read_bytes())
        else:
            writer = PdfWriter()
            for index in pages:
                writer.add_page(reader.pages[index])
            writer.add_metadata({"/Producer": "Audora golden corpus excerpt (pypdf)"})
            with target.open("wb") as out:
                writer.write(out)
        print(f"wrote {target.relative_to(REPO)} ({len(pages)} pages)")


def ground_truth(sources, doc):
    """The S2 generator's exact text, or None for real-world PDFs (drafted with pdftotext instead)."""
    stem = Path(doc["source"]).stem
    path = Path(sources) / "generated" / "ground-truth" / f"{stem}.gt.json"
    if not path.is_file():
        return None
    truth = json.loads(path.read_text(encoding="utf-8"))
    if truth["mode"] == "paged":
        pages = page_range(doc["pages"], len(truth["pages"]))
        return "\n\n".join(truth["pages"][i] for i in pages)
    if doc["pages"] != "all":
        # Flow ground truth has no page breaks: cut it by hand between the excerpt's first and last words.
        return truth["text"] + "\n\n# FLOW GROUND TRUTH: trim to the excerpt's first and last words\n"
    return truth["text"]


def command_draft(args):
    corpus = CORPORA[args.corpus]
    corpus.expected.mkdir(parents=True, exist_ok=True)
    for doc in read_corpus(corpus):
        target = corpus.expected / f"{doc['text']}.txt"
        if target.exists():
            continue
        text = ground_truth(args.sources, doc)
        if text is None:
            pdf = corpus.pdfs / f"{doc['id']}.pdf"
            text = subprocess.run(["pdftotext", "-enc", "UTF-8", str(pdf), "-"], capture_output=True, text=True,
                                  check=True).stdout
        target.write_text(text.strip() + "\n", encoding="utf-8")
        print(f"drafted {target.relative_to(REPO)}: edit it by hand (README.md, 'Expected text')")


def command_ocr(args):
    """Tesseract hOCR of every page of the corpus's scans, gzipped: what the JVM test reads instead of the phone's
    OCR. Not re-checked by CI (Tesseract builds differ); re-run only when a scan is added."""
    import pypdfium2
    corpus = CORPORA[args.corpus]
    corpus.ocr.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as workdir:
        for doc in read_corpus(corpus):
            if not doc["category"].startswith(SCAN_CATEGORY):
                continue
            langs = OCR_LANGS[doc["id"].split("-")[0]]
            document = pypdfium2.PdfDocument(str(corpus.pdfs / f"{doc['id']}.pdf"))
            try:
                for index in range(len(document)):
                    image = Path(workdir) / "page.png"
                    document[index].render(scale=OCR_DPI / 72, grayscale=True).to_pil().save(image)
                    base = Path(workdir) / "page"
                    subprocess.run(["tesseract", str(image), str(base), "-l", langs, "--dpi", str(OCR_DPI), "hocr"],
                                   check=True, capture_output=True)
                    target = corpus.ocr / f"{doc['id']}.p{index}.hocr.gz"
                    with gzip.GzipFile(target, mode="wb", compresslevel=9, mtime=0) as out:
                        out.write(base.with_suffix(".hocr").read_bytes())
                    print(f"wrote {target.relative_to(REPO)}")
            finally:
                document.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    dump = commands.add_parser("dump", help="write (or --check) the PDFium page dumps")
    dump.add_argument("--check", action="store_true", help="exit 1 if a committed dump differs")
    dump.set_defaults(run=command_dump)
    excerpt = commands.add_parser("excerpt", help="copy each document's pages from the source PDFs")
    excerpt.add_argument("--sources", required=True, help="the shared test-pdfs folder")
    excerpt.add_argument("--force", action="store_true", help="overwrite existing excerpts")
    excerpt.add_argument("--corpus", choices=sorted(CORPORA), default="golden")
    excerpt.set_defaults(run=command_excerpt)
    # Never overwrites: expected text is hand-edited.
    draft = commands.add_parser("draft", help="first draft of each missing expected-text file")
    draft.add_argument("--sources", required=True, help="the shared test-pdfs folder")
    draft.add_argument("--corpus", choices=sorted(CORPORA), default="golden")
    draft.set_defaults(run=command_draft)
    ocr = commands.add_parser("ocr", help="record Tesseract hOCR of the corpus's scanned documents")
    ocr.add_argument("--corpus", choices=sorted(CORPORA), default="listening")
    ocr.set_defaults(run=command_ocr)
    args = parser.parse_args()
    os.chdir(REPO)
    args.run(args)


if __name__ == "__main__":
    main()
