---
name: Screenshot save location
description: Save adb screenshots to tmp/screenshots/ in the TT_Coach project root so user can view them in VSCode file explorer
type: feedback
originSessionId: 001ad2a2-dff3-43f4-bd97-ff44be75ba01
---
Save `adb exec-out screencap -p` output to `tmp/screenshots/<descriptive-name>.png` under the TT_Coach project root, not `/tmp/`.

**Why:** user wants to see screenshots directly in the VSCode file explorer while we iterate. `/tmp/` paths aren't visible there and rotate silently.

**How to apply:** for any adb screenshot, redirect to `tmp/screenshots/<name>.png`. The directory is already gitignored via `.gitignore` → `/tmp/screenshots/`.
