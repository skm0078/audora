#!/usr/bin/env python3
# File: scripts/build_name_lists.py
# Purpose: Builds speech/src/main/resources/.../route/names-en.tsv, the English given names NameGenders knows beyond
#          its hand lists (ADR-073): names used for one gender at least 98 % of the time in the US Social Security
#          Administration's baby-name data (public domain), as github.com/hadley/data-baby-names publishes it
#          (top 1000 names a year, 1880-2008, share of births per name and sex).
#
# A name's share is summed over all years, so a name popular in any era counts. Left out: names below MIN_SHARE in
# total (too rare to be worth the bytes), names under three letters, names that books also use as common words
# (COMMON_WORDS: "Hope rose in her chest" is no character), and names books give both genders (BOTH: Sam is often
# Samantha, which the data counts as another name). A wrong-gender voice is worse than none, so when in doubt a
# name stays out. Standard library only.
#
#   python3 scripts/build_name_lists.py baby-names.csv > speech/src/main/resources/io/github/skm0078/audora/speech/route/names-en.tsv

import csv
import sys
from collections import defaultdict

ONE_GENDER = 0.98
MIN_SHARE = 0.002

COMMON_WORDS = set(
    "hope faith joy grace charity patience mercy honor glory destiny harmony unity trinity will may june april "
    "august summer autumn winter dawn sky river storm rain sunny rich bill pat art guy major king prince duke earl "
    "baron marshall dean bishop judge sterling ray rocky stone wood forest heath glen dale cliff lane chase hunter "
    "archer porter mason miles cash price penny ruby pearl amber crystal ivy iris daisy holly olive ginger candy "
    "cherry violet hazel jade jewel star mercedes dusty frank sonny buck doc red rusty bud sandy happy christian".split()
)
BOTH = set("sam alex chris jo jess kit toni sasha nicky ricky stevie frankie billie bobbie charlie".split())

HEADER = """# English given names that are one gender in at least 98 % of uses, from the US Social Security
# Administration's baby-name data 1880-2008 (public domain), via github.com/hadley/data-baby-names.
# Names used for both genders, rarer names and names that are also common words (Hope, Will, June) are left
# out. Format: name<TAB>F|M, lowercase. Regenerate with scripts/build_name_lists.py.
"""


def genders(rows):
    """name -> 'F' or 'M' for the names that are clearly one gender."""
    shares = defaultdict(lambda: [0.0, 0.0])
    for row in rows:
        shares[row["name"].lower()][0 if row["sex"] == "girl" else 1] += float(row["percent"])
    out = {}
    for name, (female, male) in shares.items():
        total = female + male
        if len(name) < 3 or total < MIN_SHARE or name in COMMON_WORDS or name in BOTH:
            continue
        if female / total >= ONE_GENDER:
            out[name] = "F"
        elif male / total >= ONE_GENDER:
            out[name] = "M"
    return out


def main(path):
    with open(path, newline="", encoding="utf-8") as source:
        names = genders(csv.DictReader(source))
    sys.stdout.write(HEADER)
    for name in sorted(names):
        sys.stdout.write(f"{name}\t{names[name]}\n")


if __name__ == "__main__":
    main(sys.argv[1])
