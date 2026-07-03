#!/usr/bin/env python3
"""
filter_contacts_by_pose.py

Filters *_contacts.json to keep only contacts where pose data shows
fast hand/wrist movement nearby (likely a racket swing).

Usage:
    python scripts/filter_contacts_by_pose.py <video_dir>
    python scripts/filter_contacts_by_pose.py <video_dir> --window 5 --threshold 0.03

How it works:
    1. Loads *_poses.json and *_contacts.json from the video directory
    2. Computes wrist velocity between all consecutive pose-frame pairs
    3. For each contact, checks if any fast wrist movement exists within ±window frames
    4. Keeps contacts near fast hand movement; drops the rest
    5. Overwrites *_contacts.json with filtered results (saves backup as *_contacts_unfiltered.json)

Requirements:
    Both *_poses.json and *_contacts.json must exist in the video directory.
"""

import argparse
import json
import math
import os
import shutil
import sys

# MediaPipe landmark indices
LEFT_WRIST = 15
RIGHT_WRIST = 16
LEFT_INDEX = 19
RIGHT_INDEX = 20

# Default parameters
DEFAULT_WINDOW = 5          # ±frames to look for fast movement around a contact
DEFAULT_VEL_THRESHOLD = 0.03  # normalized units per frame (wrist displacement)


def load_json(path: str) -> dict:
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def get_landmark(landmarks: list[dict], idx: int) -> dict | None:
    """Get landmark by index from a landmarks list."""
    for lm in landmarks:
        if lm["index"] == idx:
            return lm
    return None


def compute_wrist_velocities(poses_data: dict) -> list[dict]:
    """Compute wrist velocity between consecutive frames with pose data.

    Returns list of dicts with:
        frameStart, frameEnd, velocity (max of L/R wrist), side ('L'/'R')
    """
    frames_with_pose = [
        (fr["frameIndex"], fr["landmarks"])
        for fr in poses_data["frames"]
        if fr.get("landmarks")
    ]

    velocities = []
    for i in range(1, len(frames_with_pose)):
        fi_prev, lm_prev = frames_with_pose[i - 1]
        fi_curr, lm_curr = frames_with_pose[i]
        gap = fi_curr - fi_prev

        if gap > 10 or gap < 1:
            continue

        best_vel = 0.0
        best_side = "?"

        for wrist_idx, name in [(LEFT_WRIST, "L"), (RIGHT_WRIST, "R"),
                                 (LEFT_INDEX, "L"), (RIGHT_INDEX, "R")]:
            prev = get_landmark(lm_prev, wrist_idx)
            curr = get_landmark(lm_curr, wrist_idx)
            if not prev or not curr:
                continue
            if prev.get("visibility", 0) < 0.5 or curr.get("visibility", 0) < 0.5:
                continue

            dx = curr["x"] - prev["x"]
            dy = curr["y"] - prev["y"]
            dist = math.sqrt(dx * dx + dy * dy)
            vel = dist / gap  # per frame

            if vel > best_vel:
                best_vel = vel
                best_side = name

        if best_vel > 0:
            velocities.append({
                "frameStart": fi_prev,
                "frameEnd": fi_curr,
                "velocity": best_vel,
                "side": best_side,
            })

    return velocities


def filter_contacts(
    contacts_data: dict,
    velocities: list[dict],
    window: int,
    threshold: float,
) -> tuple[list[dict], list[dict]]:
    """Keep contacts near fast hand movement; drop only where poses exist but hand is slow.

    Conservative approach: if no pose velocity data exists near a contact,
    the contact is KEPT (benefit of the doubt). Only dropped when pose data
    actively shows no fast hand movement.
    """
    kept = []
    dropped = []

    for contact in contacts_data["contacts"]:
        fi = contact["frameIndex"]

        # Find all velocity measurements near this contact
        nearby_vels = [
            v for v in velocities
            if fi >= (v["frameStart"] - window) and fi <= (v["frameEnd"] + window)
        ]

        if not nearby_vels:
            # No pose data near this contact — keep it (conservative)
            kept.append(contact)
            continue

        # Pose data exists nearby — check if any shows fast hand movement
        best_vel = max(v["velocity"] for v in nearby_vels)
        if best_vel >= threshold:
            kept.append(contact)
        else:
            dropped.append(contact)

    return kept, dropped


def main():
    parser = argparse.ArgumentParser(
        description="Filter contacts by pose-based hand movement"
    )
    parser.add_argument("video_dir", help="Path to video directory (e.g., .../Videos/IMG_6370)")
    parser.add_argument(
        "--window", type=int, default=DEFAULT_WINDOW,
        help=f"Frame window ± around contact to check for movement (default: {DEFAULT_WINDOW})",
    )
    parser.add_argument(
        "--threshold", type=float, default=DEFAULT_VEL_THRESHOLD,
        help=f"Min wrist velocity per frame to count as swing (default: {DEFAULT_VEL_THRESHOLD})",
    )
    parser.add_argument(
        "--dry-run", action="store_true",
        help="Show what would be filtered without modifying files",
    )
    args = parser.parse_args()

    video_dir = os.path.abspath(args.video_dir)
    base = os.path.basename(video_dir)

    poses_path = os.path.join(video_dir, f"{base}_poses.json")
    contacts_path = os.path.join(video_dir, f"{base}_contacts.json")

    if not os.path.isfile(poses_path):
        print(f"ERROR: No poses file: {poses_path}", file=sys.stderr)
        sys.exit(1)
    if not os.path.isfile(contacts_path):
        print(f"ERROR: No contacts file: {contacts_path}", file=sys.stderr)
        sys.exit(1)

    poses_data = load_json(poses_path)
    contacts_data = load_json(contacts_path)

    n_pose_frames = sum(1 for fr in poses_data["frames"] if fr.get("landmarks"))
    total_frames = poses_data["totalFrames"]
    print(f"Video: {base}")
    print(f"  Pose coverage: {n_pose_frames}/{total_frames} frames ({100*n_pose_frames/total_frames:.0f}%)")
    print(f"  Contacts before filter: {len(contacts_data['contacts'])}")

    # Compute velocities
    velocities = compute_wrist_velocities(poses_data)
    fast_count = sum(1 for v in velocities if v["velocity"] >= args.threshold)
    print(f"  Wrist velocity pairs: {len(velocities)} total, {fast_count} above threshold ({args.threshold})")

    # Filter
    kept, dropped = filter_contacts(contacts_data, velocities, args.window, args.threshold)
    print(f"  Contacts after filter: {len(kept)} kept, {len(dropped)} dropped")
    print()

    # Show kept contacts
    print(f"=== KEPT ({len(kept)}) ===")
    for c in kept:
        print(f"  frame={c['frameIndex']:3d}  t={c['timestampMs']:5d}ms  conf={c['confidence']:.2f}  {c['type']}")

    if dropped:
        print(f"\n=== DROPPED ({len(dropped)}) ===")
        for c in dropped:
            print(f"  frame={c['frameIndex']:3d}  t={c['timestampMs']:5d}ms  conf={c['confidence']:.2f}  {c['type']}")

    if args.dry_run:
        print("\n[DRY RUN — no files modified]")
        return

    # Save backup and write filtered contacts
    backup_path = os.path.join(video_dir, f"{base}_contacts_unfiltered.json")
    shutil.copy2(contacts_path, backup_path)
    print(f"\n  Backup: {backup_path}")

    contacts_data["contacts"] = kept
    contacts_data["filterInfo"] = {
        "method": "pose_wrist_velocity",
        "window": args.window,
        "threshold": args.threshold,
        "keptCount": len(kept),
        "droppedCount": len(dropped),
    }

    with open(contacts_path, "w", encoding="utf-8") as f:
        json.dump(contacts_data, f, indent=2)

    print(f"  Written: {contacts_path} ({len(kept)} contacts)")


if __name__ == "__main__":
    main()
