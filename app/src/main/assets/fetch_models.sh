#!/usr/bin/env bash
# Fetch the ONNX models the Android RTMPose backend bundles.
# Mirrors iosApp/TTCoach/Models/fetch_models.sh.
#
# Fetches ONLY the LITE preset (production default, `RtmposeBackend.ACTIVE_PRESET`) to keep the
# app lean — the BALANCED preset (yolox_m + rtmpose-m, ~150MB) is NOT auto-fetched. If you swap
# `ACTIVE_PRESET` to `Preset.BALANCED` in RtmposeBackend.kt, fetch its two models manually (see
# MODELS.md for their URLs/SHAs) before building.
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
)
declare -a SHAS=(
  "ceb11c07298f95c50d7c5abeb906d03340c85f23aa79e3e66966e7fb6c307250"
  "9aeb635b83f86aea45cf45d85798f7eba1a162de8e0d721c44e54fe5eebaf47d"
)
declare -a URLS=(
  "https://download.openmmlab.com/mmpose/v1/projects/rtmposev1/onnx_sdk/yolox_tiny_8xb8-300e_humanart-6f3252f9.zip"
  "https://download.openmmlab.com/mmpose/v1/projects/rtmposev1/onnx_sdk/rtmpose-s_simcc-body7_pt-body7_420e-256x192-acd4a1ef_20230504.zip"
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
