#!/usr/bin/env python3
"""Generate TerminalData.kt (verbatim source strings) from terminal_data.json."""
import json
import sys

SRC = "/var/home/outadoc/dev/projects/web/aperturescience.com/decompiled/terminal_data.json"
DST = "/var/home/outadoc/dev/projects/web/aperturescience.com/cli/logic/src/main/kotlin/com/aperturescience/terminal/data/TerminalData.kt"

TYPE_MAP = {"T": "TEXT", "C": "CHECKBOX", "R": "RADIO"}


def kt_str(s: str) -> str:
    escaped = (
        s.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("$", "\\$")
        .replace("\n", "\\n")
        .replace("\t", "\\t")
    )
    return f'"{escaped}"'


def main():
    with open(SRC, encoding="utf-8") as f:
        data = json.load(f)

    lines = []
    lines.append("// GENERATED FILE — do not edit by hand.")
    lines.append("// Source data extracted verbatim from the decompiled ActionScript")
    lines.append("// (DoAction.as) of ApertureScience17 (2007-10-17).swf via")
    lines.append("// scripts/gen_kotlin_data.py (from repo root). Regenerate rather than hand-edit.")
    lines.append("package com.aperturescience.terminal.data")
    lines.append("")
    lines.append("enum class QuestionType { TEXT, CHECKBOX, RADIO }")
    lines.append("")
    lines.append("data class Question(")
    lines.append("    val index: Int,")
    lines.append("    val text: String,")
    lines.append("    val type: QuestionType,")
    lines.append("    val choices: List<String>,")
    lines.append(")")
    lines.append("")
    lines.append("object TerminalData {")

    # qar
    lines.append("    /** Login / application-flow prompt banners, indexed by qon (0..11). */")
    lines.append("    val qar: List<String> = listOf(")
    for s in data["qar"]:
        lines.append(f"        {kt_str(s)},")
    lines.append("    )")
    lines.append("")

    # qdelay
    lines.append("    /** Typewriter delay in ms/char, parallel to [qar]. */")
    lines.append("    val qdelay: List<Int> = listOf(")
    lines.append("        " + ", ".join(str(int(x)) for x in data["qdelay"]))
    lines.append("    )")
    lines.append("")

    # cjhistory
    lines.append("    /** CJOHNSON-only NOTES.EXE history pages (1..4). */")
    lines.append("    val cjHistory: List<String> = listOf(")
    for s in data["cjhistory"]:
        lines.append(f"        {kt_str(s)},")
    lines.append("    )")
    lines.append("")

    # questions
    lines.append("    /** The 50-question job application questionnaire, in order. */")
    lines.append("    val questions: List<Question> = listOf(")
    for q in data["questions"]:
        qtype = TYPE_MAP[q["type"]]
        choices_str = ", ".join(kt_str(c) for c in q["choices"])
        lines.append(f"        Question({q['index']}, {kt_str(q['question'])}, QuestionType.{qtype}, listOf({choices_str})),")
    lines.append("    )")

    lines.append("}")
    lines.append("")

    with open(DST, "w", encoding="utf-8") as out:
        out.write("\n".join(lines))

    print(f"Wrote {DST}", file=sys.stderr)
    print(f"  qar: {len(data['qar'])}, qdelay: {len(data['qdelay'])}, cjhistory: {len(data['cjhistory'])}, questions: {len(data['questions'])}", file=sys.stderr)


if __name__ == "__main__":
    main()
