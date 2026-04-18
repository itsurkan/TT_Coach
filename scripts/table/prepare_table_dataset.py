"""
Prepare table keypoint detection dataset from two sources:
1. Local videos labeled with poses_viewer (table_labels.json)
2. TTHQ dataset (CSV annotations + YouTube videos)

Output: dataset/ folder with images + labels in YOLO keypoint format

Usage:
  python prepare_table_dataset.py --local-only    # just local videos
  python prepare_table_dataset.py --all            # local + TTHQ
"""

import argparse
import csv
import json
import os
import random
import subprocess
import sys
from pathlib import Path

import cv2
import numpy as np

# ── Config ───────────────────────────────────────────────────────────────────

VIDEOS_DIR = Path("D:/Desktop/TT_Coach_AI/app/src/main/assets/Videos")
TTHQ_DIR = Path("D:/Desktop/TT_Coach_AI/datasets/tthq")
OUTPUT_DIR = Path("D:/Desktop/TT_Coach_AI/datasets/table_keypoints")

NUM_KEYPOINTS = 6
TRAIN_RATIO = 0.85
IMG_SIZE = 640  # resize longest side to this

# TTHQ 13 keypoints indices:
#  0=close_left, 1=close_right, 2=center_left, 3=center_right,
#  4=far_left, 5=far_right
TTHQ_CORNER_INDICES = [0, 1, 4, 5]  # 4 table corners in TTHQ
TTHQ_NET_INDICES = [2, 3]            # 2 net-table intersections

# Our target order: pt1=farL, pt2=farR, pt3=nearR, pt4=nearL, pt5=netL, pt6=netR
# TTHQ "close/far" and "left/right" depend on camera angle.
# Per-video mapping from TTHQ indices to our order.
# Format: {tthq_index: our_index} for corners, net handled separately
# Group A: standard view — TTHQ far=screen-top, close=screen-bottom
#   farL=4→0, farR=5→1, nearR=1→2, nearL=0→3
# Group B: flipped view — TTHQ close=screen-top, far=screen-bottom
#   farL=1→0, farR=0→1, nearR=4→2, nearL=5→3  (close becomes far on screen)
# Group C: rotated — TTHQ close_R=screen-top-left
#   farL=1→0, farR=0→1, nearR=5→2, nearL=4→3
TTHQ_CORNER_MAP_A = {4: 0, 5: 1, 1: 2, 0: 3}  # standard
TTHQ_CORNER_MAP_B = {1: 0, 0: 1, 4: 2, 5: 3}  # camera on opposite side
TTHQ_CORNER_MAP_C = {1: 0, 0: 1, 5: 2, 4: 3}  # rotated

# Net mapping: TTHQ 2=center_left, 3=center_right
# Group A: center_left is screen-left → netL, center_right is screen-right → netR
# Group B: center_left is screen-right, center_right is screen-left (flipped)
TTHQ_NET_MAP_A = {2: 4, 3: 5}  # center_left→netL, center_right→netR
TTHQ_NET_MAP_B = {3: 4, 2: 5}  # swapped

TTHQ_VIDEO_GROUP = {
    '03': 'A', '05': 'A', '06': 'A', '07': 'A', '08': 'A', '09': 'A',
    '201': 'A', '205': 'A',
    '04': 'B', '101': 'B', '102': 'B', '103': 'B', '11': 'B', '203': 'B', '204': 'B',
    '202': 'C',
    # Videos with too few corners — use screen-space sorting (fallback)
    '01': 'A', '02': 'A', '10': 'A',
}


def project_point_onto_line(p, a, b):
    """Project point p onto line segment a-b. Returns projected point."""
    ax, ay = a
    bx, by = b
    dx, dy = bx - ax, by - ay
    len_sq = dx * dx + dy * dy
    if len_sq < 1e-12:
        return a
    t = ((p[0] - ax) * dx + (p[1] - ay) * dy) / len_sq
    t = max(0.0, min(1.0, t))
    return (ax + t * dx, ay + t * dy)


def snap_net_to_edges(kps, use_midpoints=False):
    """Snap net points onto the corner edges.
    pt5 (netL) → project onto line pt1—pt4 (farL—nearL)
    pt6 (netR) → project onto line pt2—pt3 (farR—nearR)
    If use_midpoints=True and all 4 corners visible, use edge midpoints instead.
    """
    if len(kps) < 6:
        return kps
    result = list(kps)

    all_corners = all(kps[i] is not None for i in range(4))

    if use_midpoints and all_corners:
        # pt5 = midpoint of edge pt1(0)—pt4(3)
        if kps[4] is not None:
            result[4] = ((kps[0][0] + kps[3][0]) / 2, (kps[0][1] + kps[3][1]) / 2)
        # pt6 = midpoint of edge pt2(1)—pt3(2)
        if kps[5] is not None:
            result[5] = ((kps[1][0] + kps[2][0]) / 2, (kps[1][1] + kps[2][1]) / 2)
    else:
        # Project onto edges (whichever edge is visible)
        # pt5 (netL) onto edge pt1(0)—pt4(3)
        if kps[4] is not None and kps[0] is not None and kps[3] is not None:
            result[4] = project_point_onto_line(kps[4], kps[0], kps[3])
        # pt6 (netR) onto edge pt2(1)—pt3(2)
        if kps[5] is not None and kps[1] is not None and kps[2] is not None:
            result[5] = project_point_onto_line(kps[5], kps[1], kps[2])

        # If only one edge visible, still project the net point on that edge
        # pt5 missing its edge but pt6's edge exists → project pt5 onto 2—3
        if kps[4] is not None and (kps[0] is None or kps[3] is None) and kps[1] is not None and kps[2] is not None:
            result[4] = project_point_onto_line(kps[4], kps[1], kps[2])
        # pt6 missing its edge but pt5's edge exists → project pt6 onto 1—4
        if kps[5] is not None and (kps[1] is None or kps[2] is None) and kps[0] is not None and kps[3] is not None:
            result[5] = project_point_onto_line(kps[5], kps[0], kps[3])

    return result


def ensure_dir(p: Path):
    p.mkdir(parents=True, exist_ok=True)


# ── Extract frames from local videos ────────────────────────────────────────

def extract_local_frames():
    """Extract labeled frames from local videos with table_labels.json."""
    samples = []

    for video_dir in sorted(VIDEOS_DIR.iterdir()):
        if not video_dir.is_dir():
            continue
        label_file = video_dir / f"{video_dir.name}_table_labels.json"
        if not label_file.exists():
            continue

        # Find the video file
        video_file = None
        for ext in [".mp4", ".MP4", ".mov", ".MOV", ".webm"]:
            candidate = video_dir / f"{video_dir.name}{ext}"
            if candidate.exists():
                video_file = candidate
                break
        if not video_file:
            # Try any video in the directory
            for f in video_dir.iterdir():
                if f.suffix.lower() in [".mp4", ".mov", ".webm"]:
                    video_file = f
                    break
        if not video_file:
            print(f"  SKIP {video_dir.name}: no video file found")
            continue

        with open(label_file) as f:
            data = json.load(f)

        labels = data.get("labels", {})
        if not labels:
            continue

        cap = cv2.VideoCapture(str(video_file))
        if not cap.isOpened():
            print(f"  SKIP {video_dir.name}: cannot open video")
            continue

        total = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
        w = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
        h = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
        print(f"  {video_dir.name}: {len(labels)} labels, {total} frames, {w}x{h}")

        for frame_key, frame_label in labels.items():
            frame_idx = int(frame_key)
            points = frame_label.get("points", [])
            if not points:
                continue
            # Need at least 3 visible points to be useful
            visible_count = sum(1 for p in points[:NUM_KEYPOINTS] if p is not None)
            if visible_count < 3:
                continue

            cap.set(cv2.CAP_PROP_POS_FRAMES, frame_idx)
            ret, img = cap.read()
            if not ret:
                continue

            # Points: keep user's original click order, with per-video remapping
            # Remap: {video_name: [new_index_for_pt0, new_index_for_pt1, ...]}
            # e.g. IMG_6330: pt3→4, pt4→5, pt5→6, pt6→3 means indices [0,1,3,4,5,2]
            # Remap: "3→4" means what user clicked as point 3 should be point 4
            # Format: dict of {src_1based: dst_1based} or None to delete
            # result[dst-1] = raw[src-1]
            LOCAL_REMAP = {
                'IMG_6330': {3: 6, 4: 3, 5: 4, 6: 5},
                'IMG_6332': {3: 6, 4: 3, 5: 4, 6: 5},
                'IMG_6414': {3: 6, 4: 3, 5: 4, 6: 5},
                'IMG_6370': {1: 2, 2: 6},
                'table_11': {3: 6},
                'table_12': {3: 6, 5: 4, 6: 5},
                'table_v2': {3: 6, 4: 3, 5: 4, 6: 5},
                'table_v3': {3: 6, 4: 3, 5: 4, 6: 5},
                'table_v7': {3: 6, 4: 3, 5: 4, 6: 5},
            }
            raw_kps = []
            for i in range(NUM_KEYPOINTS):
                p = points[i] if i < len(points) else None
                raw_kps.append((p["x"], p["y"]) if p is not None else None)

            remap = LOCAL_REMAP.get(video_dir.name)
            if remap:
                kps = list(raw_kps)  # start with copy
                # Build full permutation: for each src, find its destination
                for src_1, dst_1 in remap.items():
                    src_idx = src_1 - 1
                    if dst_1 is None:
                        kps[src_idx] = '__delete__'  # mark for deletion
                    # else handled below
                # Apply all moves at once from raw
                result = [None] * NUM_KEYPOINTS
                moved = set()
                for src_1, dst_1 in remap.items():
                    src_idx = src_1 - 1
                    if dst_1 is None:
                        moved.add(src_idx)
                        continue
                    dst_idx = dst_1 - 1
                    result[dst_idx] = raw_kps[src_idx]
                    moved.add(src_idx)
                # Keep unmoved points in their original position
                for i in range(NUM_KEYPOINTS):
                    if i not in moved and result[i] is None:
                        result[i] = raw_kps[i]
                kps = result
            else:
                kps = raw_kps
            samples.append({
                "source": "local",
                "name": f"{video_dir.name}_f{frame_idx:05d}",
                "image": img,
                "keypoints": kps,  # normalized 0-1
            })

        cap.release()

    print(f"  Local: {len(samples)} samples extracted")
    return samples


# ── Extract frames from TTHQ ────────────────────────────────────────────────

def parse_time_to_seconds(t: str) -> float:
    """Convert HH:MM:SS or MM:SS to seconds."""
    parts = t.split(":")
    if len(parts) == 3:
        return int(parts[0]) * 3600 + int(parts[1]) * 60 + float(parts[2])
    elif len(parts) == 2:
        return int(parts[0]) * 60 + float(parts[1])
    return float(t)


def load_tthq_cut_info(tthq_dir: Path):
    """Parse video_list.txt to get cut offsets and original fps per video."""
    import re
    info = {}
    vlist = tthq_dir / "video_list.txt"
    if not vlist.exists():
        return info
    data = vlist.read_bytes().decode("utf-8")
    for block in data.split("\n\n"):
        lines = block.strip().split("\n")
        mv = next((l for l in lines if l.startswith("mv ")), None)
        ff = next((l for l in lines if "ffmpeg" in l), None)
        if not mv:
            continue
        name = mv.split()[-1].replace("_tmp.mp4", "").replace("_cut.mp4", "")
        ss = 0.0
        cut_duration = None
        if ff:
            ss_m = re.search(r"-ss (\S+)", ff)
            to_m = re.search(r"-to (\S+)", ff)
            if ss_m:
                ss = parse_time_to_seconds(ss_m.group(1))
            if ss_m and to_m:
                cut_duration = parse_time_to_seconds(to_m.group(1)) - ss
        info[name] = {"ss_offset": ss, "cut_duration": cut_duration}
    return info


def load_tthq_annotations(annotation_dir: Path):
    """Load TTHQ CSV annotations and return {video_name: {frame: (kps, original_fps)}}."""
    result = {}
    for csv_file in sorted(annotation_dir.glob("*_keypoints.csv")):
        video_name = csv_file.stem.replace("_cut_keypoints", "")
        if video_name.startswith("old_"):
            continue

        # First line is #NFRAMES — use it to determine original fps
        with open(csv_file) as f:
            first_line = f.readline().strip()

        total_ann_frames = int(first_line.replace("#", "")) if first_line.startswith("#") else 0

        frames = {}
        with open(csv_file, newline="") as f:
            f.readline()  # skip first line
            reader = csv.DictReader(f, delimiter=";")
            for row in reader:
                try:
                    frame_num = int(float(row.get("frame", -1)))
                except (ValueError, TypeError):
                    continue
                if frame_num < 0:
                    continue

                kps = []
                for i in range(1, 14):
                    prefix = f"{i:02d}"
                    try:
                        x = float(row.get(f"{prefix}_x", 0))
                        y = float(row.get(f"{prefix}_y", 0))
                        flag = int(float(row.get(f"{prefix}_flag", 0)))
                    except (ValueError, TypeError):
                        x, y, flag = 0, 0, 0
                    kps.append((x, y, flag))

                needed_tthq = [0, 1, 2, 3, 4, 5]
                visible = sum(1 for i in needed_tthq if kps[i][2] >= 2)
                if visible >= 3:
                    frames[frame_num] = kps

        if frames:
            result[video_name] = {"frames": frames, "total_ann_frames": total_ann_frames}
            print(f"  TTHQ {video_name}: {len(frames)} annotated frames (ann total={total_ann_frames})")

    return result


def download_tthq_videos(tthq_dir: Path):
    """Download TTHQ videos using yt-dlp based on video_list.txt."""
    video_list = tthq_dir / "video_list.txt"
    if not video_list.exists():
        print("  Downloading video_list.txt from GitHub...")
        subprocess.run([
            "python3", "-m", "yt_dlp", "--version"
        ], capture_output=True)
        # Download the video_list.txt from the repo
        import urllib.request
        url = "https://raw.githubusercontent.com/KieDani/UpliftingTableTennis/main/video_list.txt"
        urllib.request.urlretrieve(url, str(video_list))

    if not video_list.exists():
        print("  ERROR: Cannot find video_list.txt")
        return

    # Parse video_list.txt for download commands
    videos_dir = tthq_dir / "videos"
    ensure_dir(videos_dir)

    with open(video_list) as f:
        content = f.read()

    # Extract yt-dlp commands and mv/ffmpeg commands
    lines = content.split("\n")
    current_block = []
    blocks = []
    for line in lines:
        line = line.strip()
        if line.startswith("yt-dlp") or line.startswith("mv ") or line.startswith("ffmpeg"):
            current_block.append(line)
        elif not line and current_block:
            blocks.append(current_block)
            current_block = []
    if current_block:
        blocks.append(current_block)

    for block in blocks:
        # Find the target filename from mv command
        mv_cmd = next((l for l in block if l.startswith("mv ")), None)
        if not mv_cmd:
            continue
        # Extract target name like "01_tmp.mp4"
        parts = mv_cmd.split('"')
        if len(parts) >= 3:
            target = parts[-1].strip() if parts[-1].strip() else parts[-2].strip()
        else:
            target = mv_cmd.split()[-1]

        cut_name = target.replace("_tmp.mp4", "_cut.mp4")
        cut_path = videos_dir / cut_name
        if cut_path.exists():
            print(f"  Already have {cut_name}")
            continue

        # Execute yt-dlp download
        ytdlp_cmd = next((l for l in block if l.startswith("yt-dlp")), None)
        if not ytdlp_cmd:
            continue

        print(f"  Downloading {target}...")
        tmp_path = videos_dir / target
        try:
            # Run yt-dlp
            dl_parts = ytdlp_cmd.split()
            subprocess.run(
                ["python3", "-m", "yt_dlp"] + dl_parts[1:] + ["-o", str(tmp_path)],
                cwd=str(videos_dir),
                timeout=300,
            )
        except Exception as e:
            print(f"    Download failed: {e}")
            continue

        # Run ffmpeg cut if specified
        ffmpeg_cmd = next((l for l in block if l.startswith("ffmpeg")), None)
        if ffmpeg_cmd and tmp_path.exists():
            # Replace filenames in ffmpeg command
            ffmpeg_cmd = ffmpeg_cmd.replace("-hwaccel cuda ", "")  # Remove CUDA (may not be available)
            ffmpeg_cmd = ffmpeg_cmd.replace("-c:v h264_nvenc", "-c:v libx264")
            ffmpeg_parts = ffmpeg_cmd.split()
            # Replace input/output filenames
            for i, p in enumerate(ffmpeg_parts):
                if p == "-i" and i + 1 < len(ffmpeg_parts):
                    ffmpeg_parts[i + 1] = str(tmp_path)
                if p.endswith("_cut.mp4"):
                    ffmpeg_parts[i] = str(cut_path)

            print(f"    Cutting to {cut_name}...")
            try:
                subprocess.run(ffmpeg_parts, timeout=120, capture_output=True)
            except Exception as e:
                print(f"    Cut failed: {e}")


def extract_tthq_frames(tthq_dir: Path, annotations):
    """Extract annotated frames from TTHQ videos using timestamp-based seeking."""
    samples = []
    videos_dir = tthq_dir / "videos"
    cut_info = load_tthq_cut_info(tthq_dir)

    # TTHQ annotations are in pixel coordinates — resolution varies per video
    # Detect from max coordinate values in annotations

    for video_name, ann_data in annotations.items():
        frames_data = ann_data["frames"]
        total_ann_frames = ann_data["total_ann_frames"]

        # Annotation resolution: all videos annotated at 1920x1080 except 201 (720p source)
        TTHQ_720P_VIDEOS = {"201"}
        if video_name in TTHQ_720P_VIDEOS:
            ANN_W, ANN_H = 1280, 720
        else:
            ANN_W, ANN_H = 1920, 1080

        # Find the video file
        video_file = None
        for suffix in ["_cut.mp4", "_tmp.mp4", ".mp4"]:
            candidate = videos_dir / f"{video_name}{suffix}"
            if candidate.exists():
                video_file = candidate
                break
        if not video_file:
            print(f"  SKIP TTHQ {video_name}: video not found")
            continue

        cap = cv2.VideoCapture(str(video_file))
        if not cap.isOpened():
            print(f"  SKIP TTHQ {video_name}: cannot open")
            continue

        video_fps = cap.get(cv2.CAP_PROP_FPS)
        video_total = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
        video_duration = video_total / video_fps if video_fps > 0 else 0

        # Annotation frames are at the original fps; video may be at different fps
        # Use expected cut duration from video_list.txt for accurate fps calculation
        cut_dur = cut_info.get(video_name, {}).get("cut_duration")
        if cut_dur and cut_dur > 0 and total_ann_frames > 0:
            original_fps = total_ann_frames / cut_dur
        elif total_ann_frames > 0 and video_duration > 0:
            original_fps = total_ann_frames / video_duration
        else:
            original_fps = video_fps
        # Snap to nearest standard fps
        for std_fps in [25, 30, 50, 60]:
            if abs(original_fps - std_fps) < 3:
                original_fps = std_fps
                break

        count = 0

        for frame_num, tthq_kps in frames_data.items():
            # Seek by timestamp (handles fps mismatch between annotation and download)
            seek_time_ms = (frame_num / original_fps) * 1000
            cap.set(cv2.CAP_PROP_POS_MSEC, seek_time_ms)
            ret, img = cap.read()
            if not ret:
                continue

            # Map TTHQ 13 keypoints → our 6 keypoints
            # TTHQ coords are in pixels at 1920x1080; normalize to 0-1
            # Extract visible corners and net points
            corners = []  # list of (x_norm, y_norm) for 4 corners
            for ci in TTHQ_CORNER_INDICES:
                x_px, y_px, flag = tthq_kps[ci]
                if flag >= 2 and 0 <= x_px <= ANN_W and 0 <= y_px <= ANN_H:
                    corners.append((x_px / ANN_W, y_px / ANN_H))
                else:
                    corners.append(None)

            net_pts = []  # list of (x_norm, y_norm) for 2 net points
            for ni in TTHQ_NET_INDICES:
                x_px, y_px, flag = tthq_kps[ni]
                if flag >= 2 and 0 <= x_px <= ANN_W and 0 <= y_px <= ANN_H:
                    net_pts.append((x_px / ANN_W, y_px / ANN_H))
                else:
                    net_pts.append(None)

            visible_count = sum(1 for c in corners if c is not None) + sum(1 for n in net_pts if n is not None)
            if visible_count < 3:
                continue

            # Use per-video mapping
            group = TTHQ_VIDEO_GROUP.get(video_name, 'A')
            if group == 'A':
                corner_map = TTHQ_CORNER_MAP_A
                net_map = TTHQ_NET_MAP_A
            elif group == 'B':
                corner_map = TTHQ_CORNER_MAP_B
                net_map = TTHQ_NET_MAP_B
            else:  # C
                corner_map = TTHQ_CORNER_MAP_C
                net_map = TTHQ_NET_MAP_B  # same net swap as B

            our_kps = [None] * NUM_KEYPOINTS
            # Map corners: TTHQ_CORNER_INDICES = [0, 1, 4, 5]
            for ci in TTHQ_CORNER_INDICES:
                if corners[TTHQ_CORNER_INDICES.index(ci)] is not None and ci in corner_map:
                    our_kps[corner_map[ci]] = corners[TTHQ_CORNER_INDICES.index(ci)]

            # Map net points: TTHQ_NET_INDICES = [2, 3]
            for ni in TTHQ_NET_INDICES:
                if net_pts[TTHQ_NET_INDICES.index(ni)] is not None and ni in net_map:
                    our_kps[net_map[ni]] = net_pts[TTHQ_NET_INDICES.index(ni)]

            if sum(1 for p in our_kps if p is not None) < 3:
                continue

            samples.append({
                "source": "tthq",
                "name": f"tthq_{video_name}_f{frame_num:05d}",
                "image": img,
                "keypoints": our_kps,  # already normalized 0-1 relative to annotation res
            })
            count += 1

        cap.release()
        print(f"  TTHQ {video_name}: {count} frames extracted")

    print(f"  TTHQ total: {len(samples)} samples")
    return samples


# ── Write dataset in YOLO keypoint format ────────────────────────────────────

def resize_and_pad(img, target_size):
    """Resize keeping aspect ratio, pad to square."""
    h, w = img.shape[:2]
    scale = target_size / max(h, w)
    new_w, new_h = int(w * scale), int(h * scale)
    resized = cv2.resize(img, (new_w, new_h), interpolation=cv2.INTER_LINEAR)

    # Pad to target_size x target_size
    canvas = np.zeros((target_size, target_size, 3), dtype=np.uint8)
    y_off = (target_size - new_h) // 2
    x_off = (target_size - new_w) // 2
    canvas[y_off:y_off + new_h, x_off:x_off + new_w] = resized

    return canvas, scale, x_off, y_off, new_w, new_h


def write_yolo_dataset(samples, output_dir: Path, img_size=IMG_SIZE):
    """Write dataset in YOLO keypoint detection format."""
    ensure_dir(output_dir)

    # Shuffle and split
    random.seed(42)
    random.shuffle(samples)
    split = int(len(samples) * TRAIN_RATIO)
    splits = {"train": samples[:split], "val": samples[split:]}

    for split_name, split_samples in splits.items():
        img_dir = output_dir / split_name / "images"
        lbl_dir = output_dir / split_name / "labels"
        ensure_dir(img_dir)
        ensure_dir(lbl_dir)

        for sample in split_samples:
            name = sample["name"]
            img = sample["image"]
            kps = sample["keypoints"]
            h_orig, w_orig = img.shape[:2]

            # Resize and pad to square
            img_out, scale, x_off, y_off, new_w, new_h = resize_and_pad(img, img_size)

            # Convert keypoints: normalized(0-1 of original) → position in padded square → normalized(0-1 of padded)
            # The image content sits at (x_off, y_off) with size (new_w, new_h) inside the img_size x img_size canvas
            yolo_kps = []
            for pt in kps:
                if pt is not None:
                    x_in_pad = pt[0] * new_w + x_off
                    y_in_pad = pt[1] * new_h + y_off
                    x_final = max(0.0, min(1.0, x_in_pad / img_size))
                    y_final = max(0.0, min(1.0, y_in_pad / img_size))
                    yolo_kps.extend([x_final, y_final, 2])  # 2 = visible
                else:
                    yolo_kps.extend([0, 0, 0])  # 0 = not visible

            # YOLO format: class x_center y_center width height kp1_x kp1_y kp1_v ...
            # Bounding box from visible keypoints only
            xs = [pt[0] * new_w + x_off for pt in kps if pt is not None]
            ys = [pt[1] * new_h + y_off for pt in kps if pt is not None]
            x_min, x_max = min(xs), max(xs)
            y_min, y_max = min(ys), max(ys)
            # Add margin
            margin = 0.02 * img_size
            x_min = max(0, x_min - margin)
            y_min = max(0, y_min - margin)
            x_max = min(img_size, x_max + margin)
            y_max = min(img_size, y_max + margin)

            cx = max(0, min(1, (x_min + x_max) / 2 / img_size))
            cy = max(0, min(1, (y_min + y_max) / 2 / img_size))
            bw = min(1, (x_max - x_min) / img_size)
            bh = min(1, (y_max - y_min) / img_size)
            # Ensure bbox doesn't extend past image
            if cx - bw / 2 < 0: bw = cx * 2
            if cy - bh / 2 < 0: bh = cy * 2
            if cx + bw / 2 > 1: bw = (1 - cx) * 2
            if cy + bh / 2 > 1: bh = (1 - cy) * 2

            # Write image
            cv2.imwrite(str(img_dir / f"{name}.jpg"), img_out)

            # Final sanity clamp — all coords must be in [0, 1]
            clamped_kps = []
            for i, v in enumerate(yolo_kps):
                if i % 3 == 2:  # visibility flag
                    clamped_kps.append(v)
                else:
                    clamped_kps.append(max(0.0, min(1.0, v)))

            # Write label: class cx cy w h kp1_x kp1_y kp1_v kp2_x kp2_y kp2_v ...
            kps_str = " ".join(f"{v:.6f}" for v in clamped_kps)
            with open(lbl_dir / f"{name}.txt", "w") as f:
                f.write(f"0 {cx:.6f} {cy:.6f} {bw:.6f} {bh:.6f} {kps_str}\n")

        print(f"  {split_name}: {len(split_samples)} samples")

    # Write dataset.yaml
    yaml_content = f"""path: {output_dir.as_posix()}
train: train/images
val: val/images

names:
  0: table

kpt_shape: [{NUM_KEYPOINTS}, 3]
"""
    with open(output_dir / "dataset.yaml", "w") as f:
        f.write(yaml_content)

    print(f"\n  Dataset written to {output_dir}")
    print(f"  Total: {len(samples)} samples ({splits['train'].__len__()} train, {splits['val'].__len__()} val)")
    print(f"  Config: {output_dir / 'dataset.yaml'}")


# ── Main ─────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="Prepare table keypoint dataset")
    parser.add_argument("--local-only", action="store_true", help="Only use local labeled videos")
    parser.add_argument("--tthq-only", action="store_true", help="Only use TTHQ data")
    parser.add_argument("--all", action="store_true", help="Use local + TTHQ data")
    parser.add_argument("--skip-download", action="store_true", help="Skip TTHQ video downloads")
    parser.add_argument("--output", type=str, default=str(OUTPUT_DIR))
    parser.add_argument("--img-size", type=int, default=IMG_SIZE)
    args = parser.parse_args()

    if not args.local_only and not args.all and not args.tthq_only:
        args.local_only = True  # default

    output = Path(args.output)
    all_samples = []

    # 1. Local videos
    if not args.tthq_only:
        print("\n=== Local Videos ===")
        local_samples = extract_local_frames()
        all_samples.extend(local_samples)

    # 2. TTHQ (optional)
    if args.all or args.tthq_only:
        print("\n=== TTHQ Dataset ===")
        ensure_dir(TTHQ_DIR)

        # Look for annotations
        ann_dir = TTHQ_DIR / "tthq_annotations"
        if not ann_dir.exists():
            ann_dir = TTHQ_DIR / "annotations"
        if not ann_dir.exists() or not list(ann_dir.glob("*.csv")):
            print("  Download tthq_annotation.zip from:")
            print("  https://mediastore.rz.uni-augsburg.de/get/E6idNDRk20/")
            print(f"  Extract to: {TTHQ_DIR}")
            print("  Then re-run this script.")
        else:
            annotations = load_tthq_annotations(ann_dir)

            if not args.skip_download:
                print("\n  Downloading TTHQ videos...")
                download_tthq_videos(TTHQ_DIR)

            tthq_samples = extract_tthq_frames(TTHQ_DIR, annotations)
            all_samples.extend(tthq_samples)

    if not all_samples:
        print("\nNo samples found! Label some frames in poses_viewer first.")
        sys.exit(1)

    # 3. Write dataset
    print(f"\n=== Writing Dataset ({len(all_samples)} samples) ===")
    write_yolo_dataset(all_samples, output, args.img_size)


if __name__ == "__main__":
    main()
