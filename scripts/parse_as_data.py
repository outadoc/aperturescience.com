#!/usr/bin/env python3
"""Parse the literal data arrays (questions, cjhistory, qar, qdelay) out of the
decompiled ActionScript DoAction.as file into JSON, so we can embed them
verbatim in the Kotlin port without manual transcription."""
import re
import json
import sys

SRC = "/var/home/outadoc/dev/projects/web/aperturescience.com/decompiled/scripts/frame_1/DoAction.as"

with open(SRC, encoding="utf-8") as f:
    lines = f.readlines()

# Data block is lines 1057..4187 (1-indexed) per earlier grep (qdelay ends at 4187).
block = lines[1056:4187]

def unescape_as_string(raw):
    # raw is the content between the outer double quotes, as literally written
    # in the .as source (still containing backslash escapes like \' \" \\).
    out = []
    i = 0
    while i < len(raw):
        c = raw[i]
        if c == "\\" and i + 1 < len(raw):
            nxt = raw[i + 1]
            if nxt in ("'", '"', "\\"):
                out.append(nxt)
                i += 2
                continue
        out.append(c)
        i += 1
    return "".join(out)

STR_ASSIGN = re.compile(
    r'^(?P<lhs>[A-Za-z_][A-Za-z0-9_.\[\]]*)\s*=\s*"(?P<val>(?:[^"\\]|\\.)*)"\s*;\s*$'
)
NUM_ASSIGN = re.compile(
    r'^(?P<lhs>[A-Za-z_][A-Za-z0-9_.\[\]]*)\s*=\s*(?P<val>-?\d+(?:\.\d+)?)\s*;\s*$'
)

questions = {}
cjhistory = {}
qar = {}
qdelay = {}

unmatched = []

for raw_line in block:
    line = raw_line.strip()
    if not line:
        continue
    if line.endswith("new Object();") or line.endswith("new Array();"):
        continue  # structural boilerplate, nothing to capture
    m = STR_ASSIGN.match(line)
    kind = "str"
    if not m:
        m = NUM_ASSIGN.match(line)
        kind = "num"
    if not m:
        unmatched.append(line)
        continue
    lhs = m.group("lhs")
    val = m.group("val")
    if kind == "str":
        val = unescape_as_string(val)
    else:
        val = float(val) if "." in val else int(val)

    mm = re.match(r'^questions\[(\d+)\]\.(question|type)$', lhs)
    if mm:
        idx, field = int(mm.group(1)), mm.group(2)
        questions.setdefault(idx, {"choices": {}})[field] = val
        continue
    mm = re.match(r'^questions\[(\d+)\]\.choices\[(\d+)\]$', lhs)
    if mm:
        qidx, cidx = int(mm.group(1)), int(mm.group(2))
        questions.setdefault(qidx, {"choices": {}})["choices"][cidx] = val
        continue
    mm = re.match(r'^cjhistory\[(\d+)\]\.question$', lhs)
    if mm:
        cjhistory[int(mm.group(1))] = val
        continue
    mm = re.match(r'^qar\[(\d+)\]$', lhs)
    if mm:
        qar[int(mm.group(1))] = val
        continue
    mm = re.match(r'^qdelay\[(\d+)\]$', lhs)
    if mm:
        qdelay[int(mm.group(1))] = val
        continue
    unmatched.append(line + "  <-- matched regex but no target array (probably fine, other var)")

def to_ordered_list(d, start=0):
    if not d:
        return []
    max_idx = max(d.keys())
    return [d.get(i) for i in range(start, max_idx + 1)]

questions_out = []
for idx in sorted(questions.keys()):
    q = questions[idx]
    choices = q.get("choices", {})
    choices_list = [choices[i] for i in sorted(choices.keys())] if choices else []
    questions_out.append({
        "index": idx,
        "question": q.get("question"),
        "type": q.get("type"),
        "choices": choices_list,
    })

result = {
    "questions": questions_out,
    "cjhistory": to_ordered_list(cjhistory, start=1),
    "qar": to_ordered_list(qar, start=0),
    "qdelay": to_ordered_list(qdelay, start=0),
}

with open(sys.argv[1] if len(sys.argv) > 1 else "/dev/stdout", "w", encoding="utf-8") as out:
    json.dump(result, out, ensure_ascii=False, indent=2)

print(f"questions parsed: {len(questions_out)}", file=sys.stderr)
print(f"cjhistory parsed: {len(result['cjhistory'])}", file=sys.stderr)
print(f"qar parsed: {len(result['qar'])}", file=sys.stderr)
print(f"qdelay parsed: {len(result['qdelay'])}", file=sys.stderr)
print(f"unmatched lines: {len(unmatched)}", file=sys.stderr)
for u in unmatched[:50]:
    print("  UNMATCHED:", u, file=sys.stderr)
