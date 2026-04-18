#!/usr/bin/env python3
"""
process_video.py

Full pipeline for any video file. Handles:
  0. Copy video to assets/<base>/ subfolder if not already there
  1. Export poses  (Python MediaPipe, PC-side)
  2. Export ball detections  (Python OpenCV, PC-side)
  3. Merge poses + ball  ->  *_poses_ball.json

Usage:
    python scripts/process_video.py <video_name_or_path>

Examples:
    python scripts/process_video.py IMG_6370.MP4
    python scripts/process_video.py C:/Users/Ivan/Downloads/IMG_6370.MP4
    python scripts/process_video.py video_2026-03-01_13-59-49.mp4 --skip-ball

Options:
    --interval N       Frame sampling interval in ms (default 100)
    --skip-poses       Skip pose export (reuse existing _poses.json)
    --skip-ball        Skip ball detection (reuse existing _ball.json)
    --color COLOR      Ball color: white (default) or orange
"""

import argparse
import os
import shutil
import subprocess
import sys

# ── Paths ─────────────────────────────────────────────────────────────────────
SCRIPTS_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_DIR = os.path.normpath(os.path.join(SCRIPTS_DIR, "..", ".."))
SCRIPTS_ROOT = os.path.join(PROJECT_DIR, "scripts")
VIDEOS_DIR  = os.path.join(PROJECT_DIR, "Videos")

PYTHON      = sys.executable


# ── Helpers ───────────────────────────────────────────────────────────────────

def run(cmd: list[str], *, check: bool = True, cwd: str | None = None) -> int:
    print(f"\n$ {' '.join(cmd)}")
    result = subprocess.run(cmd, cwd=cwd)
    if check and result.returncode != 0:
        print(f"ERROR: command failed (exit {result.returncode})", file=sys.stderr)
        sys.exit(result.returncode)
    return result.returncode


def video_subdir(base: str) -> str:
    """Returns the per-video subfolder path: Videos/<base>/"""
    return os.path.join(VIDEOS_DIR, base)


def ensure_in_assets(video_name: str, src_path: str | None, base: str) -> str:
    """Copies the video to Videos/<base>/ if not already there. Returns the asset path."""
    subdir = video_subdir(base)
    os.makedirs(subdir, exist_ok=True)
    dest = os.path.join(subdir, video_name)
    if os.path.isfile(dest):
        print(f"  Video already in assets: {dest}")
        return dest
    if src_path is None or not os.path.isfile(src_path):
        print(f"ERROR: video not found at '{src_path or dest}'", file=sys.stderr)
        print(f"  Copy it to: {subdir}", file=sys.stderr)
        sys.exit(1)
    print(f"  Copying {src_path} -> {dest}")
    shutil.copy2(src_path, dest)
    return dest


# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="Full pose+ball pipeline for one video.")
    parser.add_argument("video",
        help="Video filename (e.g. IMG_6370.MP4) or full path to the video file")
    parser.add_argument("--interval",       type=int, default=100)
    parser.add_argument("--skip-poses",     action="store_true")
    parser.add_argument("--skip-ball",      action="store_true")
    parser.add_argument("--color", choices=["white", "orange"], default="white",
        help="Ball color to detect (default: white)")
    args = parser.parse_args()

    # Resolve video name and optional source path
    video_arg  = args.video
    if os.path.sep in video_arg or "/" in video_arg:
        src_path   = os.path.abspath(video_arg)
        video_name = os.path.basename(src_path)
    else:
        src_path   = None
        video_name = video_arg

    base = video_name.rsplit(".", 1)[0]

    print(f"\n=== Processing: {video_name} ===")

    # ── Step 0: Copy to per-video subfolder if needed ────────────────────────
    print("\n[0/3] Checking assets folder...")
    video_path = ensure_in_assets(video_name, src_path, base)
    subdir     = video_subdir(base)

    # ── Step 1: Export poses (Python MediaPipe) ──────────────────────────────
    poses_json = os.path.join(subdir, base + "_poses.json")
    if args.skip_poses:
        print(f"\n[1/3] Skipping pose export.")
        if not os.path.isfile(poses_json):
            print(f"  WARNING: {poses_json} missing -- merge will have no landmarks")
    else:
        print(f"\n[1/3] Exporting poses...")
        run([PYTHON, os.path.join(SCRIPTS_ROOT, "poses", "export_poses.py"),
             video_path, "--interval", str(args.interval)])

    # ── Step 2: Export ball detections (Python OpenCV) ────────────────────────
    ball_json_local = os.path.join(subdir, base + "_ball.json")
    if args.skip_ball:
        print(f"\n[2/3] Skipping ball detection.")
        if not os.path.isfile(ball_json_local):
            print(f"  WARNING: {ball_json_local} missing -- merge will have no ball data")
    else:
        print(f"\n[2/3] Exporting ball detections...")
        run([PYTHON, os.path.join(SCRIPTS_ROOT, "ball", "export_ball.py"),
             video_path, "--interval", str(args.interval),
             "--color", args.color])

    # ── Step 3: Merge ────────────────────────────────────────────────────────
    print(f"\n[3/3] Merging poses + ball...")
    run([PYTHON, os.path.join(SCRIPTS_ROOT, "ball", "merge_poses_ball.py"), "--video", base])

    out = os.path.join(subdir, base + "_poses_ball.json")
    print(f"\nDone. Load in poses viewer:\n  {out}")


if __name__ == "__main__":
    main()
