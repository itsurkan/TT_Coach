---
name: viewer-ui-test
description: Use when smoke-testing the poses_viewer #/strokes drill-simulator UI in a real browser — confirming a dataset loads, the per-rep results table populates, spoken-feedback banner/log fire on cadence, and "Лише текст" silences audio. Drives headed Chrome via CDP (no Playwright/Puppeteer). Use when the user says "smoke test the viewer", "UI test the strokes page", "check it in Chrome", or after changing StrokesPage / useSpokenFeedback / analyzeDrill.
---

# viewer-ui-test

## Overview

The `#/strokes` page (M1 drill simulator) has runtime behavior that unit tests can't see:
React state wiring, `speechSynthesis` audio, video-`timeupdate`-driven feedback cadence, and
the audio/text mode toggle. This skill drives a **real headed Chrome** via the Chrome DevTools
Protocol — no Playwright or Puppeteer needed, just Node's global `WebSocket` (Node 21+).

`drive_viewer.mjs` (next to this file) does the whole flow and exits non-zero on failure.

## When to use

- Verifying a change to [StrokesPage.tsx](../../../poses_viewer/src/components/StrokesPage.tsx),
  [useSpokenFeedback.ts](../../../poses_viewer/src/components/useSpokenFeedback.ts),
  [DrillResultsTable.tsx](../../../poses_viewer/src/components/DrillResultsTable.tsx), or
  [analyzeDrill.ts](../../../poses_viewer/src/drill2d/analyzeDrill.ts).
- The user asks for a browser smoke test (can't run headless — needs real `speechSynthesis` + `<video>`).
- **Not** for `drill2d/` detection math — that has golden vitest coverage; run `npx vitest run` instead.

## Steps

1. **Start the dev server** and capture the port (Vite picks 5781+ if 5780 is busy):
   ```bash
   cd poses_viewer && npm run dev > /tmp/viewer_dev.log 2>&1 &
   sleep 4 && grep -o 'localhost:[0-9]*' /tmp/viewer_dev.log | head -1
   ```

2. **Launch headed Chrome with CDP** (fresh profile so it never reuses your real one):
   ```bash
   rm -rf /tmp/chrome-cdp-profile
   "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" \
     --remote-debugging-port=9222 --user-data-dir=/tmp/chrome-cdp-profile \
     --no-first-run --no-default-browser-check "http://localhost:<PORT>/#/strokes" \
     > /tmp/chrome.log 2>&1 &
   sleep 4 && curl -s http://127.0.0.1:9222/json/version | head -c 80   # confirm CDP up
   ```

3. **Run the driver** (`<PORT>` from step 1; dataset defaults to `andrii_1`):
   ```bash
   node .claude/skills/viewer-ui-test/drive_viewer.mjs <PORT> [videoBase] [cdpPort]
   ```
   It prints raw/forward/rep counts, table row count, the per-fire banner + log timestamps with
   cadence gaps, and the text-mode spoken count. Screenshots → `tmp/screenshots/0{1,2,3}-*.png`
   (read `02-audio.png` to eyeball the render). **Read those screenshots** — counts passing ≠ rendering right.

## Pass criteria

| Check | andrii_1 | video_4 | How |
|---|---|---|---|
| raw / forward / rep counts | 23 / 15 / 15 | 18 / 12 / 9 | M0 golden, must match exactly |
| results-table rows | 15 | 9 | = rep count |
| audio mode fires `speak()` | > 0 | > 0 | banner + log grow, `__spoken` increments |
| feedback cadence | gaps ~3–5 s | gaps ~3–5 s | log-item leading timestamps |
| "Лише текст" silences audio | 0 spoken | 0 spoken | `__spoken` stays 0 while log still grows |

## How it works (CDP essentials)

- `GET http://127.0.0.1:9222/json` → page targets; attach to `webSocketDebuggerUrl`.
- `Runtime.evaluate` runs JS in the page; `Page.captureScreenshot` grabs PNGs.
- **React-controlled `<select>`:** setting `.value` won't trigger `onChange`. Use the native
  prototype setter then dispatch a bubbling `change` event (see `SET_SELECT` in the script).
- **Audio is counted, not emitted:** the script overrides `speechSynthesis.speak` to push to
  `window.__spoken` — observable, and headed Chrome won't block it behind a user-gesture.
- Playback is sped up (`video.playbackRate = 8`, `muted`) so an ~18 s clip finishes in seconds.

## Common mistakes

- **`Page.navigate` to the same `#/strokes` URL is a hash change — no document reload**, so React
  state (e.g. `feedbackMode`) persists from a prior run. The driver explicitly forces audio mode
  before the audio run; don't assume the default. To fully reset, navigate to `about:blank` first.
- **Counts wrong but page looks fine** → wrong dataset loaded, or yaw ≠ 0 changed detection. The
  driver leaves yaw at its default 0.
- **CDP connect fails** → Chrome not launched with `--remote-debugging-port`, or you reused a
  running Chrome that ignored the flag. Kill it and relaunch with the fresh `--user-data-dir`.
- **Cadence gaps show negatives / duplicates** → the log accumulates across replays because the
  driver seeks via raw `currentTime` (not the UI seek that calls `reset`). Judge cadence from the
  first clean pass, not the concatenated list.
