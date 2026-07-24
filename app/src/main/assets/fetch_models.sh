#!/usr/bin/env bash
# Fetch the ONNX models the Android RTMPose backend bundles.
# Mirrors iosApp/TTCoach/Models/fetch_models.sh.
#
# Fetches BOTH the LITE preset (production default, `RtmposeBackend.ACTIVE_PRESET`) and the
# BALANCED preset, not just the active one. Rationale: swapping `ACTIVE_PRESET` is meant to be a
# one-line code change (see RtmposeBackend.kt); if this script only fetched the active preset,
# swapping later would ALSO require re-running fetch_models.sh, and a stale asset dir could be
# missing the just-swapped-away-from preset entirely. Fetching both keeps every preset runnable
# out of the box regardless of which one is currently active.
#
# Resolution order (per model):
#   1. $1 (explicit source dir), if given
#   2. iosApp/TTCoach/Models/ in the main working tree (found via `git rev-parse
#      --git-common-dir`, since this script may run from inside a worktree whose
#      own directory does NOT contain iosApp/)
#   3. ~/.cache/rtmlib/hub/checkpoints/ (rtmlib's own download cache)
#   4. download + unzip from the documented openmmlab URLs
#
# Verifies SHA-256 against MODELS.md. Idempotent. Fails loudly on mismatch.
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Resolve the main working tree root even when this script runs inside a git worktree.
GIT_COMMON_DIR="$(git -C "$DIR" rev-parse --git-common-dir 2>/dev/null || true)"
if [[ -n "$GIT_COMMON_DIR" ]]; then
  MAIN_TREE="$(cd "$(dirname "$GIT_COMMON_DIR")" && pwd)"
else
  MAIN_TREE=""
fi

IOS_MODELS_DIR="${1:-${MAIN_TREE:+$MAIN_TREE/iosApp/TTCoach/Models}}"
CACHE="$HOME/.cache/rtmlib/hub/checkpoints"

declare -a NAMES=(
  "yolox_tiny_8xb8-300e_humanart-6f3252f9.onnx"
  "rtmpose-s_simcc-body7_pt-body7_420e-256x192-acd4a1ef_20230504.onnx"
  "yolox_m_8xb8-300e_humanart-c2c7a14a.onnx"
  "rtmpose-m_simcc-body7_pt-body7_420e-256x192-e48f03d0_20230504.onnx"
)
declare -a SHAS=(
  "ceb11c07298f95c50d7c5abeb906d03340c85f23aa79e3e66966e7fb6c307250"
  "9aeb635b83f86aea45cf45d85798f7eba1a162de8e0d721c44e54fe5eebaf47d"
  "3dea6513388889f0fff4b77bf7a26013600321b9eb9ceb0e9a400a82572f5f23"
  "5c0a4bf67953e6d2ac43ce15e77dc9d5d354ae18430a47d2c5963a7bc5683e3c"
)
declare -a URLS=(
  "https://download.openmmlab.com/mmpose/v1/projects/rtmposev1/onnx_sdk/yolox_tiny_8xb8-300e_humanart-6f3252f9.zip"
  "https://download.openmmlab.com/mmpose/v1/projects/rtmposev1/onnx_sdk/rtmpose-s_simcc-body7_pt-body7_420e-256x192-acd4a1ef_20230504.zip"
  "https://download.openmmlab.com/mmpose/v1/projects/rtmposev1/onnx_sdk/yolox_m_8xb8-300e_humanart-c2c7a14a.zip"
  "https://download.openmmlab.com/mmpose/v1/projects/rtmposev1/onnx_sdk/rtmpose-m_simcc-body7_pt-body7_420e-256x192-e48f03d0_20230504.zip"
)

verify() { echo "$2  $1" | shasum -a 256 -c - >/dev/null 2>&1; }

for i in "${!NAMES[@]}"; do
  name="${NAMES[$i]}"; sha="${SHAS[$i]}"; url="${URLS[$i]}"
  dest="$DIR/$name"
  if [[ -f "$dest" ]] && verify "$dest" "$sha"; then
    echo "ok: $name"; continue
  fi
  if [[ -n "$IOS_MODELS_DIR" && -f "$IOS_MODELS_DIR/$name" ]] && verify "$IOS_MODELS_DIR/$name" "$sha"; then
    echo "copy from iOS models dir: $name"
    cp "$IOS_MODELS_DIR/$name" "$dest"
  elif [[ -f "$CACHE/$name" ]] && verify "$CACHE/$name" "$sha"; then
    echo "copy from rtmlib cache: $name"
    cp "$CACHE/$name" "$dest"
  else
    echo "download: $name"
    tmp="$(mktemp -d)"
    curl -fSL "$url" -o "$tmp/m.zip"
    unzip -o "$tmp/m.zip" -d "$tmp" >/dev/null
    found="$(find "$tmp" -name "$name" | head -1)"
    [[ -n "$found" ]] || { echo "ERROR: $name not found in $url" >&2; exit 1; }
    cp "$found" "$dest"
    rm -rf "$tmp"
  fi
  verify "$dest" "$sha" || { echo "ERROR: SHA mismatch for $name" >&2; exit 1; }
  echo "ok: $name"
done
echo "All models present and verified in $DIR"
