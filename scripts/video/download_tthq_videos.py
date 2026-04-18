"""Download TTHQ videos from YouTube using yt-dlp. Only downloads videos that have table keypoint annotations."""
import re
import subprocess
import sys
from pathlib import Path

TTHQ_DIR = Path("D:/Desktop/TT_Coach_AI/datasets/tthq")
VIDEOS_DIR = TTHQ_DIR / "videos"
ANN_DIR = TTHQ_DIR / "tthq_annotations"


def get_annotated_video_names():
    """Return set of video names that have table keypoint annotations."""
    import csv
    names = set()
    for csv_file in ANN_DIR.glob("*_keypoints.csv"):
        vname = csv_file.stem.replace("_cut_keypoints", "")
        if vname.startswith("old_"):
            continue
        with open(csv_file, newline="") as f:
            f.readline()
            reader = csv.DictReader(f, delimiter=";")
            for row in reader:
                try:
                    flag = int(float(row.get("01_flag", 0)))
                except (ValueError, TypeError):
                    continue
                if flag >= 2:
                    names.add(vname)
                    break
    return names


def parse_video_list():
    """Parse video_list.txt into {name: {url, ss, to}}."""
    data = (TTHQ_DIR / "video_list.txt").read_bytes().decode("utf-8")
    entries = {}
    for block in data.split("\n\n"):
        lines = block.strip().split("\n")
        yt_line = next((l for l in lines if "yt-dlp" in l), None)
        mv_line = next((l for l in lines if l.startswith("mv ")), None)
        ff_line = next((l for l in lines if "ffmpeg" in l), None)
        if not yt_line or not mv_line:
            continue

        name = mv_line.split()[-1].replace("_tmp.mp4", "")
        url_match = re.search(r"https://www\.youtube\.com/watch\?v=[\w\-]+", yt_line)
        if not url_match:
            continue

        ss, to = None, None
        if ff_line:
            ss_m = re.search(r"-ss (\S+)", ff_line)
            to_m = re.search(r"-to (\S+)", ff_line)
            ss = ss_m.group(1) if ss_m else None
            to = to_m.group(1) if to_m else None

        entries[name] = {"url": url_match.group(), "ss": ss, "to": to}
    return entries


def download(name, info):
    """Download a single video at 360p."""
    out_path = VIDEOS_DIR / f"{name}_cut.mp4"
    if out_path.exists():
        mb = out_path.stat().st_size / 1024 / 1024
        print(f"  SKIP {name} (exists, {mb:.0f} MB)")
        return True

    print(f"  {name}: {info['url']}")
    cmd = [
        sys.executable, "-m", "yt_dlp",
        "-f", "best[height<=480]",  # single stream, no merge needed
        "-o", str(out_path),
    ]
    if info["ss"] and info["to"]:
        cmd.extend(["--download-sections", f"*{info['ss']}-{info['to']}"])
        cmd.append("--force-keyframes-at-cuts")
    cmd.append(info["url"])

    try:
        r = subprocess.run(cmd, timeout=300, capture_output=True, text=True)
        if r.returncode != 0:
            # Fallback without section cutting
            print(f"    Retrying full download...")
            cmd2 = [
                sys.executable, "-m", "yt_dlp",
                "-f", "best[height<=480]",
                "-o", str(out_path),
                info["url"],
            ]
            r = subprocess.run(cmd2, timeout=600, capture_output=True, text=True)
            if r.returncode != 0:
                print(f"    FAILED")
                return False
    except subprocess.TimeoutExpired:
        print(f"    TIMEOUT")
        return False

    if out_path.exists():
        mb = out_path.stat().st_size / 1024 / 1024
        print(f"    OK ({mb:.0f} MB)")
        return True
    return False


def main():
    VIDEOS_DIR.mkdir(parents=True, exist_ok=True)

    needed = get_annotated_video_names()
    all_videos = parse_video_list()

    to_download = {n: all_videos[n] for n in needed if n in all_videos}
    missing = needed - set(all_videos.keys())

    print(f"Need {len(needed)} videos, {len(to_download)} found in video_list.txt")
    if missing:
        print(f"  Not in video_list: {sorted(missing)}")
    print()

    ok = 0
    for name, info in sorted(to_download.items()):
        if download(name, info):
            ok += 1

    print(f"\nDone: {ok}/{len(to_download)} videos downloaded")


if __name__ == "__main__":
    main()
