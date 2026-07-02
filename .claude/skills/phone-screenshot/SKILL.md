---
name: phone-screenshot
description: Use when you need to capture a screenshot from the connected Android phone/device — "take a screen from my phone", "screenshot the app", "grab the current screen", "adb screencap", or to see live app UI while iterating on Android changes.
---

# Capture a screenshot from the phone

Grabs the current screen off the connected Android device via `adb` and saves it
into the project so it's visible in the VSCode file explorer while iterating.

## Command

```bash
adb exec-out screencap -p > tmp/screenshots/<descriptive-name>.png
```

Then `Read` the PNG to view it. Use a descriptive name (e.g. `profile-fragment-gold.png`),
not a generic one — screenshots accumulate during a session.

## Rules

- **Always save under `tmp/screenshots/` in the project root** — never `/tmp/` or the
  scratchpad. That folder is gitignored (`.gitignore` → `/tmp/screenshots/`) and shows
  up in VSCode so the user can see it too.
- `mkdir -p tmp/screenshots` first if the folder doesn't exist.
- Use `exec-out` (binary-safe), not `shell screencap` piped through stdout — the latter
  can corrupt the PNG with CRLF translation.

## If it fails

- `adb devices` — confirm a device shows `device` (not `unauthorized`/`offline`).
  If empty, the phone isn't connected (USB or `adb connect <ip>` for wireless).
- `unauthorized` → accept the "Allow USB debugging" prompt on the phone.
- Multiple devices → add `-s <serial>` (serial from `adb devices`), e.g.
  `adb -s adb-RFCWB0AZP2D-1oe5GR exec-out screencap -p > tmp/screenshots/screen.png`.
- `adb` not on PATH → it's at `~/Library/Android/sdk/platform-tools/adb`.
