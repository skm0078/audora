# ADR-077: Hindi and Bengali characters are found by repetition

**Status:** Accepted (2026-10-25). Extends ADR-075.

## Context
ADR-075 trusts a name in narration only when it can tell a name from a noun: English by capitals, Hindi and Bengali by
the name lists (`NameGenders`). Hindi and Bengali print no capitals, so a story's own names ("बिरजू", "झुमकी",
"পল্টু") were never trusted. On the held-out set that ADR-075 was never tuned on, the right speaker was found for
7 % of Hindi lines with speech; on a second set written for this change, 0 % in both languages.

Bengali also opens many sentences with a place, a time or a participle ("ছাদে উঠে পল্টু দেখল", "মাছের দাম শুনে
হরিপদ থমকে দাঁড়াল"), so the first word was taken as the subject.

## Decision
1. **A word that opens Hindi or Bengali narration at least twice on a page is one of the story's people**
   (`SpeakerTurns.recurring`, kept in `Turns.cast` for later pages and across headings). A noun that opens one
   sentence ("बादल घिर आए थे") stays untrusted. A Hindi verb's ending still teaches the gender (बैठी, आई).
2. **A Bengali clause's subject is said bare:** words in "-এ" (place, time, participle) and "-তেই" are passed over,
   and an owner in "-এর" is passed over with the noun it owns. Pronouns (সে) are never skipped.

## Results (right speaker / right-gender voice / wrong-gender voice, lines with speech)
| Set | Before | After |
|---|---|---|
| Tuned, Hindi | 84 / 88 / 0 | 84 / 88 / 0 |
| Tuned, Bengali | 100 / 100 / 0 | 100 / 100 / 0 |
| Held-out, Hindi | 7 / 7 / 0 | 38 / 38 / 0 |
| Held-out, Bengali | 46 / 0 / 0 | 46 / 0 / 0 |
| Second held-out, Hindi | 0 / 0 / 0 | 80 / 40 / 20 (1 of 5 lines) |
| Second held-out, Bengali | 0 / 0 / 0 | 83 / 0 / 0 |

The second held-out set was scored once with rule 1 (Hindi 80, Bengali 33) before rule 2's owner case was added
from its misses, so its Bengali figure is partly tuned.

## Consequences
- One wrong voice in the second set: a man comes home late and his wife asks "where were you?" in the same
  paragraph; the narration's last subject (him) is taken. A wrong voice is worse than none, so this is watched by
  the eval test (at most one).
- Bengali names still get no voice: Bengali verbs carry no gender, so a name not in the lists stays UNKNOWN and is
  read by the dialogue voice. A Bengali name list, or the optional story brain, would fix that.
