#!/usr/bin/env python3
"""Render a schema-v2 pose JSON as a skeleton overlay PNG.

Reads a *_poses_rtm.json (COCO-17 or Halpe26), draws the skeleton over the
matching video frame (grabbed from the sibling .mp4 by timestamp) or over a
blank canvas when no video is found, and writes a PNG you can open / Read.

Usage:
    .venv/bin/python .claude/skills/visualize-pose/render_pose.py <pose.json> [options]

Options:
    --frame N      Render only the pose frame whose frameIndex == N.
    --index I      Render the I-th frame in the file (0-based). Default: 0.
    --all          Render every frame to <stem>_frame<idx>.png.
    --out PATH     Output PNG path (single-frame only). Default: next to JSON.
    --video PATH   Override the source video (default: sibling .mp4).
    --no-video     Skip the video; draw on a blank dark canvas.
    --min-score F  Confidence gate; joints/bones below this are skipped (default 0.3).

No deps beyond the repo .venv (cv2, numpy already installed).
"""
import argparse
import json
import os
import sys

import cv2
import numpy as np

# COCO-17 index -> name (Halpe26 shares 0-16, adds 17-25). See docs/pose_json_schema_v2.md.
COCO17_NAMES = [
    "nose", "left_eye", "right_eye", "left_ear", "right_ear",
    "left_shoulder", "right_shoulder", "left_elbow", "right_elbow",
    "left_wrist", "right_wrist", "left_hip", "right_hip",
    "left_knee", "right_knee", "left_ankle", "right_ankle",
]

# (a, b, side) bone pairs. side colours: left, right, center.
COCO17_CONNECTIONS = [
    (0, 1, "left"), (0, 2, "right"), (1, 3, "left"), (2, 4, "right"),   # face
    (5, 6, "center"), (5, 11, "left"), (6, 12, "right"), (11, 12, "center"),  # torso
    (5, 7, "left"), (7, 9, "left"),    # left arm
    (6, 8, "right"), (8, 10, "right"), # right arm
    (11, 13, "left"), (13, 15, "left"),    # left leg
    (12, 14, "right"), (14, 16, "right"),  # right leg
]
HALPE26_FOOT = [
    (15, 24, "left"), (24, 20, "left"), (20, 22, "left"),    # left foot
    (16, 25, "right"), (25, 21, "right"), (21, 23, "right"),  # right foot
]

# BGR (OpenCV order). Mirrors poses_viewer RTM palette intent: warm right, cool left.
SIDE_BGR = {"left": (246, 130, 59), "right": (68, 68, 239), "center": (94, 197, 34)}
JOINT_BGR = (21, 199, 250)  # amber/yellow


def connections(topology):
    if topology == "halpe26":
        return COCO17_CONNECTIONS + HALPE26_FOOT
    return COCO17_CONNECTIONS


def grab_video_frame(video_path, timestamp_ms):
    """Return the BGR frame nearest timestamp_ms, or None on failure."""
    cap = cv2.VideoCapture(video_path)
    if not cap.isOpened():
        return None
    cap.set(cv2.CAP_PROP_POS_MSEC, float(timestamp_ms))
    ok, frame = cap.read()
    cap.release()
    return frame if ok else None


def render_frame(doc, frame, conns, min_score, video_path, no_video):
    w, h = doc["videoWidth"], doc["videoHeight"]
    lms = {lm["index"]: lm for lm in frame.get("landmarks", [])}

    canvas = None
    if not no_video and video_path and os.path.exists(video_path):
        vf = grab_video_frame(video_path, frame.get("timestampMs", 0))
        if vf is not None:
            canvas = cv2.resize(vf, (w, h))
    if canvas is None:
        canvas = np.full((h, w, 3), (40, 23, 15), np.uint8)  # dark slate (#0f172a)

    def px(lm):
        return int(round(lm["x"] * w)), int(round(lm["y"] * h))

    # bones first, joints on top
    for a, b, side in conns:
        la, lb = lms.get(a), lms.get(b)
        if not la or not lb or la["score"] < min_score or lb["score"] < min_score:
            continue
        cv2.line(canvas, px(la), px(lb), SIDE_BGR[side], 3, cv2.LINE_AA)
    drawn = {i for a, b, _ in conns for i in (a, b)}
    for i in drawn:
        lm = lms.get(i)
        if lm and lm["score"] >= min_score:
            cv2.circle(canvas, px(lm), 4, JOINT_BGR, -1, cv2.LINE_AA)
    return canvas


def main():
    ap = argparse.ArgumentParser(description="Render schema-v2 pose JSON to a skeleton PNG.")
    ap.add_argument("pose_json")
    ap.add_argument("--frame", type=int, help="render pose frame with this frameIndex")
    ap.add_argument("--index", type=int, default=0, help="0-based frame position (default 0)")
    ap.add_argument("--all", action="store_true", help="render every frame")
    ap.add_argument("--out", help="output PNG (single-frame only)")
    ap.add_argument("--video", help="source video (default: sibling .mp4)")
    ap.add_argument("--no-video", action="store_true", help="blank canvas, ignore video")
    ap.add_argument("--min-score", type=float, default=0.3)
    args = ap.parse_args()

    with open(args.pose_json) as f:
        doc = json.load(f)
    if doc.get("schemaVersion") != 2:
        sys.exit(f"Not schema v2 (got schemaVersion={doc.get('schemaVersion')}). "
                 "This renderer is for *_poses_rtm.json only.")

    topology = doc.get("topology", "coco17")
    conns = connections(topology)
    frames = doc["frames"]

    # locate the video
    video_path = args.video
    if video_path is None and not args.no_video:
        guess = os.path.join(os.path.dirname(os.path.abspath(args.pose_json)),
                             f"{doc.get('videoName', '')}")
        video_path = guess if os.path.exists(guess) else None

    stem = os.path.splitext(os.path.abspath(args.pose_json))[0]

    if args.all:
        for i, fr in enumerate(frames):
            img = render_frame(doc, fr, conns, args.min_score, video_path, args.no_video)
            out = f"{stem}_frame{fr.get('frameIndex', i)}.png"
            cv2.imwrite(out, img)
            print(out)
        return

    if args.frame is not None:
        sel = next((fr for fr in frames if fr.get("frameIndex") == args.frame), None)
        if sel is None:
            sys.exit(f"No frame with frameIndex={args.frame}")
    else:
        if args.index >= len(frames):
            sys.exit(f"--index {args.index} out of range ({len(frames)} frames)")
        sel = frames[args.index]

    img = render_frame(doc, sel, conns, args.min_score, video_path, args.no_video)
    out = args.out or f"{stem}_render.png"
    cv2.imwrite(out, img)
    n_kp = len(sel.get("landmarks", []))
    src = "video frame" if (video_path and not args.no_video) else "blank canvas"
    print(f"{out}  ({topology}, {n_kp} keypoints, frameIndex={sel.get('frameIndex')}, on {src})")


if __name__ == "__main__":
    main()
