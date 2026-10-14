# File: research/spikes/S2-pdf-extraction/scripts/metrics.py
# Purpose: Text-quality metrics for S2: CER, Indic matra-order errors, script share, legacy-font detection.
import re, unicodedata
from collections import Counter
from rapidfuzz.distance import Levenshtein

WS = re.compile(r"\s+")
DEVA_CONS = set(range(0x0915, 0x093A)) | set(range(0x0958, 0x0960)) | {0x0978, 0x0979, 0x097A, 0x097B, 0x097C, 0x097D, 0x097E, 0x097F}
BENG_CONS = set(range(0x0995, 0x09BA)) | {0x09CE, 0x09DC, 0x09DD, 0x09DF, 0x09F0, 0x09F1}
DEVA_SIGNS = set(range(0x093E, 0x094D)) | {0x0962, 0x0963}
BENG_SIGNS = set(range(0x09BE, 0x09CD)) | {0x09D7, 0x09E2, 0x09E3}
NUKTA_VIRAMA = {0x093C, 0x094D, 0x09BC, 0x09CD, 0x200C, 0x200D}
PRE_BASE = {0x093F, 0x09BF, 0x09C7, 0x09C8}  # ि ি ে ৈ

LEGACY_FONT_PAT = re.compile(r"(kruti|devlys|chanakya|agra|shusha|walkman|aps-?dv|akruti|sutonny|bijoy|\bmj\b|mj$|-mj|salma|anandabazar|boishakhi|kalpurush ansi)", re.I)


def norm(s, drop_ws=False):
    s = unicodedata.normalize("NFC", s).replace("­", "").replace("\u0002", "")
    s = s.replace("￾", "")
    return WS.sub("", s) if drop_ws else WS.sub(" ", s).strip()


def cer(ref, hyp, drop_ws=False):
    r, h = norm(ref, drop_ws), norm(hyp, drop_ws)
    if not r:
        return 0.0 if not h else 1.0
    return Levenshtein.distance(r, h) / len(r)


def invalid_sign_count(text):
    """Dependent vowel signs not preceded by a consonant/nukta/virama: the fingerprint of visual-order extraction."""
    bad = 0
    prev = None
    for ch in text:
        c = ord(ch)
        if c in DEVA_SIGNS or c in BENG_SIGNS:
            p = ord(prev) if prev else 0
            if not (p in DEVA_CONS or p in BENG_CONS or p in NUKTA_VIRAMA or p in DEVA_SIGNS or p in BENG_SIGNS or p in (0x0901, 0x0902, 0x0981, 0x0982)):
                bad += 1
        prev = ch
    return bad


def prebase_word_recall(ref, hyp):
    """Share of reference words containing a pre-base matra that appear verbatim in the hypothesis."""
    rw = [w for w in norm(ref).split() if any(ord(c) in PRE_BASE for c in w)]
    if not rw:
        return None
    hw = Counter(norm(hyp).split())
    hit = 0
    for w in rw:
        if hw[w] > 0:
            hit += 1; hw[w] -= 1
    return hit / len(rw)


def word_recall(ref, hyp):
    rw, hw = Counter(norm(ref).split()), Counter(norm(hyp).split())
    tot = sum(rw.values())
    return sum(min(c, hw[w]) for w, c in rw.items()) / tot if tot else None


def script_share(text):
    """Fraction of letters per script; used by the quality scorer."""
    cnt = Counter()
    for ch in text:
        if not ch.isalpha() and unicodedata.category(ch)[0] != "M":
            continue
        c = ord(ch)
        if 0x0900 <= c <= 0x097F:
            cnt["deva"] += 1
        elif 0x0980 <= c <= 0x09FF:
            cnt["beng"] += 1
        elif c < 0x0250:
            cnt["latin"] += 1
        else:
            cnt["other"] += 1
    tot = sum(cnt.values()) or 1
    return {k: round(v / tot, 3) for k, v in cnt.items()}


FURNITURE = re.compile(r"^\s*(Audora S2 test corpus.*|\d{1,4})\s*$")


def strip_furniture(text):
    """Drop the synthetic corpus header and bare page-number lines (P2-03 removes these in the app)."""
    lines = re.split(r"\r\n|\n|\r", text)
    return "\n".join(l for l in lines if not FURNITURE.match(l))
