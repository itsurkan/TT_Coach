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

# Remember the last wireless endpoint so a dropped phone can be reconnected
# without hand-typing its address. The wireless adb-tls port rotates on reboot,
# so mDNS discovery is the primary path; the cached ip:port is only a fallback.
DEVICE_CACHE="$ROOT/.last_wireless_device"

# Ensure at least one device is reachable. If none is in 'device' state, try to
# reconnect: first via mDNS discovery (survives port rotation), then via the
# cached ip:port. No-op when a device is already attached (e.g. USB).
ensure_connected() {
  if "$ADB" devices | awk -F'\t' 'NR>1 && $2=="device"' | grep -q .; then
    return 0
  fi

  # mDNS: pick the first advertised adb-tls endpoint (host:port on the last field).
  local mdns_ep
  mdns_ep="$("$ADB" mdns services 2>/dev/null \
    | awk '/_adb-tls-connect\._tcp/ {print $NF}' | grep -E '^[0-9.]+:[0-9]+$' | head -n1 || true)"
  if [[ -n "$mdns_ep" ]]; then
    echo "==> reconnecting via mDNS: $mdns_ep"
    if "$ADB" connect "$mdns_ep" >/dev/null 2>&1; then
      printf '%s\n' "$mdns_ep" > "$DEVICE_CACHE"
    fi
  fi

  # Still nothing? Fall back to the last remembered endpoint.
  if ! "$ADB" devices | awk -F'\t' 'NR>1 && $2=="device"' | grep -q . \
      && [[ -f "$DEVICE_CACHE" ]]; then
    local cached
    cached="$(head -n1 "$DEVICE_CACHE" 2>/dev/null || true)"
    if [[ -n "$cached" ]]; then
      echo "==> reconnecting via cached endpoint: $cached"
      "$ADB" connect "$cached" >/dev/null 2>&1 || true
    fi
  fi
}
ensure_connected

# Drop stale wireless duplicates first. When the same phone re-advertises over
# mDNS without the old transport dying, adb keeps both and disambiguates the new
# one by appending " (N)" to the instance name — so any serial carrying a
# " (N)" suffix is by definition a duplicate of a still-listed sibling. Detach
# them so a single live device remains and no -s is needed. `adb disconnect`
# accepts the full mDNS instance name.
DUP_SERIALS="$("$ADB" devices | awk -F'\t' 'NR>1 && $1 ~ / \([0-9]+\)\./ {print $1}')"
if [[ -n "$DUP_SERIALS" ]]; then
  while IFS= read -r dup; do
    [[ -z "$dup" ]] && continue
    echo "==> detaching duplicate wireless entry: $dup"
    "$ADB" disconnect "$dup" >/dev/null 2>&1 || true
  done <<< "$DUP_SERIALS"
fi

# Collapse same-phone dual transports. adb auto-connects a wireless phone via
# BOTH its ip:port and its mDNS instance name (adb-XXXX._adb-tls-connect._tcp),
# so one physical device shows up twice and trips the multi-device guard below.
# When an ip:port transport exists, the mDNS-name transports are its auto-
# connected duplicates — drop those, keeping the ip:port form (what we cache and
# reconnect through). Only fires when an ip:port sibling is present, so a phone
# reachable solely by its mDNS name is never disconnected. (bash 3.2-safe: no
# associative arrays — macOS default bash.)
COLLAPSE_SERIALS="$("$ADB" devices | awk -F'\t' 'NR>1 && $2=="device" {print $1}')"
if echo "$COLLAPSE_SERIALS" | grep -qE '^[0-9.]+:[0-9]+$'; then
  while IFS= read -r s; do
    [[ -z "$s" ]] && continue
    if [[ "$s" == *._adb-tls-connect._tcp ]]; then
      echo "==> collapsing duplicate transport: dropping $s"
      "$ADB" disconnect "$s" >/dev/null 2>&1 || true
    fi
  done <<< "$COLLAPSE_SERIALS"
fi

# Verify a usable device is attached. `adb devices` tab-separates serial and
# state; wireless adb-tls serials can contain spaces (the mDNS name), so split on
# TAB — whitespace splitting mis-parses those serials and fails the check.
DEVICES="$("$ADB" devices | awk -F'\t' 'NR>1 && $2=="device" {print $1}')"
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

# Auto-pin to the sole device so every adb call targets it explicitly. Wireless
# mDNS can re-advertise a stale duplicate mid-run (e.g. right after the install
# loop's kill-server), which would otherwise make a later `adb install`/`am start`
# fail with "more than one device". Pinning -s makes those calls immune.
if [[ -z "$SERIAL" ]]; then
  SERIAL="$DEVICES"
fi

# Remember a wireless (ip:port) target for next time's reconnect fallback.
if [[ "$SERIAL" =~ ^[0-9.]+:[0-9]+$ ]]; then
  printf '%s\n' "$SERIAL" > "$DEVICE_CACHE"
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
