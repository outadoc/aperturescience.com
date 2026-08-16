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
  `cli/logic/src/commonMain/kotlin/com/aperturescience/terminal/data/TerminalData.kt`
  from that JSON (verbatim string literals, escaped for Kotlin). This file
  is committed as a generated artifact — do not hand-edit it, rerun the
  script instead.
- `cli/` — the Kotlin reimplementation (Gradle, Kotlin Multiplatform: `jvm()`,
  `linuxX64()`, and `wasmJs()` targets, version catalog at
  `cli/gradle/libs.versions.toml`). Three modules: `cli/logic/` (the whole
  state machine, `TerminalEngine.kt` + `data/TerminalData.kt` in
  `src/commonMain/` — plain Kotlin/coroutines, zero dependency on
  Mosaic/Compose, kotlinx-browser, or any other UI framework; tests in
  `src/commonTest/` run on all three targets); `cli/ui-terminal/` (the
  Mosaic-based UI, depends on `:logic`; `App.kt`/`AppRunner.kt`/`Platform.kt`
  live in `src/commonMain/`, with a tiny per-target `Main.kt` +
  `Platform.kt` actual in `src/jvmMain/` and `src/nativeMain/` — see "UI
  toolkit: Mosaic" below for why the platform split exists at all); and
  `cli/ui-web/` (the browser UI, `wasmJs()`-only, depends on `:logic`,
  drives the DOM directly with `kotlinx-browser` instead of Mosaic — see
  "Web frontend: ui-web" below). Builds and runs on the host directly
  (Gradle 9.7 / JDK — no distrobox needed for this half of the project, only
  for decompilation). `:ui-terminal:shadowJar` produces the JVM fat jar;
  `:ui-terminal:linkReleaseExecutableLinuxX64` produces a standalone native
  Linux binary that needs no JVM at all; `:ui-web:wasmJsBrowserDistribution`
  produces the static site (`.wasm` + `.js` + `index.html`/`styles.css`) in
  `cli/ui-web/build/dist/wasmJs/productionExecutable/`.

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

`cli/ui-terminal/` is built on [Mosaic](https://github.com/JakeWharton/mosaic)
(`com.jakewharton.mosaic:mosaic-runtime:0.18.0`), a Jetpack-Compose-for-the-
terminal library — `kotlin("plugin.compose")` + Compose `remember`/
`mutableStateOf`/`LaunchedEffect` drive `App.kt`. None of this reaches
`cli/logic/`: `TerminalEngine` exposes its screen content as a plain
`StateFlow<String>` (`liveLine`) with zero Compose/Mosaic coupling; `App.kt`
is what binds it to a `@Composable Text()`, via `collectAsState()`. Two
things worth knowing:

- Requires `mavenCentral()` **and** `google()` repositories (transitive
  `androidx.lifecycle`/`androidx.annotation` artifacts).
- `App.kt`'s whole current page is a single dynamic `@Composable` `Text()`
  bound to `engine.liveLine.collectAsState()`, **not**
  `LocalStaticLogger`/`StaticEffect` (Mosaic's Ink.js-style "permanent,
  never-redrawn scrollback" mechanism — there's no such thing in the
  original, which always clears and redraws its whole canvas, so a
  never-cleared log would be unfaithful). `Text()` splits its `value` on
  `\n` and remeasures `height` every recomposition (see
  `mosaic-runtime/.../text/TextLayout.kt`), so replacing the whole string
  wholesale is exactly what makes Mosaic erase the old (possibly taller)
  page and redraw the new one — this *is* the clear-and-redraw effect,
  achieved through Mosaic's own diffing rather than manual ANSI clear
  codes, which would desync its redraw bookkeeping. `TerminalEngine` itself
  builds that same full-page string internally (`pageContent`) before
  publishing it to `liveLine` — that accumulation logic lives in `logic` and
  has nothing to do with Mosaic; only the `Text()`/`collectAsState()` side
  in `ui-terminal` is Mosaic-specific.
- Word-wrap unbroken text to `WRAP_WIDTH` before animating it
  (`TerminalEngine.wordWrap`, porting the original's own pixel-width
  auto-wrap). Without it, a single long line can soft-wrap in the real
  terminal while still being typed character-by-character, which desyncs
  Mosaic's redraw bookkeeping and leaves stray duplicate rows behind.
- Mosaic requires a real interactive TTY; it prints an error and refuses to
  run under Gradle's captured output or a plain pipe. Test manually via
  `tmux` (`tmux new-session -d -x 120 -y 50 "..."`, `tmux send-keys`,
  `tmux capture-pane -p`) or `script -qec "... " /dev/null`, not `gradle run`
  with piped input.
- **No `exitProcess()`/`System.exit()` anywhere in this codebase** - fatal to
  call from something meant to be embeddable in a test suite or a server, so
  every exit path is a normal, cooperative coroutine completion instead:
  - Ctrl+C: Mosaic's own frame loop has built-in handling for it, but only
    when no `onKeyEvent` in the tree already reported the key as handled -
    and only when the *ctrl* modifier is actually checked (`KeyEvent("c",
    ctrl = true)` and a plain lowercase `c` keypress both have `key == "c"`;
    looking only at `.key` makes Ctrl+C indistinguishable from typing the
    letter C, which would swallow it as text input). `App.kt`'s `onKeyEvent`
    modifier checks `event.ctrl && event.key == "c"` and returns `false`
    (explicitly *not handled*) instead of forwarding to
    `engine.onKeyEvent(...)` - Mosaic's own root-level handler then does the
    actual cancellation (its own internal composition `job.cancel()`),
    letting `runMosaic()` return normally. This works unconditionally,
    regardless of the engine's internal lock/mode state, including
    mid-typewriter-animation and inside the cake/bosskey loop (which
    otherwise has no in-story way back to the shell). Deliberately a
    UI-layer concern, not `TerminalEngine`'s: Ctrl+C is our own escape
    hatch, not part of the original terminal's modeled behavior.
  - `LOGOUT`/`PLAY PORTAL`: these *are* faithfully-ported in-game commands
    that end the session (the original navigated away via `getURL()`), so
    `TerminalEngine.farewell()` sets `_exitRequested.value = true` on a
    `MutableStateFlow<Boolean>` exposed as `exitRequested`. Unlike Ctrl+C,
    Mosaic has no built-in hook for this, so `Main.kt` watches it itself:
    `runBlocking { val mosaicJob = launch { runMosaic { App(engine) } };
    val watcherJob = launch { engine.exitRequested.first { it };
    mosaicJob.cancel() }; mosaicJob.join(); watcherJob.cancel() }` - a
    standard, structured-concurrency cancellation, not a process kill.
    (Earlier attempt that didn't work, for reference: mirroring Mosaic's own
    "robot" sample - `if (!exitRequested) LaunchedEffect(Unit) {
    awaitCancellation() }` inside `App()` - compiles fine but the process
    just hangs forever after `LOGOUT`; verified empirically via `tmux`, not
    just reasoned about. Whatever `runMosaicComposition.awaitComplete()`
    actually waits on, it is not "no more suspended `LaunchedEffect`s in the
    tree" in the way that pattern assumes.)
- Mosaic has no built-in alternate-screen-buffer support (confirmed: no
  `1049` anywhere in its source), so `AppRunner.kt` (`runTerminalApp()`,
  shared `commonMain` code called from each target's tiny `Main.kt`) drives
  it directly - writes `ESC[?1049h` (enter) before entering `runBlocking`
  and calls `installTerminationHandler { ... ESC[?1049l ... }` to register
  the "leave" side. This is what makes the whole *terminal* (not just our
  own drawn region) clear on startup and restores whatever was there before
  on exit, like vim/htop. `installTerminationHandler`/`flushStdout` are
  `expect`/`actual` (`Platform.kt`) since the underlying mechanism is
  necessarily platform-specific: on JVM, a `Runtime.addShutdownHook`, which
  the JVM itself runs on every exit path including a normal return from
  `main()`; on `linuxX64` (Kotlin/Native, via `kotlinx.cinterop`/
  `platform.posix`), `atexit()` covers the normal-return case the same way,
  plus `signal(SIGTERM/SIGHUP) { exit(0) }` so an external signal reaches
  `exit()` (which runs the `atexit` handler) instead of terminating the
  process without unwinding. Kept as a dedicated hook (rather than only a
  `try`/`finally`) as a fallback for exits that bypass normal Kotlin control
  flow entirely - e.g. SIGTERM/SIGHUP from outside the process - since every
  in-process exit path is now cooperative and would hit a `finally` anyway.
  Verified via `tmux` on both the JVM shadow jar and the native `linuxX64`
  binary: pre-existing shell content is hidden on launch and exactly
  restored (plus the shell's own record of the launch command) after exit
  via Ctrl+C (idle, mid-animation, and from inside the cake/bosskey loop),
  `LOGOUT`, `PLAY PORTAL`, and (native binary only, to specifically exercise
  the `signal()` path) an external `kill -TERM`, with the terminal left in a
  normal, working state afterward.

## Web frontend: ui-web

`cli/ui-web/` is a `wasmJs()`-only Kotlin Multiplatform module (Kotlin/Wasm,
browser target) that depends on `:logic` and drives the DOM directly with
[`kotlinx-browser`](https://github.com/Kotlin/kotlinx-browser) - there's no
shared UI layer with `ui-terminal` because Mosaic has no Wasm target at all
(confirmed: its published Gradle module metadata only advertises `jvm` and a
handful of Kotlin/Native targets, no `wasmJs`). `TerminalEngine` itself
needed **zero** changes to support this: it was already UI-agnostic
(`liveLine`/`exitRequested` are plain `StateFlow`s, `onKeyEvent` takes a
plain key name), the exact contract `Main.kt` here binds to the DOM with
instead of Mosaic's `Text()`/`onKeyEvent`.

- `src/wasmJsMain/kotlin/.../Main.kt`: `main()` grabs `<pre id="terminal">`
  by id, launches a coroutine collecting `engine.liveLine` into its
  `textContent` (a full-string replacement each emission - simpler than
  Mosaic's diffing approach, and safe to do this way because there's no
  analog of the Mosaic-desync-on-narrow-terminal bug `wrapWidth`/
  `setViewportWidth` exists to avoid on the CLI side, see
  `TerminalEngine.setViewportWidth`'s doc), and forwards `keydown` events to
  `engine.onKeyEvent`. Ctrl/Cmd/Alt combos are deliberately *not* forwarded
  (and not `preventDefault()`-ed) so browser/OS shortcuts keep working -
  this frontend's equivalent of `App.kt` returning `false` for Ctrl+C.
  `Main.kt` calls `engine.setViewportWidth(Int.MAX_VALUE)` once at startup
  to suppress `TerminalEngine`'s own hard-wrapping *entirely*, leaving
  `#terminal`'s `white-space: pre-wrap` (styles.css) as the only layer
  deciding where lines actually break. Two things were tried first and both
  looked broken: (1) not calling `setViewportWidth` at all, i.e. leaving the
  default `WRAP_WIDTH` (100) in effect - since that's a plain hardcoded
  character count with no awareness of the actual rendered font metrics or
  viewport size, its break points don't move on resize and can land well
  short of where the text would naturally wrap, reading as random early
  breaks; (2) computing an approximate column count from
  `window.innerWidth` and a guessed px-per-character constant and passing
  *that* to `setViewportWidth` - same problem, still a hardcoded-metric
  hard-wrap independent of the browser's real layout, just with a
  differently-wrong number. Both are two engines disagreeing about where to
  break the same text; letting exactly one (the browser's, which has the
  real metrics and re-wraps correctly on every resize for free) own the
  decision is what actually fixes it, not tuning either number closer to
  the other.
- `src/wasmJsMain/resources/index.html` + `styles.css`: hand-written, not
  Kotlin-generated - a green-phosphor-on-black CRT-styled page with a
  blinking block-cursor `::after` on the terminal element (there's no
  movable cursor to track - `TerminalEngine.reveal()`/Backspace always
  read/write the *end* of `pageContent`, so a permanent end-of-text cursor
  is faithful, not a simplification).
- `:logic`'s `wasmJs()` target needs **both** `d8()` (V8's standalone CLI
  shell - what actually runs `wasmJsD8Test`, headlessly, no browser needed)
  *and* `browser()` (with its own test task disabled -
  `browser { testTask { enabled = false } }` - since it'd need a headless
  Chrome that isn't installed, and `d8()` already covers this DOM-free
  module's whole suite). `browser()` alone looks redundant, but without it
  `ui-web`'s `wasmJsBrowserDistribution` fails to configure at all:
  `":logic is not configured for JS usage"` - the webpack/npm tooling that
  bundles `ui-web` needs every wasmJs project it depends on, including a
  pure-logic library with no DOM code, to have *some* JS/Wasm sub-target
  registered, not just a Kotlin/Native-style `d8()` runner.
- `:logic`'s `kotlinx-coroutines-core` dependency had to become `api(...)`,
  not `implementation(...)`: `TerminalEngine`'s public surface
  (`StateFlow<T>`, `boot(CoroutineScope)`) exposes coroutines types
  directly, and `implementation` doesn't leak those onto a *separate Gradle
  module's* compile classpath even though `ui-terminal` never needed this
  fix itself (Mosaic's own `api`-scoped coroutines dependency was already
  covering it transitively there).
- No headless browser is available in this dev environment (no Chrome,
  Playwright, etc.), so full in-browser interactive testing hasn't been
  done here - only code review plus the verification below. If testing this
  UI further, prefer an actual browser (`:ui-web:wasmJsBrowserDevelopmentRun`
  for a live-reloading dev server) over trying to shim one.
- What *has* been verified: `:logic:wasmJsD8Test` runs the full 76-test
  suite against the actual compiled Wasm binary via V8 (not a mock/stub of
  any kind) - strong evidence `:logic` behaves identically compiled to Wasm
  as it does on JVM/`linuxX64`. Separately, `:ui-web`'s own production
  Wasm+JS output was smoke-tested end-to-end outside a real browser, by
  importing the raw (non-webpack-bundled) `ui-web.mjs` under Node with a
  hand-rolled DOM stub (`window`/`document`/`HTMLPreElement`/`KeyboardEvent`
  stand-ins) - simulated keystrokes drove a real login through to the
  `B:\>` shell prompt, and a Ctrl-modified keystroke was confirmed *not* to
  reach the engine. Two non-obvious things surfaced doing this, worth
  knowing if repeating it: Kotlin/Wasm's `as SomeExternalType` casts (and
  the JS-interop adapters wrapping callback parameters like `(Event) ->
  Unit`) do a real `instanceof` check against the browser's global
  constructor for that type at runtime - a duck-typed stub object isn't
  enough, `globalThis.HTMLPreElement`/`Event`/`KeyboardEvent` all had to be
  defined as actual stub classes and instantiated with `new`; and the
  *webpack-bundled* `ui-web.js` (as opposed to the raw compiler-output
  `.mjs`) additionally runs webpack's own Node/browser/Deno/d8
  environment-autodetection bootstrap first, which itself calls
  `document.getElementsByTagName`/`currentScript` before any application
  code runs at all - importing the raw `.mjs` sidesteps that entirely and
  is the easier thing to stub against.

## Status (2026-08-17)

- Decompilation, data extraction, and Kotlin scaffold: done (see above).
- State machine (`cli/logic/.../TerminalEngine.kt`): **implemented** —
  login/username/password (incl. `CJOHNSON`/`TIER3` admin unlock and masked
  password entry), GLaDOS shell commands, the 50-question application wizard
  (incl. the original's off-by-one where question 50's answer is discarded),
  NOTES.EXE paging, and the `THECAKEISALIE` ⇄ bosskey toggle loop.
  `cli/ui-terminal/.../App.kt` wires it to Mosaic; `Main.kt` is the entry
  point.
- Split into two Gradle modules (`cli/logic`, no UI dependency at all — not
  even transitively, verified via `./gradlew :logic:dependencies` — and
  `cli/ui-terminal`, the Mosaic UI, depends on `:logic`). The only change
  this forced in `TerminalEngine` itself: `liveLine` went from a Compose
  `mutableStateOf`-delegated property to a plain `StateFlow<String>`
  (`MutableStateFlow` internally); `isLocked` had no external reader at all
  so it's now a fully private plain `var`. See "UI toolkit: Mosaic" above.
- Verified interactively via `tmux` end-to-end: full login, admin login,
  DIR/HELP/IP/unknown-command shell errors, APPLY through several questions
  (text + choice types, invalid-choice rejection), QUIT-from-questionnaire,
  THECAKEISALIE + bosskey toggle (both directions), NOTES.EXE all 4 pages
  with correct return-to-shell, wrong-password retry, LOGOUT process exit.
  Not manually walked: the 2313-entry Q21 PgUp/PgDn pagination specifically
  (code-reviewed, shares the tested choice-rendering path) and every one of
  the 50 questions individually.
- Matches the original's clear-and-redraw model: every page transition wipes
  the screen and redraws from scratch, and — like the original — whatever
  you just typed is never echoed anywhere once submitted, it simply
  disappears along with the rest of the old page.
- Known deviations from the original (intentional, not bugs): `gdxt.php`
  calls are no-ops and `uid` is a locally-synthesized random string;
  `LOGOUT`/`PLAY PORTAL` (`farewell()`) end the session instead of
  navigating a browser to steampowered.com/the trailer, shown as an
  in-universe terminal error (`[ERROR: STORE NOT FOUND]` /
  `[ERROR: TRAILER NOT FOUND]`) rather than a bracketed dev note explaining
  what would have happened - an earlier version's "this would open <url> in
  your browser" had exactly the same leaked-implementation-note problem as
  `CAKE_MONOLOGUE_1`'s security02.flv placeholder below, fixed the same way;
  the cake video isn't reproduced - in its place, `CAKE_MONOLOGUE_1` shows
  `[ERROR: SECURITY02.FLV NOT FOUND]`, an in-universe terminal error rather
  than a bracketed dev note like an earlier version's `[security02.flv
  would play here]` (which wasn't in the original's own text at all -
  `qar[11]`/`glob_ns.play("security02.flv")` in `DoAction.as` play the video
  as a real overlay with no such line in the terminal text itself);
  cosmetic-only effects (glitching UID digits, random cake-image
  flicker) are skipped.
- `cli/logic` has an automated unit test suite (`kotlin.test` +
  `kotlinx-coroutines-test`, `./gradlew :logic:allTests` - runs on both the
  `jvm` and `linuxX64` targets since the tests live in `src/commonTest/`):
  76 tests across `LoginFlowTest`, `ShellCommandsTest`, `ApplicationWizardTest`,
  `NotesExeTest`, `CakeBosskeyTest`, `InputHandlingTest`. Drives
  `TerminalEngine` through `TestScope`/`runTest`'s virtual time, so the
  whole suite runs in well under a second despite exercising every typewriter
  animation (including the 2313-choice Q21 pagination and the full 50-question
  form) - see `TestHelpers.kt`. `ui-terminal` still has none (harder to unit
  test - Mosaic needs a real TTY); UI-level behavior is still verified
  manually via `tmux` as described above.
  - Two quirks the test suite caught/confirmed: `qar[8]`'s crisis message
    word-wraps "Crisis Response" and "Team" onto separate lines at
    `WRAP_WIDTH`, so tests assert on `"mobilized"` instead of the
    straddling phrase. More interestingly, `APPLY.EXE`/`NOTES.EXE` (both
    referenced in shell code as valid command aliases) are **unreachable
    in practice**: `.` isn't in the accepted-character set, faithfully
    matching the original's keyCode allowlist which also has no period key
    - so typing either one actually submits `APPLYEXE`/`NOTESEXE`, which
    don't match anything and fall through to the unknown-command error.
  - Porting the test suite to also run on `linuxX64` surfaced a Kotlin/Native
    quirk: backtick-named `@Test` functions containing a comma fail to
    compile there ("Name contains illegal characters") even though the same
    name compiles and runs fine on the JVM - Kotlin/Native mangles test
    names into binary symbols, which have a narrower allowed character set
    than a JVM method name does. Existing test names were reworded to use
    ` - ` instead of `,`; keep that in mind for new backtick-named tests.
