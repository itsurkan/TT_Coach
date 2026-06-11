#!/usr/bin/env python
"""Tidy loose videos in Videos/ root and run the RTMPose export on them.

Flow per video:
  1. Move Videos/<name>.mp4 -> Videos/<name>/<name>.mp4 (viewer folder convention).
  2. Detect fps via cv2 and export full-fps pose JSON (schema v2) next to the video
     by shelling out to export_poses_rtmpose.py with --interval round(1000/fps).

Usage:
  .venv/bin/python scripts/poses/export_new.py            # all loose videos in Videos/ root
  .venv/bin/python scripts/poses/export_new.py video_3    # only the named ones
                                                          # (loose file, or existing folder
                                                          #  missing its *_poses_rtm.json)

Existing Videos/<base>/ folders are never scanned implicitly — name them explicitly
to (re)export. The poses_viewer picks up new folders/JSON on page refresh.
"""

import argparse
import shutil
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
VIDEOS_DIR = REPO_ROOT / "Videos"
EXPORTER = Path(__file__).resolve().parent / "export_poses_rtmpose.py"
VIDEO_EXTS = {".mp4", ".mov", ".webm"}


def detect_interval_ms(video: Path) -> int:
    import cv2  # noqa: PLC0415 — defer so --help works without the venv

    cap = cv2.VideoCapture(str(video))
    fps = cap.get(cv2.CAP_PROP_FPS)
    cap.release()
    if not fps or fps <= 0:
        print(f"  WARNING: cannot read fps of {video.name}, falling back to 33 ms (~30 fps)")
        return 33
    return max(1, round(1000 / fps))


def tidy_loose_file(loose: Path) -> Path | None:
    """Move Videos/<name>.ext into Videos/<name>/<name>.ext. Returns new path or None."""
    folder = VIDEOS_DIR / loose.stem
    target = folder / loose.name
    if folder.is_dir() and any(f.suffix.lower() in VIDEO_EXTS for f in folder.iterdir()):
        print(f"  SKIP {loose.name}: folder {folder.name}/ already contains a video")
        return None
    folder.mkdir(exist_ok=True)
    shutil.move(str(loose), str(target))
    print(f"  moved {loose.name} -> Videos/{folder.name}/{loose.name}")
    return target


def find_pending(names: list[str]) -> list[Path]:
    """Resolve work items to video paths (tidying loose files along the way)."""
    videos: list[Path] = []
    if names:
        for name in names:
            stem = Path(name).stem
            loose = next((p for p in VIDEOS_DIR.glob(f"{stem}.*") if p.is_file() and p.suffix.lower() in VIDEO_EXTS), None)
            if loose:
                moved = tidy_loose_file(loose)
                if moved:
                    videos.append(moved)
                continue
            folder = VIDEOS_DIR / stem
            vid = next((p for p in folder.iterdir() if p.suffix.lower() in VIDEO_EXTS), None) if folder.is_dir() else None
            if vid:
                videos.append(vid)
            else:
                print(f"  ERROR: no loose file or folder video found for '{name}'")
    else:
        for loose in sorted(VIDEOS_DIR.iterdir()):
            if loose.is_file() and loose.suffix.lower() in VIDEO_EXTS:
                moved = tidy_loose_file(loose)
                if moved:
                    videos.append(moved)
    return videos


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("names", nargs="*", help="video base names to process (default: all loose files in Videos/ root)")
    parser.add_argument("--feet", action="store_true", help="forward --feet (Halpe26 with foot keypoints)")
    args = parser.parse_args()
    sys.stdout.reconfigure(line_buffering=True)

    if not EXPORTER.is_file():
        print(f"exporter not found: {EXPORTER}", file=sys.stderr)
        return 1

    print("Scanning for new videos…")
    videos = find_pending(args.names)
    if not videos:
        print("Nothing to do.")
        return 0

    exported, skipped, failed = [], [], []
    for video in videos:
        out_json = video.parent / f"{video.stem}_poses_rtm.json"
        if out_json.exists():
            print(f"  SKIP {video.stem}: {out_json.name} already exists")
            skipped.append(video.stem)
            continue
        interval = detect_interval_ms(video)
        # -u: unbuffered, so progress streams when stdout is a pipe (tee/background log)
        cmd = [sys.executable, "-u", str(EXPORTER), str(video), "--interval", str(interval)]
        if args.feet:
            cmd.append("--feet")
        print(f"\n=== {video.stem}: exporting at --interval {interval} ms ===", flush=True)
        result = subprocess.run(cmd)
        if result.returncode == 0 and out_json.exists():
            exported.append(video.stem)
        else:
            failed.append(video.stem)
            print(f"  FAILED: {video.stem} (exit {result.returncode})")

    print("\n--- Summary ---")
    print(f"exported: {', '.join(exported) or '—'}")
    if skipped:
        print(f"skipped:  {', '.join(skipped)}")
    if failed:
        print(f"FAILED:   {', '.join(failed)}")
    if exported:
        print("Refresh poses_viewer (http://localhost:5780) and enable the RTM header toggle.")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
