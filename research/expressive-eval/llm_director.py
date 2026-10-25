#!/usr/bin/env python3
# File: research/expressive-eval/llm_director.py
# Purpose: Asks a small on-device-sized LLM (llama.cpp server) who speaks each sentence of the marked passages and how,
#          scores it like ExpressiveEval.kt, and times it, so the plan can say whether an on-phone "director" is worth it.
#          With --brainhost it asks the app's own story brain instead (ADR-078): the app's prompt (brain/prompt.txt),
#          its answer grammar and its native code (brainhost/, built on a PC), on the spoken lines only.

import argparse
import json
import re
import subprocess
import sys
import tempfile
import time
import urllib.request
from pathlib import Path

FEELINGS = ["neutral", "joy", "sad", "angry", "scared", "surprised", "soft", "loud"]
CLOSE = {("angry", "loud"), ("joy", "loud"), ("sad", "soft"), ("scared", "soft")}
OPPOSITE = {("sad", "joy"), ("scared", "joy"), ("angry", "joy"), ("joy", "sad"), ("angry", "soft"), ("loud", "soft")}
LANG_NAMES = {"en": "English", "hi": "Hindi", "bn": "Bengali"}

PROMPT = """You prepare a {lang} story for an audiobook narrator who gives each character their own voice.
Below are the story's sentences, numbered; a blank line separates paragraphs.
For EVERY sentence, give:
- "speaker": the character who speaks the quoted words in that sentence, using the name or description the story uses
  for them (for example "Tom", "the old woman", "दादी", "বাবা"). If the sentence has no spoken words, use "narrator".
  Words inside quote marks (“ ” ‘ ’ " ") are spoken. A spoken line without a tag is usually the other person in a
  two-person conversation; a speech can run over several sentences.
- "feeling": how the sentence should be read, one of: neutral, joy, sad, angry, scared, surprised, soft (gentle,
  whispered or tender), loud (shouting or calling out without anger). Judge from the scene, not only from cue words:
  an exclamation can be angry, scared or happy. Use neutral when nothing special is felt.
Also list the "cast": every character who speaks, with "gender" F or M (use names, titles, relations, verb endings).

Sentences:
{sentences}
"""


def load(path):
    lang = path.stem
    passages, cur, cast, para = [], None, {}, 0
    for raw in path.read_text(encoding="utf-8").splitlines():
        row = raw.rstrip()
        if row.startswith("@cast "):
            cast = dict(m.groups() for m in re.finditer(r"(.+?)=([FM])(?:\s+|$)", row[6:]))
            cast = {k.strip(): v for k, v in cast.items()}
            cur["cast"] = cast
        elif row.startswith("@ "):
            cur = {"id": row[2:].strip(), "lang": lang, "cast": {}, "lines": []}
            passages.append(cur)
            para = 0
        elif row.startswith("#"):
            continue
        elif not row.strip():
            para += 1
        else:
            text, speaker, feeling = row.split("\t")
            cur["lines"].append({"text": text, "speaker": None if speaker == "-" else speaker, "feeling": feeling,
                                 "para": para})
    return passages


def schema(n):
    return {
        "type": "object",
        "properties": {
            "cast": {"type": "array", "items": {
                "type": "object",
                "properties": {"name": {"type": "string"}, "gender": {"type": "string", "enum": ["F", "M"]}},
                "required": ["name", "gender"]}},
            "lines": {"type": "array", "minItems": n, "maxItems": n, "items": {
                "type": "object",
                "properties": {"n": {"type": "integer"}, "speaker": {"type": "string"},
                               "feeling": {"type": "string", "enum": FEELINGS}},
                "required": ["n", "speaker", "feeling"]}},
        },
        "required": ["cast", "lines"],
    }


def numbered(lines):
    out, para = [], lines[0]["para"] if lines else 0
    for i, line in enumerate(lines, 1):
        if line["para"] != para:
            out.append("")
            para = line["para"]
        out.append(f"{i}. {line['text']}")
    return "\n".join(out)


def ask(server, passage):
    lines = passage["lines"]
    body = {
        "messages": [{"role": "user", "content": PROMPT.format(lang=LANG_NAMES[passage["lang"]],
                                                                sentences=numbered(lines))}],
        "temperature": 0,
        "max_tokens": 60 * len(lines) + 200,
        "response_format": {"type": "json_object", "schema": schema(len(lines))},
        "chat_template_kwargs": {"enable_thinking": False},
    }
    req = urllib.request.Request(f"{server}/v1/chat/completions", data=json.dumps(body).encode(),
                                 headers={"Content-Type": "application/json"})
    started = time.time()
    with urllib.request.urlopen(req, timeout=1800) as resp:
        data = json.load(resp)
    wall = time.time() - started
    content = data["choices"][0]["message"]["content"]
    try:
        answer = json.loads(content)
    except json.JSONDecodeError:
        answer = {"cast": [], "lines": []}
    return answer, data.get("timings", {}), wall


APP_PROMPT = Path(__file__).resolve().parents[2] / "speech/src/main/resources/brain/prompt.txt"


def app_request(passage):
    """The app's question for one passage, as StoryBrainPrompt builds it: spoken lines (marked *) answered in order."""
    lines = passage["lines"]
    numbers = [i for i, line in enumerate(lines, 1) if line["speaker"]]
    out = []
    for i, line in enumerate(lines, 1):
        if i > 1:
            out.append("\n\n" if line["para"] != lines[i - 2]["para"] else "\n")
        out.append(f"{i}{'* ' if line['speaker'] else '. '}{' '.join(line['text'].split())}")
    prompt = (APP_PROMPT.read_text(encoding="utf-8").replace("{language}", LANG_NAMES[passage["lang"]])
              .replace("{context}", "").replace("{sentences}", "".join(out)))
    grammar = ("root ::= " + " ".join(f'"{n}|" who "|" how "\\n"' for n in numbers) + " cast\n"
               + "who ::= [^|\\n]{1,40}\n"
               + "how ::= " + " | ".join(f'"{f}"' for f in FEELINGS) + "\n"
               + 'cast ::= "cast: " pair (", " pair){0,' + str(min(len(numbers), 12) - 1) + '} "\\n"\n'
               + 'pair ::= [^|\\n=,]{1,40} "=" [FM]\n')
    return prompt, grammar, numbers, len(numbers) * 16 + 120


def ask_app(brainhost, gguf, passage):
    """The app brain's answer, read into the {cast, lines} shape [score] takes; narration lines are left unanswered."""
    prompt, grammar, numbers, max_tokens = app_request(passage)
    if not numbers:
        return {"cast": [], "lines": []}, {}, 0
    with tempfile.TemporaryDirectory() as tmp:
        (Path(tmp) / "p.txt").write_text(prompt, encoding="utf-8")
        (Path(tmp) / "g.txt").write_text(grammar, encoding="utf-8")
        started = time.time()
        done = subprocess.run([brainhost, gguf, str(Path(tmp) / "p.txt"), str(Path(tmp) / "g.txt"), str(max_tokens)],
                              capture_output=True, text=True, timeout=1800, check=False)
        wall = time.time() - started
    text, _, tail = done.stdout.rpartition("\n--- ")
    stats = dict(kv.split("=") for kv in tail.split() if "=" in kv)
    answer = {"cast": [], "lines": []}
    for row in text.splitlines():
        if row.startswith("cast:"):
            for pair in row[5:].split(","):
                name, _, gender = pair.strip().rpartition("=")
                answer["cast"].append({"name": name.strip(), "gender": gender.strip()})
        elif row.count("|") == 2:
            n, who, how = row.split("|")
            if n.strip().isdigit():
                answer["lines"].append({"n": int(n), "speaker": who.strip(), "feeling": how.strip()})
    timing = {"prompt_n": int(stats.get("prompt_tokens", 0)), "prompt_ms": 0,
              "predicted_n": 0, "predicted_ms": int(stats.get("ms", 0))}
    return answer, timing, wall


def bare(name):
    name = (name or "").strip().lower()
    for prefix in ("the ", "a ", "their ", "his ", "her "):
        if name.startswith(prefix):
            name = name[len(prefix):]
    return name


def same(found, gold):
    """A found name matches when it is the marked one, or holds it ("Mr Hale" / "Hale", "सेठ जी" / "सेठ")."""
    f, g = bare(found), bare(gold)
    return bool(f) and (f == g or f in g.split() or g in f.split() or f.replace(" ", "") == g.replace(" ", ""))


def score(passages, answers):
    rows = []
    for passage, answer in zip(passages, answers):
        cast = {bare(c.get("name")): c.get("gender") for c in answer.get("cast", [])}
        by_n = {item.get("n"): item for item in answer.get("lines", [])}
        for i, line in enumerate(passage["lines"], 1):
            listed = answer.get("lines") or []
            item = by_n.get(i) or (listed[i - 1] if i <= len(listed) else {})
            speaker = item.get("speaker", "narrator")
            feeling = item.get("feeling", "neutral")
            gender = None
            if speaker and bare(speaker) != "narrator":
                gender = cast.get(bare(speaker)) or next((g for k, g in cast.items() if same(k, speaker)), None)
            gold_gender = passage["cast"].get(line["speaker"]) if line["speaker"] else None
            voice = "none" if gender is None else ("right" if gender == gold_gender else "wrong")
            rows.append({"passage": passage["id"], "lang": passage["lang"], "text": line["text"],
                         "spoken": bool(line["speaker"]),
                         "gold_speaker": line["speaker"], "speaker": speaker,
                         "speaker_right": bool(line["speaker"]) and same(speaker, line["speaker"]),
                         "voice": voice, "gold_feeling": line["feeling"], "feeling": feeling})
    return rows


def pct(a, b):
    return "-" if b == 0 else f"{a * 100 // b} %"


def report(model, rows, timings, spoken_only=False):
    if spoken_only:
        rows = [r for r in rows if r["spoken"]]
    out = [f"### {model}" + (" (spoken lines only: narration keeps the rules' feelings)" if spoken_only else ""), "",
           "| Lang | Lines with speech | Right speaker | Right-gender voice | Wrong-gender voice |",
           "|---|---:|---:|---:|---:|"]
    for lang in ("en", "hi", "bn"):
        sp = [r for r in rows if r["lang"] == lang and r["gold_speaker"]]
        out.append(f"| {lang.upper()} | {len(sp)} | {pct(sum(r['speaker_right'] for r in sp), len(sp))} | "
                   f"{pct(sum(r['voice'] == 'right' for r in sp), len(sp))} | "
                   f"{pct(sum(r['voice'] == 'wrong' for r in sp), len(sp))} |")
    out += ["", "| Lang | Lines | Feeling right | Near enough | Feeling missed | Neutral coloured | Opposite |",
            "|---|---:|---:|---:|---:|---:|---:|"]
    for lang in ("en", "hi", "bn"):
        rs = [r for r in rows if r["lang"] == lang]
        felt = [r for r in rs if r["gold_feeling"] != "neutral"]
        calm = [r for r in rs if r["gold_feeling"] == "neutral"]
        near = sum(r["feeling"] == r["gold_feeling"] or (r["gold_feeling"], r["feeling"]) in CLOSE for r in rs)
        out.append(f"| {lang.upper()} | {len(rs)} | {pct(sum(r['feeling'] == r['gold_feeling'] for r in rs), len(rs))} |"
                   f" {pct(near, len(rs))} | {pct(sum(r['feeling'] == 'neutral' for r in felt), len(felt))} | "
                   f"{pct(sum(r['feeling'] != 'neutral' for r in calm), len(calm))} | "
                   f"{sum((r['gold_feeling'], r['feeling']) in OPPOSITE for r in rs)} |")
    pn = sum(t.get("prompt_n", 0) for t in timings)
    pms = sum(t.get("prompt_ms", 0) for t in timings)
    gn = sum(t.get("predicted_n", 0) for t in timings)
    gms = sum(t.get("predicted_ms", 0) for t in timings)
    sentences = len(rows)
    out += ["", f"Speed on this runner: prompt {pn} tokens at {pn / max(pms, 1) * 1000:.1f} tok/s, "
                f"output {gn} tokens at {gn / max(gms, 1) * 1000:.1f} tok/s; "
                f"{(pms + gms) / max(sentences, 1):.0f} ms per sentence ({sentences} sentences)."]
    return "\n".join(out) + "\n"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--server", default="http://127.0.0.1:8080")
    ap.add_argument("--model", required=True)
    ap.add_argument("--passages", nargs="+", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--brainhost", help="the app brain's host build (brainhost/); asks it instead of --server")
    ap.add_argument("--gguf", help="the model file for --brainhost")
    args = ap.parse_args()
    passages = [p for f in args.passages for p in load(Path(f))]
    answers, timings = [], []
    for passage in passages:
        try:
            if args.brainhost:
                answer, timing, wall = ask_app(args.brainhost, args.gguf, passage)
            else:
                answer, timing, wall = ask(args.server, passage)
        except Exception as error:  # noqa: BLE001 - one passage failing must not hide the others' scores
            print(f"{passage['id']}: {error}", file=sys.stderr)
            answer, timing, wall = {"cast": [], "lines": []}, {}, 0
        print(f"{passage['id']}: {wall:.1f} s", file=sys.stderr)
        answers.append(answer)
        timings.append(timing)
    rows = score(passages, answers)
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    (out / f"{args.model}.json").write_text(json.dumps({"answers": answers, "timings": timings, "rows": rows},
                                                       ensure_ascii=False, indent=1), encoding="utf-8")
    text = report(args.model, rows, timings, spoken_only=bool(args.brainhost))
    (out / f"{args.model}.md").write_text(text, encoding="utf-8")
    print(text)


if __name__ == "__main__":
    main()
