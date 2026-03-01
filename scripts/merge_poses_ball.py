#!/usr/bin/env python3
"""
merge_poses_ball.py

Merges *_poses.json + *_ball.json → *_poses_ball.json for every video
in app/src/main/assets/Videos/.

Output schema (matches pose_viewer expectations):
{
  "videoUri":        <from poses JSON>,
  "videoName":       <from ball JSON>,
  "intervalMs":      <from poses JSON>,
  "totalFrames":     <from poses JSON>,
  "videoDurationMs": <from ball JSON>,
  "videoWidth":      <from ball JSON>,
  "videoHeight":     <from ball JSON>,
  "exportTimestamp": <current time>,
  "frames": [
    {
      "frameIndex":  <int>,
      "timestampMs": <int>,
      "landmarks":   <33 MediaPipe landmarks, from poses JSON>,
      "ball":        <BallDetection or null, from ball JSON>
    },
    ...
  ]
}
"""

import json
import os
import sys
import time

VIDEOS_DIR = os.path.join(
    os.path.dirname(__file__),
    "..", "app", "src", "main", "assets", "Videos"
)


def find_pairs(videos_dir: str) -> list[tuple[str, str]]:
    """Return [(poses_path, ball_path), ...] for every matched pair."""
    files = os.listdir(videos_dir)
    poses_files = [f for f in files if f.endswith("_poses.json")]
    pairs = []
    for poses_file in sorted(poses_files):
        base = poses_file[: -len("_poses.json")]
        ball_file = base + "_ball.json"
        if ball_file in files:
            pairs.append((
                os.path.join(videos_dir, poses_file),
                os.path.join(videos_dir, ball_file),
            ))
        else:
            print(f"  WARNING: no ball file for {poses_file}, skipping")
    return pairs


def merge(poses_path: str, ball_path: str) -> dict:
    with open(poses_path, encoding="utf-8") as f:
        poses = json.load(f)
    with open(ball_path, encoding="utf-8") as f:
        ball = json.load(f)

    # Index ball frames by timestampMs for O(1) lookup
    ball_by_ts: dict[int, dict | None] = {}
    for frame in ball.get("frames", []):
        ts = frame["timestampMs"]
        ball_by_ts[ts] = frame.get("ball")  # may be None

    merged_frames = []
    for pose_frame in poses.get("frames", []):
        ts = pose_frame["timestampMs"]
        merged_frames.append({
            "frameIndex": pose_frame["frameIndex"],
            "timestampMs": ts,
            "landmarks": pose_frame.get("landmarks", []),
            "ball": ball_by_ts.get(ts),  # None if timestamp not in ball file
        })

    return {
        "videoUri":        poses.get("videoUri"),
        "videoName":       ball.get("videoName"),
        "intervalMs":      poses.get("intervalMs", ball.get("intervalMs")),
        "totalFrames":     poses.get("totalFrames", ball.get("totalFrames")),
        "videoDurationMs": ball.get("videoDurationMs"),
        "videoWidth":      ball.get("videoWidth"),
        "videoHeight":     ball.get("videoHeight"),
        "exportTimestamp": int(time.time() * 1000),
        "frames":          merged_frames,
    }


def main():
    videos_dir = os.path.normpath(VIDEOS_DIR)
    if not os.path.isdir(videos_dir):
        print(f"ERROR: directory not found: {videos_dir}", file=sys.stderr)
        sys.exit(1)

    pairs = find_pairs(videos_dir)
    if not pairs:
        print("No *_poses.json + *_ball.json pairs found.")
        return

    for poses_path, ball_path in pairs:
        base = os.path.basename(poses_path)[: -len("_poses.json")]
        out_path = os.path.join(videos_dir, base + "_poses_ball.json")

        print(f"Merging {base}...")
        merged = merge(poses_path, ball_path)

        with open(out_path, "w", encoding="utf-8") as f:
            json.dump(merged, f, indent=2)

        n_frames = len(merged["frames"])
        n_detected = sum(1 for fr in merged["frames"] if fr["ball"] is not None)
        print(f"  -> {out_path}")
        print(f"     {n_frames} frames, {n_detected} with ball detected")

    print("\nDone.")


if __name__ == "__main__":
    main()
