# ADR 2026-10-26-natural-table-speech: tables are read the way a person explains them

**Status:** Accepted (2026-10-26). Changes the spoken templates of RI-4b; the table modes and `:pdf` stay as they were.

## Context
Testing on 25 Oct judged tables and footnotes "not too good, gets robotic" (quality ticket Q-4). The templates were
"4 rows, 5 columns." for a table and "Row 3. Name: Ram. Age: 34. City: Delhi." for each row. Every cell ended in a
full stop, so the voice fell in pitch and paused for a whole sentence after each one, and the listener heard a row
number nobody says aloud. The Summary mode said only the table's size, which tells the listener nothing about what it
holds. Footnotes were read in exactly the voice of the text, so nothing marked where the page ended and its notes
began.

## Decision
1. **A summary says what the table holds:** "A table of 4 rows, with columns for Name, Age, and City." When there are
   more than four columns, or some are unnamed, it names the first three distinct ones: "A table of 4 rows and 9
   columns, including Year, Sales, and Profit." Brackets after a column name ("(kg)", "(n = 104)") are not said. A
   carried-over table says "The table continues, with 7 more rows."
2. **A row is one sentence led by its name:** the first cell names the row and the others follow with their headers,
   joined by commas and an "and": "Ram: Age 34 and City Delhi." A first cell that is only a number keeps its header
   in front ("Year 2021: Sales 40 and Profit 8."). A row of more than four cells is split into sentences of four, so
   a wide row still has places to breathe. A row with nothing to say falls back to "Row 3." so it is never silent.
3. **Units go where a person says them:** a header's unit in brackets follows a number: "Weight (kg)" and 34 read
   "Weight 34 kg", "Growth (%)" "Growth 12%", "Price (₹ crore)" "Price ₹40 crore". A bracket holding a digit (a year)
   or three or more words is not a unit.
4. **Footnotes are read a little quicker (×1.08) and 3 dB softer** than the text around them, on top of whatever
   delivery the sentence has (`FootnoteVoiceConfig`), whether or not "Read to suit the book" is on.
5. Hindi and Bengali use the same structure with their own words (और, আর; Hindi's oblique plural before की).
6. The setting's label changes from "Size only" to "Summary", since it now says more than the size.

## Consequences
- What the voice says for tables changes, so `STUDIO_AUDIO_VERSION` goes to 5 and saved studio audio is rendered
  again. `:pdf` output does not change, so `PIPELINE_VERSION` and resume anchors are untouched.
- The listening corpus script for the table document is updated to the new summary wording (the script is what a
  careful reader says); its score must not drop.
- Hindi and Bengali wording is checked by ear on the test phone before release; it is a template, so a fix is one line.
- Rejected: dropping headers after the first row (shorter, but a listener who drifts off loses which number is which);
  a verb per cell ("sales were 40"), which needs grammar per header and goes wrong on plural or unit headers.
