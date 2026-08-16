# Aperture Science Terminal (Kotlin port)

A Kotlin/JVM reimplementation of `ApertureScience17 (2007-10-17).swf`, the
fake DOS-style terminal from the 2007 *Portal* ARG viral-marketing site
(aperturescience.com). It reproduces the login flow, the "GLaDOS" shell,
the 50-question fake job application, the `CJOHNSON` admin account and its
`NOTES.EXE` corporate-history reader, and the `THECAKEISALIE` easter egg —
including its quirks — in a real terminal, no Flash required.

See [`../AGENTS.md`](../AGENTS.md) for how this was decompiled, extracted,
and ported, and for the list of deliberate deviations from the original.

## Requirements

- JDK 21+
- A real interactive terminal (this uses [Mosaic](https://github.com/JakeWharton/mosaic),
  a Compose-for-terminal UI library, which needs a real TTY — it won't run
  under a plain pipe or redirected output)

## Build & run

```sh
./gradlew run
```

or build a standalone fat jar:

```sh
./gradlew shadowJar
java -jar build/libs/aperturescience-terminal-0.1.0-all.jar
```

The program takes over the whole terminal on launch, like `vim` or `htop`
(using the terminal's alternate-screen buffer) — whatever was on screen
before is hidden while it runs and comes back exactly as it was when it
exits.

Press **Ctrl+C** at any time to exit — including from the `THECAKEISALIE`
easter egg loop, which otherwise has no scripted way back to the shell (this
matches the original: your only real exit there was closing the browser tab).

## Input rules

- Accepted characters: `0-9`, `A-Z` (letters are upper-cased as you type,
  matching the original), space, and `?`. Everything else is ignored.
- <kbd>Backspace</kbd> deletes the last character; <kbd>Enter</kbd> submits
  the current line. Max input length is 65 characters.
- Commands are matched **case-insensitively as typed** but compared
  **exactly** — no abbreviations, no partial matches.
- <kbd>PageUp</kbd> / <kbd>PageDown</kbd> only do anything on a job-
  application question with more than 104 choices (only question 21 — see
  below).

## Command / state tree

Every screen the terminal can show, and every input that moves you between
them. Error/rejection text is quoted verbatim; unlabeled arrows mean "any
other input is silently ignored, prompt stays as-is".

```
Boot
└─ "> "                                              (initial prompt)
   ├─ HELP | ?  ──────────────────────────▶  "crisis response team" joke message ──▶ back to "> "
   ├─ LOGON | LOGIN | USER
   │  └─ "Username> "
   │     └─ <any text, length > 2>                    (≤ 2 chars: rejected, reprompt)
   │        └─ "Password> "                           (input echoed as ***)
   │           ├─ if username was "CJOHNSON":
   │           │  ├─ "TIER3"        ──▶  SHELL (admin: "GLaDOS v1.07a", "ADMIN>" prompt)
   │           │  └─ anything else  ──▶  "ERROR 07 [Incorrect Password]" ──▶ retry "Password> "
   │           └─ else:
   │              ├─ "PORTAL" | "PORTALS"  ──▶  SHELL (regular: "GLaDOS v1.07", "B:\>" prompt)
   │              └─ anything else          ──▶  "ERROR 07 [Incorrect Password]" ──▶ retry
   └─ anything else  ──▶  (ignored)

SHELL  (GLaDOS v1.07[a] (c) 1982 Aperture Science, Inc.)
├─ DIR | CATALOG | DIRECTORY | LIST | LS | CAT
│    ──▶ fake directory listing: "APPLY.EXE" (+ "NOTES.EXE" if admin)
├─ IP                    ──▶ "uid:<synthesized session id>"
├─ HELP | LIB | ?        ──▶ command list (adds "NOTES" if admin)
├─ APPEND | ATTRIB | COPY | FORMAT | ERASE | RENAME
│    ──▶ "ERROR 15 [Disk is write protected]"
├─ PLAY
│  ├─ (no argument)      ──▶ "ERROR 03 [What would you like to play?]"
│  ├─ PLAY PORTAL        ──▶ prints a short message, then exits the program
│  │                          (the original opened a YouTube trailer; no browser is opened here)
│  └─ PLAY <anything else> ──▶ (no output)
├─ INTERROGATE
│  ├─ (no argument)      ──▶ "ERROR 02 [Command requires at least one parameter]"
│  ├─ (as admin)         ──▶ "ERROR 07 [Unknown Employee]"
│  └─ (as regular user)  ──▶ "ERROR 01 [Illegal attempt to initiate disciplinary action]"
├─ TAPEDISK              ──▶ "ERROR 18 [User not authorized to transfer system tapes]"
├─ NOTES | NOTES.EXE
│  ├─ (as admin, i.e. logged in as CJOHNSON)  ──▶ NOTES.EXE  (see below)
│  └─ (as regular user)  ──▶ "ERROR 24 [File 'NOTES' not found]"
├─ APPLY | APPLY.EXE     ──▶ APPLICATION  (see below)
├─ THECAKEISALIE         ──▶ CAKE / BOSSKEY loop  (see below)
├─ LOGOUT | BYE | LOGOFF | VALVE  ──▶ prints a short message, then exits the program
└─ anything else         ──▶ "ERROR 24 [File '<word>' not found]"

APPLICATION  (job-application wizard, entered via APPLY)
└─ intro screen: "Loaded: ENRICHMENT CENTER TEST SUBJECT APPLICATION PROCESS..."
   ├─ QUIT       ──▶ back to SHELL
   └─ CONTINUE
      └─ UID display screen: "...your form ... Unique Indentity Number ... [<uid>]..."
         ├─ QUIT       ──▶ back to SHELL
         └─ CONTINUE
            └─ Question 1 ──▶ Question 2 ──▶ ... ──▶ Question 50   (50 total, see below)
               └─ after Question 50 is reached, the next Enter ends the form immediately
                  (its answer is never actually validated or submitted — this is a quirk
                  in the original, faithfully reproduced) and shows:
                  "Congratulations! ... Please enter your 64 digit UIN(+L) to complete
                  the process."
                  ├─ "THECAKEISALIE"    ──▶ CAKE / BOSSKEY loop
                  └─ anything else      ──▶ dead end: "The entered UIN(+L) does not
                                              match..." — no further input does anything;
                                              only Ctrl+C escapes this screen

  Each question (types come from the original form):
  ├─ type TEXT (free text)      ──▶ any input accepted, advances to the next question
  ├─ type CHECKBOX / RADIO      ──▶ a number from 1 to <choice count> advances;
  │                                  anything else is silently rejected (question redisplays)
  ├─ QUIT  (works on any question)  ──▶ back to SHELL, form abandoned
  └─ PageUp / PageDown  ──▶ only active on Question 21 ("...what wild animal would you
                             like to domesticate?", 2313 choices) — pages through 104
                             choices at a time

NOTES.EXE  (admin-only, entered via NOTES from the shell)
└─ page 1 of 4 (Aperture Science corporate history, 1953–1996)
   └─ any keypress ──▶ page 2 ──▶ page 3 ──▶ page 4 ("...In many ways, the
      initial test goes well... [END]")
      └─ any keypress ──▶ back to SHELL

CAKE / BOSSKEY  (entered via THECAKEISALIE from the shell, or as the UIN(+L) answer)
└─ cake monologue + security-feed placeholder ("...When was the last time
   you left the building?..." / "...If a supervisor walks by, press return!")
   └─ any keypress ──▶ BOSSKEY: a disguised fake spreadsheet screen
      └─ any keypress ──▶ back to the cake monologue
         └─ ... (toggles forever — there is no scripted way out of this loop;
                 press Ctrl+C to exit)
```

**Global, from any state:** <kbd>Ctrl+C</kbd> exits the program immediately.
