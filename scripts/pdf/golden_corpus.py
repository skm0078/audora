#!/usr/bin/env python3
# File: scripts/pdf/golden_corpus.py
# Purpose: Builds and checks the golden corpus in pdf/golden/ (P2-01): page excerpts of the source PDFs, the PDFium
#          page dumps the JVM golden test reads, and first drafts of the expected text.
#
# The JVM test (./gradlew :pdf:goldenTest) cannot run PDFium, so every golden page is dumped once with the production
# page reader (pdf/src/main/cpp/page_reader.cpp, through scripts/pdf/rawpage_dump.cpp) against pypdfium2's desktop
# libpdfium.so, and the dumps are committed. CI re-dumps them and fails if they differ, so a change to the PDFs or to
# page_reader.cpp cannot leave stale dumps behind.
#
#   python3 scripts/pdf/golden_corpus.py dump            # rewrite pdf/golden/rawpages/*.rawpages.gz
#   python3 scripts/pdf/golden_corpus.py dump --check    # CI: exit 1 if a committed dump is stale or missing
#   python3 scripts/pdf/golden_corpus.py excerpt --sources DIR   # once per new document: copy its pages from DIR
#   python3 scripts/pdf/golden_corpus.py draft --sources DIR     # once per new document: first expected-text draft
#
# Needs: pypdfium2 (pinned below; its libpdfium.so is what the dumps are made with), g++, and for excerpt/draft also
# pypdf. DIR is the shared test-PDF folder (audora/test-pdfs, see pdf/golden/README.md).

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
CORPUS = GOLDEN / "corpus.tsv"
PDFS = GOLDEN / "pdfs"
RAWPAGES = GOLDEN / "rawpages"
EXPECTED = GOLDEN / "expected"
CPP = REPO / "pdf" / "src" / "main" / "cpp"
DUMPER_SOURCE = REPO / "scripts" / "pdf" / "rawpage_dump.cpp"

# The dumps depend on the exact PDFium build. Bump together with a full `dump` and a new goldenTest run.
PYPDFIUM2_VERSION = "5.13.0"
COLUMNS = ["id", "category", "source", "pages", "text", "status"]
STATUSES = {"exact", "draft", "checked"}


def read_corpus():
    lines = CORPUS.read_text(encoding="utf-8").splitlines()
    if lines[0].split("\t") != COLUMNS:
        sys.exit(f"{CORPUS}: header must be {COLUMNS}")
    docs = []
    for number, line in enumerate(lines[1:], start=2):
        fields = line.split("\t")
        if len(fields) != len(COLUMNS):
            sys.exit(f"{CORPUS}:{number}: expected {len(COLUMNS)} tab-separated fields")
        doc = dict(zip(COLUMNS, fields))
        if doc["status"] not in STATUSES:
            sys.exit(f"{CORPUS}:{number}: status must be one of {sorted(STATUSES)}")
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
    RAWPAGES.mkdir(parents=True, exist_ok=True)
    stale = []
    with tempfile.TemporaryDirectory() as workdir:
        dumper = build_dumper(workdir)
        for doc in read_corpus():
            pdf = PDFS / f"{doc['id']}.pdf"
            if not pdf.is_file():
                sys.exit(f"missing {pdf.relative_to(REPO)}: run `excerpt` first")
            target = RAWPAGES / f"{doc['id']}.rawpages.gz"
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
    PDFS.mkdir(parents=True, exist_ok=True)
    for doc in read_corpus():
        target = PDFS / f"{doc['id']}.pdf"
        if target.exists() and not args.force:
            continue
        reader = PdfReader(str(Path(args.sources) / doc["source"]))
        if reader.is_encrypted:
            sys.exit(f"{doc['id']}: encrypted sources are not golden documents (open paths are device-tested)")
        pages = page_range(doc["pages"], len(reader.pages))
        if doc["pages"] == "all":
            target.write_bytes((Path(args.sources) / doc["source"]).read_bytes())
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
    EXPECTED.mkdir(parents=True, exist_ok=True)
    for doc in read_corpus():
        target = EXPECTED / f"{doc['text']}.txt"
        if target.exists():
            continue
        text = ground_truth(args.sources, doc)
        if text is None:
            pdf = PDFS / f"{doc['id']}.pdf"
            text = subprocess.run(["pdftotext", "-enc", "UTF-8", str(pdf), "-"], capture_output=True, text=True,
                                  check=True).stdout
        target.write_text(text.strip() + "\n", encoding="utf-8")
        print(f"drafted {target.relative_to(REPO)}: edit it by hand (README.md, 'Expected text')")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    dump = commands.add_parser("dump", help="write (or --check) the PDFium page dumps")
    dump.add_argument("--check", action="store_true", help="exit 1 if a committed dump differs")
    dump.set_defaults(run=command_dump)
    excerpt = commands.add_parser("excerpt", help="copy each document's pages from the source PDFs")
    excerpt.add_argument("--sources", required=True, help="the shared test-pdfs folder")
    excerpt.add_argument("--force", action="store_true", help="overwrite existing excerpts")
    excerpt.set_defaults(run=command_excerpt)
    # Never overwrites: expected text is hand-edited.
    draft = commands.add_parser("draft", help="first draft of each missing expected-text file")
    draft.add_argument("--sources", required=True, help="the shared test-pdfs folder")
    draft.set_defaults(run=command_draft)
    args = parser.parse_args()
    os.chdir(REPO)
    args.run(args)


if __name__ == "__main__":
    main()
