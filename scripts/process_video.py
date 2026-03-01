#!/usr/bin/env python3
"""
process_video.py

Full pipeline for a video that already lives in app/src/main/assets/Videos/.

Steps:
  1. Export poses  (Python MediaPipe, PC-side)
  2. Run ball detection test on device  (adb + instrumented test)
  3. Pull ball JSON from device
  4. Merge poses + ball  -> *_poses_ball.json

Usage:
    python scripts/process_video.py <video_name>
    python scripts/process_video.py video_2026-03-01_13-59-49.mp4

Requirements:
    pip install mediapipe opencv-python
    adb in PATH  (or set ADB env var)
"""

import argparse
import os
import subprocess
import sys

# ── Paths ────────────────────────────────────────────────────────────────────
SCRIPTS_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_DIR = os.path.normpath(os.path.join(SCRIPTS_DIR, ".."))
VIDEOS_DIR  = os.path.join(PROJECT_DIR, "app", "src", "main", "assets", "Videos")

PYTHON      = sys.executable          # same interpreter running this script
ADB         = os.environ.get("ADB", "adb")
DEVICE_OUT  = "/sdcard/Android/data/com.ttcoachai/files"
TEST_RUNNER = "com.ttcoachai.test/androidx.test.runner.AndroidJUnitRunner"
TEST_CLASS  = "com.ttcoachai.tracking.BallDetectorVideoTest#exportsBallDetectionsToJson"


def run(cmd: list[str], *, env: dict | None = None, check: bool = True) -> int:
    print(f"\n$ {' '.join(cmd)}")
    result = subprocess.run(cmd, env=env)
    if check and result.returncode != 0:
        print(f"ERROR: command failed (exit {result.returncode})", file=sys.stderr)
        sys.exit(result.returncode)
    return result.returncode


def main():
    parser = argparse.ArgumentParser(description="Full pose+ball pipeline for one video.")
    parser.add_argument("video", help="Video filename in app/src/main/assets/Videos/ (e.g. my_video.mp4)")
    parser.add_argument("--interval", type=int, default=100, help="Sampling interval ms (default 100)")
    parser.add_argument("--skip-poses",   action="store_true", help="Skip pose export (reuse existing _poses.json)")
    parser.add_argument("--skip-ball",    action="store_true", help="Skip ball detection test (reuse existing _ball.json)")
    args = parser.parse_args()

    video_name = args.video
    video_path = os.path.join(VIDEOS_DIR, video_name)
    base        = video_name.rsplit(".", 1)[0]

    if not os.path.isfile(video_path):
        print(f"ERROR: video not found: {video_path}", file=sys.stderr)
        sys.exit(1)

    print(f"\n=== Processing: {video_name} ===")

    # ── Step 1: Export poses ──────────────────────────────────────────────────
    poses_json = os.path.join(VIDEOS_DIR, base + "_poses.json")
    if args.skip_poses:
        print(f"\n[1/4] Skipping pose export (--skip-poses)")
        if not os.path.isfile(poses_json):
            print(f"  WARNING: {poses_json} does not exist — merge may be incomplete")
    else:
        print(f"\n[1/4] Exporting poses...")
        run([PYTHON, os.path.join(SCRIPTS_DIR, "export_poses.py"), video_path,
             "--interval", str(args.interval)])

    # ── Step 2: Ball detection on device ─────────────────────────────────────
    ball_json_device = f"{DEVICE_OUT}/{base}_ball.json"
    ball_json_local  = os.path.join(VIDEOS_DIR, base + "_ball.json")

    if args.skip_ball:
        print(f"\n[2/4] Skipping ball detection (--skip-ball)")
        print(f"[3/4] Skipping adb pull (--skip-ball)")
        if not os.path.isfile(ball_json_local):
            print(f"  WARNING: {ball_json_local} does not exist — merge may be incomplete")
    else:
        print(f"\n[2/4] Running ball detection on device...")
        env = {**os.environ, "MSYS_NO_PATHCONV": "1"}
        run([ADB, "shell", "am", "instrument",
             "-w", "-r",
             "-e", "class", TEST_CLASS,
             TEST_RUNNER], env=env)

        # ── Step 3: Pull ball JSON ────────────────────────────────────────────
        print(f"\n[3/4] Pulling ball JSON from device...")
        run([ADB, "pull", ball_json_device, VIDEOS_DIR + "/"], env=env)

    # ── Step 4: Merge ─────────────────────────────────────────────────────────
    print(f"\n[4/4] Merging poses + ball...")
    run([PYTHON, os.path.join(SCRIPTS_DIR, "merge_poses_ball.py")])

    out = os.path.join(VIDEOS_DIR, base + "_poses_ball.json")
    print(f"\nDone. Load in poses viewer:\n  {out}")


if __name__ == "__main__":
    main()
