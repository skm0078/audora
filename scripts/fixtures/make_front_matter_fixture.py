#!/usr/bin/env python3
# File: scripts/fixtures/make_front_matter_fixture.py
# Purpose: Writes en-front-matter.pdf for the release journey in app/src/androidTest/assets/pdfs: a 24-page book
#          with a title page, a copyright page, contents and a two-page preface before its first chapter, and a
#          chapter list (outline) naming them, so a new book can be seen opening at Chapter 1
#          (ADR 2026-10-31-skip-front-matter). Our own text, free to copy.
# Run: python3 make_front_matter_fixture.py   (needs reportlab)

import os

from reportlab.lib.pagesizes import A5
from reportlab.pdfgen import canvas
from reportlab import rl_config

OUT = os.path.join(
    os.path.dirname(__file__), "..", "..", "app", "src", "androidTest", "assets", "pdfs", "en-front-matter.pdf"
)
PAGES = 24
MARGIN = 48
LINE = 15

# The chapter title the journey expects on the card: "Started at “Chapter 1: The Light”, page 6".
CHAPTERS = {5: "Chapter 1: The Light", 12: "Chapter 2: The Storm", 18: "Chapter 3: The Morning"}
PROSE = (
    "The lighthouse stood at the end of the long grey road, and every evening the keeper climbed its stairs to "
    "light the lamp. The ships far out at sea saw the beam and knew where the rocks were. "
)


def text_block(c: canvas.Canvas, top: float, words: str, size: int = 11) -> None:
    c.setFont("Helvetica", size)
    width = A5[0] - 2 * MARGIN
    line, y = "", top
    for word in words.split():
        trial = f"{line} {word}".strip()
        if c.stringWidth(trial, "Helvetica", size) > width:
            c.drawString(MARGIN, y, line)
            line, y = word, y - LINE
        else:
            line = trial
    if line:
        c.drawString(MARGIN, y, line)


def heading(c: canvas.Canvas, text: str) -> float:
    c.setFont("Helvetica-Bold", 20)
    c.drawString(MARGIN, A5[1] - MARGIN - 20, text)
    return A5[1] - MARGIN - 60


def page(c: canvas.Canvas, number: int) -> None:
    top = A5[1] - MARGIN
    if number == 0:
        c.setFont("Helvetica-Bold", 24)
        c.drawCentredString(A5[0] / 2, A5[1] / 2 + 20, "The Lighthouse Keeper")
        c.setFont("Helvetica", 12)
        c.drawCentredString(A5[0] / 2, A5[1] / 2 - 10, "A test book")
    elif number == 1:
        text_block(c, top, "Copyright 2026. First published 2026 by a small press. All rights reserved. "
                           "ISBN 000-0-00-000000-0. Printed in India.")
    elif number == 2:
        y = heading(c, "Contents")
        c.setFont("Helvetica", 11)
        for line in ["Preface .......... 4", *[f"{t} .......... {p + 1}" for p, t in CHAPTERS.items()]]:
            c.drawString(MARGIN, y, line)
            y -= LINE * 2
    elif number == 3:
        text_block(c, heading(c, "Preface"), "This preface says why the book was written. " * 12)
    elif number == 4:
        text_block(c, top, "The preface goes on to thank everyone who helped along the way. " * 10)
    elif number in CHAPTERS:
        text_block(c, heading(c, CHAPTERS[number]), PROSE * 4)
    else:
        text_block(c, top, PROSE * 5)


def main() -> None:
    rl_config.invariant = 1  # the same bytes on every run
    c = canvas.Canvas(OUT, pagesize=A5)
    c.setTitle("The Lighthouse Keeper")
    outline = {0: "Cover", 1: "Copyright", 2: "Contents", 3: "Preface", **CHAPTERS}
    for number in range(PAGES):
        page(c, number)
        if number in outline:
            key = f"p{number}"
            c.bookmarkPage(key)
            c.addOutlineEntry(outline[number], key, level=0)
        c.showPage()
    c.save()
    print(f"Wrote {os.path.normpath(OUT)}")


if __name__ == "__main__":
    main()
