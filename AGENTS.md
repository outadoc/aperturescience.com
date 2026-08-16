# ApertureScience.com — Project Goal

This repository contains `ApertureScience17 (2007-10-17).swf`, a Flash (SWF, version 8,
CWS/zlib-compressed) file that emulates a fictional command-line prompt (an
"Aperture Science" themed terminal, in the spirit of the Portal ARG viral
marketing site of the same name).

## Goal

1. **Decompile** the SWF file to recover its ActionScript source, embedded
   assets (fonts, sounds, images), and any text/data resources (command
   names, help strings, responses, easter eggs, etc.).
2. **Understand** the behavior of the emulated command prompt: the full set
   of supported commands, their output, any state machine / storyline logic,
   timing behavior, and visual presentation.
3. **Reimplement** the same behavior as a Kotlin-based CLI program that
   faithfully reproduces the original terminal's commands and output when
   run in a real terminal (ANSI/text based, no Flash runtime required).

## Tooling notes

- SWF decompilation work happens inside a **distrobox** container
  (`apscience-swf`, based on `fedora:latest`) rather than installing
  decompiler/Java tooling on the host. Enter it with:
  `distrobox enter apscience-swf`
- JPEXS Free Flash Decompiler (`ffdec`) is the primary decompilation tool,
  run via its CLI inside the container.
- The Kotlin reimplementation lives alongside the original SWF in this repo
  once scaffolded (Gradle-based CLI project).

## What the SWF actually is

Confirmed by decompilation: this is the terminal from the 2007 *Portal* ARG
viral-marketing site. It's a fake DOS-style login/shell built around
`GLaDOS`/Aperture Science flavor text, with a 50-question fake job
application, a `CJOHNSON` admin account unlocking a `NOTES.EXE` corporate
history reader, and a `THECAKEISALIE` easter egg that toggles between a
security-camera video monologue and a disguised "boss key" spreadsheet
screen. All logic and text lives in a single ~4300-line AS2 script
(`DoAction.as`); there is no separate asset/string file.

## Repo layout

- `ApertureScience17 (2007-10-17).swf` — original source file.
- `decompiled/` — ffdec export output (`scripts/frame_1/DoAction.as` is the
  full AS2 source; `terminal_data.json` is the machine-parsed data arrays;
  `fonts/`, `images/`, `sounds/`, `sprites/`, `shapes/`, `movies/` are
  exported assets, kept for reference/fidelity checks, not otherwise used).
- `scripts/parse_as_data.py` — parses the literal `questions[]`, `cjhistory[]`,
  `qar[]`, `qdelay[]` array assignments out of `DoAction.as` into
  `decompiled/terminal_data.json`. Regenerate this if re-exporting the SWF.
- `scripts/gen_kotlin_data.py` — generates
  `cli/src/main/kotlin/com/aperturescience/terminal/data/TerminalData.kt`
  from that JSON (verbatim string literals, escaped for Kotlin). This file
  is committed as a generated artifact — do not hand-edit it, rerun the
  script instead.
- `cli/` — the Kotlin reimplementation (Gradle, Kotlin/JVM, `application`
  plugin). Builds and runs on the host directly (Gradle 9.7 / JDK — no
  distrobox needed for this half of the project, only for decompilation).

## Reimplementation plan (Kotlin CLI, `cli/`)

State machine mirrors the original `entryMode`/`qon` pair from `DoAction.as`:

1. **Login** (`entryMode 0`): `LOGON`/`LOGIN`/`USER` → username (>2 chars,
   `CJOHNSON` unlocks admin) → password (`PORTAL`/`PORTALS`, or `TIER3` for
   `CJOHNSON`) → drops into the shell.
2. **GLaDOS shell** (`entryMode 1`): dispatches on the first whitespace token
   of input (`DIR`, `HELP`, `IP`, `APPLY`, `NOTES` if admin, `LOGOUT`/`BYE`,
   `PLAY PORTAL`, etc.), `"ERROR 24 [File '<word>' not found]"` for unknown
   commands.
3. **Application** (`entryMode 2`): the 50-question wizard from
   `TerminalData.questions` (free-text / checkbox / radio types); ends with
   a fake "enter your 64-digit UIN(+L)" trap where typing `THECAKEISALIE`
   is the actual intended answer.
4. **NOTES.EXE** (`entryMode 5`, admin only): pages through
   `TerminalData.cjHistory` (4 pages).
5. **Cake / bosskey** (`entryMode 4`/`3`): `THECAKEISALIE` plays the
   security-feed monologue; any further input toggles to a fake spreadsheet
   screen and back, indefinitely — faithfully has **no scripted way back to
   the shell** in the original (real exit is Ctrl+C, matching "close the
   browser tab" in the original).

Cross-cutting concerns to port from `placeText`/`TTYwriter` in `DoAction.as`:
typewriter-style character reveal (`qdelay`/`gladosSpeed` control ms/char),
`^` → newline in banner strings, `@` → `[uid]` substitution. Server calls
(`gdxt.php?...`) and Flash `getURL()` navigation (opening YouTube/Steam on
`PLAY PORTAL`/`LOGOUT`) have no live backend — stub as no-ops, but keep the
exact terminal-visible text/behavior otherwise.

Full command/behavior spec (verbatim strings, every `qon` transition, edge
cases) was extracted from `DoAction.as` during initial analysis — re-derive
from `decompiled/scripts/frame_1/DoAction.as` directly if this file goes
stale rather than trusting a paraphrase.

## UI toolkit: Mosaic

The CLI is built on [Mosaic](https://github.com/JakeWharton/mosaic)
(`com.jakewharton.mosaic:mosaic-runtime:0.18.0`), a Jetpack-Compose-for-the-
terminal library — `kotlin("plugin.compose")` + Compose `remember`/
`mutableStateOf`/`LaunchedEffect` drive the UI. Two things worth knowing:

- Requires `mavenCentral()` **and** `google()` repositories (transitive
  `androidx.lifecycle`/`androidx.annotation` artifacts).
- `LocalStaticLogger.current` (`.log(string)` / `+=`) prints permanent,
  never-redrawn scrollback lines — used for everything the terminal has
  already "typed out". Only the in-progress typewriter line / live prompt is
  kept as recomposable `@Composable` state (`TerminalEngine.liveLine`). This
  matters for performance (the 2313-entry Q21 choice list) and is *required*
  for correctness: animating a single long unbroken line long enough to
  soft-wrap in the real terminal desyncs Mosaic's redraw bookkeeping and
  leaves stray duplicate rows behind (see `TerminalEngine.wordWrap`/
  `WRAP_WIDTH` — ported the original's own pixel-width auto-wrap for exactly
  this reason).
- Mosaic requires a real interactive TTY; it prints an error and refuses to
  run under Gradle's captured output or a plain pipe. Test manually via
  `tmux` (`tmux new-session -d -x 120 -y 50 "..."`, `tmux send-keys`,
  `tmux capture-pane -p`) or `script -qec "... " /dev/null`, not `gradle run`
  with piped input.
- Mosaic's own frame loop has built-in Ctrl+C handling, but only when no
  `onKeyEvent` in the tree already reported the key as handled - and only
  when the *ctrl* modifier is actually checked. `KeyEvent("c", ctrl = true)`
  and a plain lowercase `c` keypress both have `key == "c"`; if you only look
  at `.key` (as `App.kt` originally did) Ctrl+C is indistinguishable from
  typing the letter C and gets swallowed as text input. `TerminalEngine.
  onKeyEvent` now takes `ctrl: Boolean` explicitly and exits immediately on
  Ctrl+C before any lock/mode-specific early return, so it always works -
  including mid-typewriter-animation and inside the cake/bosskey loop, which
  otherwise has no in-story way back to the shell.

## Status (2026-08-16)

- Decompilation, data extraction, and Kotlin scaffold: done (see above).
- State machine (`TerminalEngine.kt`): **implemented** — login/username/
  password (incl. `CJOHNSON`/`TIER3` admin unlock and masked password entry),
  GLaDOS shell commands, the 50-question application wizard (incl. the
  original's off-by-one where question 50's answer is discarded), NOTES.EXE
  paging, and the `THECAKEISALIE` ⇄ bosskey toggle loop. `App.kt` wires it to
  Mosaic; `Main.kt` is the entry point.
- Verified interactively via `tmux` end-to-end: full login, admin login,
  DIR/HELP/IP/unknown-command shell errors, APPLY through several questions
  (text + choice types, invalid-choice rejection), QUIT-from-questionnaire,
  THECAKEISALIE + bosskey toggle (both directions), NOTES.EXE all 4 pages
  with correct return-to-shell, wrong-password retry, LOGOUT process exit.
  Not manually walked: the 2313-entry Q21 PgUp/PgDn pagination specifically
  (code-reviewed, shares the tested choice-rendering path) and every one of
  the 50 questions individually.
- Known deviations from the original (intentional, not bugs): scrolling
  transcript instead of screen-clear-and-redraw; rejected input isn't shown
  at all (rather than shown-then-erased); `gdxt.php` calls are no-ops and
  `uid` is a locally-synthesized random string; `LOGOUT`/`PLAY PORTAL` print
  a message and exit the process instead of navigating a browser; the cake
  video is a text placeholder (`security02.flv` isn't reproduced); cosmetic-
  only effects (glitching UID digits, random cake-image flicker) are skipped.
- Not yet done: no automated test suite (all verification so far is manual/
  interactive); `README.md` for the `cli/` project.
