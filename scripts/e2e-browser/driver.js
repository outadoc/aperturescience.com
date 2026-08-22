// Minimal headless-Chromium driver for testing ui-web end-to-end - see AGENTS.md ("End-to-end
// browser testing" under "Web frontend: ui-web") for how to set up the container this runs in.
//
// Drives `puppeteer-core` against the system `chromium` package (not a bundled download - there
// isn't one available in the sandboxed dev environment this was written for) via a JSON list of
// steps, so a whole scenario can be described declaratively instead of writing a one-off script
// per test.
//
// Usage: node driver.js <script.json>
// where script.json is an array of steps, e.g.:
//   [
//     {"nav": "http://localhost:8080"},
//     {"waitFor": "#terminal"},
//     {"screenshot": "/tmp/out1.png"},
//     {"type": "LOGON\n"},
//     {"sleep": 1500},
//     {"screenshot": "/tmp/out2.png"},
//     {"consoleErrors": true}
//   ]
//
// Supported steps:
//   nav <url>            - navigate to url
//   waitFor <selector>    - wait for a selector to appear
//   sleep <ms>            - wait a fixed duration - see AGENTS.md's note on why this needs to be
//                           generous (the app silently drops keystrokes sent while its own
//                           reveal-animation lock is up)
//   type <text>           - dispatch each character as a real keydown (matches this app's
//                           listener, which reacts to KeyboardEvent.key) - "\n" sends Enter
//   press <key>           - send a single named key (e.g. "Enter", "ArrowLeft")
//   screenshot <path>     - save a full-page PNG screenshot
//   consoleErrors true    - print any console.error/pageerror seen so far

import fs from "node:fs";
import puppeteer from "puppeteer-core";

const CHROMIUM_PATH = "/usr/bin/chromium";

async function main() {
  const scriptPath = process.argv[2];
  if (!scriptPath) {
    console.error("usage: node driver.js <script.json>");
    process.exit(1);
  }
  const steps = JSON.parse(fs.readFileSync(scriptPath, "utf8"));

  const browser = await puppeteer.launch({
    executablePath: CHROMIUM_PATH,
    headless: true,
    args: ["--no-sandbox", "--disable-setuid-sandbox", "--autoplay-policy=no-user-gesture-required"],
  });
  const page = await browser.newPage();
  await page.setViewport({ width: 1280, height: 900 });

  const consoleMessages = [];
  page.on("console", (msg) => consoleMessages.push(`[${msg.type()}] ${msg.text()}`));
  page.on("pageerror", (err) => consoleMessages.push(`[pageerror] ${err.message}`));

  for (const step of steps) {
    if (step.nav) {
      await page.goto(step.nav, { waitUntil: "networkidle0" });
    } else if (step.waitFor) {
      await page.waitForSelector(step.waitFor, { timeout: 10000 });
    } else if (step.sleep) {
      await new Promise((r) => setTimeout(r, step.sleep));
    } else if (step.type) {
      for (const ch of step.type) {
        const key = ch === "\n" ? "Enter" : ch;
        await page.keyboard.press(key);
        await new Promise((r) => setTimeout(r, 15));
      }
    } else if (step.press) {
      await page.keyboard.press(step.press);
    } else if (step.screenshot) {
      await page.screenshot({ path: step.screenshot, fullPage: true });
      console.log(`screenshot -> ${step.screenshot}`);
    } else if (step.consoleErrors) {
      const errors = consoleMessages.filter((m) => m.startsWith("[error]") || m.startsWith("[pageerror]"));
      console.log("console errors:", errors.length ? errors.join("\n") : "(none)");
    }
  }

  await browser.close();
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
