#!/usr/bin/env bash
#
# run_on_phone.sh — build the debug APK, install it on the connected Android
# device, and launch the app. The whole "reinstall and run" flow in one step.
#
# Usage:
#   scripts/run_on_phone.sh            # build + install + launch
#   scripts/run_on_phone.sh -s SERIAL  # target a specific device (multi-device)
#
set -euo pipefail

APP_ID="com.ttcoachai"
LAUNCHER="${APP_ID}/.MainActivity"

# Resolve project root (this script lives in <root>/scripts/).
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# Locate adb (not always on PATH).
ADB="$(command -v adb || true)"
if [[ -z "$ADB" ]]; then
  ADB="$HOME/Library/Android/sdk/platform-tools/adb"
fi
if [[ ! -x "$ADB" ]]; then
  echo "error: adb not found (looked on PATH and $ADB)" >&2
  exit 1
fi

# Optional explicit device serial (-s <serial>).
SERIAL=""
if [[ "${1:-}" == "-s" && -n "${2:-}" ]]; then
  SERIAL="$2"
fi

# Verify a usable device is attached.
DEVICES="$("$ADB" devices | awk 'NR>1 && $2=="device" {print $1}')"
if [[ -z "$DEVICES" ]]; then
  echo "error: no device in 'device' state. Check 'adb devices' (offline/unauthorized?)." >&2
  exit 1
fi
DEVICE_COUNT="$(echo "$DEVICES" | grep -c .)"
if [[ -z "$SERIAL" && "$DEVICE_COUNT" -gt 1 ]]; then
  echo "error: multiple devices attached; pass -s <serial>:" >&2
  echo "$DEVICES" >&2
  exit 1
fi

ADB_TARGET=()
[[ -n "$SERIAL" ]] && ADB_TARGET=(-s "$SERIAL")

# Wait until the (optionally chosen) device is back in 'device' state, retrying
# through the transient drops that wireless adb-tls suffers. Returns non-zero if
# it never settles within the timeout.
wait_for_device() {
  local tries=0 max=30
  while (( tries < max )); do
    if "$ADB" ${ADB_TARGET[@]+"${ADB_TARGET[@]}"} get-state 2>/dev/null | grep -q '^device$'; then
      return 0
    fi
    sleep 1
    (( tries++ ))
  done
  return 1
}

# gradle's :app:installDebug pushes via ddmlib, which chokes with "Broken pipe"
# when the wireless link hiccups mid-transfer. Build once, then install with
# `adb install` (more robust) and retry, restarting the adb server between tries.
echo "==> Building debug APK (./gradlew :app:assembleDebug)"
if [[ -n "$SERIAL" ]]; then
  ANDROID_SERIAL="$SERIAL" ./gradlew :app:assembleDebug
else
  ./gradlew :app:assembleDebug
fi

# Resolve the freshly built APK (ABI split → app-arm64-v8a-debug.apk).
APK="$(ls -t app/build/outputs/apk/debug/*-debug.apk 2>/dev/null | head -n1 || true)"
if [[ -z "$APK" ]]; then
  echo "error: no debug APK found under app/build/outputs/apk/debug/" >&2
  exit 1
fi

echo "==> Installing $APK"
install_ok=""
for attempt in 1 2 3; do
  if ! wait_for_device; then
    echo "error: device never returned to 'device' state (check 'adb devices')." >&2
    exit 1
  fi
  if "$ADB" ${ADB_TARGET[@]+"${ADB_TARGET[@]}"} install -r "$APK"; then
    install_ok="yes"
    break
  fi
  echo "==> install attempt $attempt failed; restarting adb server and retrying…" >&2
  "$ADB" kill-server >/dev/null 2>&1 || true
  "$ADB" start-server >/dev/null 2>&1 || true
  sleep 2
done
if [[ -z "$install_ok" ]]; then
  echo "error: install failed after 3 attempts (wireless adb unstable?)." >&2
  echo "  Try re-pairing: adb connect <ip>:<port>, or use USB." >&2
  exit 1
fi

echo "==> Launching $LAUNCHER"
"$ADB" ${ADB_TARGET[@]+"${ADB_TARGET[@]}"} shell am start -n "$LAUNCHER"

echo "==> Done."
