#!/usr/bin/env python3
"""
process_video.py

Full pipeline for any video file. Handles:
  0. Copy video to assets/<base>/ subfolder if not already there
  1. Add video to EXPORT_VIDEOS in BallDetectorVideoTest.kt (if missing)
  2. Rebuild + reinstall APKs  (skipped if video was already registered)
  3. Export poses  (Python MediaPipe, PC-side)
  4. Run ball detection test on device  (adb)
  5. Pull ball JSON from device
  6. Merge poses + ball  ->  *_poses_ball.json

Usage:
    python scripts/process_video.py <video_name_or_path>

Examples:
    python scripts/process_video.py IMG_6370.MP4
    python scripts/process_video.py C:/Users/Ivan/Downloads/IMG_6370.MP4
    python scripts/process_video.py video_2026-03-01_13-59-49.mp4 --skip-ball

Options:
    --interval N       Frame sampling interval in ms (default 100)
    --skip-poses       Skip pose export (reuse existing _poses.json)
    --skip-ball        Skip ball detection + adb (reuse existing _ball.json)
    --skip-rebuild     Skip Gradle build + adb install even if video is new
    --force-install    Install already-built APKs without rebuilding
"""

import argparse
import os
import re
import shutil
import subprocess
import sys
import threading
import time

# ── Paths ─────────────────────────────────────────────────────────────────────
SCRIPTS_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_DIR = os.path.normpath(os.path.join(SCRIPTS_DIR, ".."))
VIDEOS_DIR  = os.path.join(PROJECT_DIR, "app", "src", "main", "assets", "Videos")
TEST_FILE   = os.path.join(PROJECT_DIR, "app", "src", "androidTest", "java",
                            "com", "ttcoachai", "tracking", "BallDetectorVideoTest.kt")
GRADLEW     = os.path.join(PROJECT_DIR, "gradlew.bat" if sys.platform == "win32" else "gradlew")
APK_DEBUG   = os.path.join(PROJECT_DIR, "app", "build", "outputs", "apk",
                            "debug", "app-arm64-v8a-debug.apk")
APK_TEST    = os.path.join(PROJECT_DIR, "app", "build", "outputs", "apk",
                            "androidTest", "debug", "app-debug-androidTest.apk")

PYTHON      = sys.executable

def _find_adb() -> str:
    if adb_env := os.environ.get("ADB"):
        return adb_env
    if sys.platform == "win32":
        candidate = os.path.expandvars(r"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe")
        if os.path.isfile(candidate):
            return candidate
    return "adb"

ADB = _find_adb()
DEVICE_OUT  = "/sdcard/Android/data/com.ttcoachai/files"
TEST_RUNNER = "com.ttcoachai.test/androidx.test.runner.AndroidJUnitRunner"
TEST_CLASS  = "com.ttcoachai.tracking.BallDetectorVideoTest#exportsBallDetectionsToJson"


# ── Helpers ───────────────────────────────────────────────────────────────────

def run(cmd: list[str], *, env: dict | None = None, check: bool = True, cwd: str | None = None) -> int:
    print(f"\n$ {' '.join(cmd)}")
    result = subprocess.run(cmd, env=env, cwd=cwd)
    if check and result.returncode != 0:
        print(f"ERROR: command failed (exit {result.returncode})", file=sys.stderr)
        sys.exit(result.returncode)
    return result.returncode


def run_instrument(cmd: list[str], *, env: dict | None = None) -> int:
    """Run adb instrument with a live status bar that parses output in real-time."""
    print(f"\n$ {' '.join(cmd)}\n")

    spinner_chars = "|/-\\"
    spin_idx      = 0
    start_time    = time.time()
    last_status   = ""
    lock          = threading.Lock()

    def update_spinner():
        nonlocal spin_idx
        while not done_event.is_set():
            elapsed = time.time() - start_time
            with lock:
                line = f"\r  {spinner_chars[spin_idx % 4]}  {last_status}  [{elapsed:.0f}s]   "
            print(line, end="", flush=True)
            spin_idx += 1
            time.sleep(0.12)

    done_event = threading.Event()
    spinner_thread = threading.Thread(target=update_spinner, daemon=True)
    spinner_thread.start()

    proc = subprocess.Popen(
        cmd, env=env,
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
        text=True, encoding="utf-8", errors="replace"
    )

    output_lines = []
    for raw_line in proc.stdout:
        line = raw_line.rstrip()
        output_lines.append(line)

        # Parse progress hints from the test's println() calls
        if "BallDetectorVideoTest:" in line:
            msg = line.split("BallDetectorVideoTest:", 1)[-1].strip()
            with lock:
                last_status = msg
            print(f"\r  {msg}{' ' * 20}")

        elif line.startswith("INSTRUMENTATION_STATUS: test="):
            test_name = line.split("test=", 1)[-1]
            with lock:
                last_status = f"running {test_name}"

        elif line.startswith("INSTRUMENTATION_RESULT:") or line.startswith("INSTRUMENTATION_CODE:"):
            with lock:
                last_status = line

    proc.wait()
    done_event.set()
    spinner_thread.join()

    elapsed = time.time() - start_time
    if proc.returncode == 0:
        print(f"\r  Done  [{elapsed:.0f}s]{' ' * 30}")
    else:
        print(f"\r  Failed (exit {proc.returncode})  [{elapsed:.0f}s]{' ' * 20}")
        print("\n--- instrument output ---")
        for l in output_lines[-30:]:
            print(" ", l)
        print("---")
        sys.exit(proc.returncode)

    return proc.returncode


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


def is_in_export_videos(video_name: str) -> bool:
    with open(TEST_FILE, encoding="utf-8") as f:
        return f'"{video_name}"' in f.read()


def add_to_export_videos(video_name: str):
    with open(TEST_FILE, encoding="utf-8") as f:
        src = f.read()
    pattern = r'(private val EXPORT_VIDEOS = listOf\(.*?\))'
    match = re.search(pattern, src, re.DOTALL)
    if not match:
        print("ERROR: could not find EXPORT_VIDEOS in test file", file=sys.stderr)
        sys.exit(1)
    old_block = match.group(1)
    new_block = old_block.rstrip(')') + f'        "{video_name}",\n        )'
    src = src.replace(old_block, new_block)
    with open(TEST_FILE, "w", encoding="utf-8") as f:
        f.write(src)
    print(f"  Added \"{video_name}\" to EXPORT_VIDEOS in BallDetectorVideoTest.kt")


def install_apks():
    env = {**os.environ, "MSYS_NO_PATHCONV": "1"}
    print("      Installing APKs...")
    run([ADB, "install", "-r", APK_DEBUG], env=env)
    run([ADB, "install", "-r", APK_TEST],  env=env)


def rebuild_and_install():
    print("\n[2/6] Building APKs...")
    run([GRADLEW, ":app:assembleDebug", ":app:assembleDebugAndroidTest"], cwd=PROJECT_DIR)
    install_apks()


# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="Full pose+ball pipeline for one video.")
    parser.add_argument("video",
        help="Video filename (e.g. IMG_6370.MP4) or full path to the video file")
    parser.add_argument("--interval",       type=int, default=100)
    parser.add_argument("--skip-poses",     action="store_true")
    parser.add_argument("--skip-ball",      action="store_true")
    parser.add_argument("--skip-rebuild",   action="store_true")
    parser.add_argument("--force-install",  action="store_true",
        help="Install already-built APKs without rebuilding")
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
    print("\n[0/6] Checking assets folder...")
    video_path = ensure_in_assets(video_name, src_path, base)
    subdir     = video_subdir(base)

    # ── Step 1: Add to EXPORT_VIDEOS if needed ───────────────────────────────
    needs_rebuild = False
    if not args.skip_ball:
        print("\n[1/6] Checking EXPORT_VIDEOS list...")
        if is_in_export_videos(video_name):
            print(f"  \"{video_name}\" already registered.")
        else:
            add_to_export_videos(video_name)
            needs_rebuild = True

    # ── Step 2: Rebuild + install ────────────────────────────────────────────
    if args.force_install and not args.skip_ball:
        print("\n[2/6] Force-installing existing APKs...")
        install_apks()
    elif needs_rebuild and not args.skip_rebuild and not args.skip_ball:
        rebuild_and_install()
    elif args.skip_ball or args.skip_rebuild:
        print("\n[2/6] Skipping rebuild.")
    else:
        print("\n[2/6] No rebuild needed (video already registered).")

    # ── Step 3: Export poses ─────────────────────────────────────────────────
    poses_json = os.path.join(subdir, base + "_poses.json")
    if args.skip_poses:
        print(f"\n[3/6] Skipping pose export.")
        if not os.path.isfile(poses_json):
            print(f"  WARNING: {poses_json} missing -- merge will have no landmarks")
    else:
        print(f"\n[3/6] Exporting poses...")
        run([PYTHON, os.path.join(SCRIPTS_DIR, "export_poses.py"),
             video_path, "--interval", str(args.interval)])

    # ── Step 4: Ball detection on device ─────────────────────────────────────
    ball_json_device = f"{DEVICE_OUT}/{base}_ball.json"
    ball_json_local  = os.path.join(subdir, base + "_ball.json")

    env = {**os.environ, "MSYS_NO_PATHCONV": "1"}
    if args.skip_ball:
        print(f"\n[4/6] Skipping ball detection.")
        print(f"[5/6] Skipping adb pull.")
        if not os.path.isfile(ball_json_local):
            print(f"  WARNING: {ball_json_local} missing -- merge will have no ball data")
    else:
        print(f"\n[4/6] Running ball detection on device...")
        run_instrument([ADB, "shell", "am", "instrument",
                        "-w", "-r", "-e", "class", TEST_CLASS, TEST_RUNNER], env=env)

        # ── Step 5: Pull ball JSON ───────────────────────────────────────────
        print(f"\n[5/6] Pulling ball JSON from device...")
        run([ADB, "pull", ball_json_device, subdir + "/"], env=env)

    # ── Step 6: Merge ────────────────────────────────────────────────────────
    print(f"\n[6/6] Merging poses + ball...")
    run([PYTHON, os.path.join(SCRIPTS_DIR, "merge_poses_ball.py"), "--video", base])

    out = os.path.join(subdir, base + "_poses_ball.json")
    print(f"\nDone. Load in poses viewer:\n  {out}")


if __name__ == "__main__":
    main()
