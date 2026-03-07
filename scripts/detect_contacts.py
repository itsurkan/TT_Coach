#!/usr/bin/env python3
"""
detect_contacts.py

Detects ball-table contact events from the audio track of a table tennis video.

Usage:
    python scripts/detect_contacts.py <video_path> [--interval 100] [--sensitivity medium] [--out-dir <dir>]

Examples:
    python scripts/detect_contacts.py app/src/main/assets/Videos/forehand_drive/forehand_drive.mp4
    python scripts/detect_contacts.py my_video.mp4 --interval 33 --sensitivity high

Output:
    <video_name>_contacts.json  written next to the video (or to --out-dir)

Requirements:
    pip install librosa soundfile numpy
"""

import argparse
import json
import os
import sys
import time
from pathlib import Path

import numpy as np

try:
    import librosa
except ImportError:
    print("ERROR: librosa is required. Install with: pip install librosa soundfile", file=sys.stderr)
    sys.exit(1)


# ── Sensitivity presets ──────────────────────────────────────────────────────
# delta = minimum onset strength to count as a peak (lower = more sensitive)
SENSITIVITY = {
    "low":    {"delta": 0.15, "energy_percentile": 70},
    "medium": {"delta": 0.08, "energy_percentile": 50},
    "high":   {"delta": 0.04, "energy_percentile": 30},
}

SAMPLE_RATE = 22050
HOP_LENGTH = 512          # ~23 ms per hop at 22050 Hz
FMIN = 1000               # ball-table transient lower bound (Hz)
FMAX = 10000              # ball-table transient upper bound (Hz)
MIN_GAP_FRAMES = 3        # ~70 ms minimum gap between contacts (physical limit)
SHARPNESS_WINDOW_SHORT = 5   # ms  — energy window for the transient
SHARPNESS_WINDOW_LONG = 50   # ms  — energy window for background
SHARPNESS_THRESHOLD = 0.15   # minimum ratio to keep


def extract_audio(video_path: str) -> tuple[np.ndarray, int]:
    """Load mono audio from a video file at SAMPLE_RATE."""
    try:
        y, sr = librosa.load(video_path, sr=SAMPLE_RATE, mono=True)
    except Exception as e:
        print(f"ERROR: Could not load audio from {video_path}: {e}", file=sys.stderr)
        sys.exit(1)

    if len(y) == 0:
        print(f"ERROR: Video has no audio track or audio is empty: {video_path}", file=sys.stderr)
        sys.exit(1)

    return y, sr


def detect_onsets(y: np.ndarray, sr: int, sensitivity: str) -> list[dict]:
    """
    Detect sharp transient onsets in the audio signal.
    Returns list of {timestampMs, confidence, onsetStrength}.
    """
    params = SENSITIVITY[sensitivity]

    # Compute mel spectrogram focused on ball-hit frequency range
    S = librosa.feature.melspectrogram(
        y=y, sr=sr, hop_length=HOP_LENGTH,
        fmin=FMIN, fmax=FMAX, n_mels=64,
    )

    # Onset strength envelope
    onset_env = librosa.onset.onset_strength(
        S=librosa.power_to_db(S, ref=np.max),
        sr=sr, hop_length=HOP_LENGTH,
        aggregate=np.median,
    )

    # Pick onsets
    onset_frames = librosa.onset.onset_detect(
        onset_envelope=onset_env,
        sr=sr, hop_length=HOP_LENGTH,
        delta=params["delta"],
        wait=MIN_GAP_FRAMES,
        backtrack=False,
    )

    if len(onset_frames) == 0:
        return []

    # Convert onset frames to sample indices and timestamps
    onset_times = librosa.frames_to_time(onset_frames, sr=sr, hop_length=HOP_LENGTH)
    onset_strengths = onset_env[onset_frames]

    # ── Post-filtering ───────────────────────────────────────────────────
    # 1. Energy threshold: reject onsets with strength below percentile
    if len(onset_strengths) > 1:
        threshold = np.percentile(onset_strengths, params["energy_percentile"])
    else:
        threshold = 0

    # 2. Sharpness ratio: energy in short window vs long window
    short_samples = int(SHARPNESS_WINDOW_SHORT * sr / 1000)
    long_samples = int(SHARPNESS_WINDOW_LONG * sr / 1000)

    results = []
    max_strength = float(np.max(onset_strengths)) if len(onset_strengths) > 0 else 1.0

    for i, (frame_idx, t, strength) in enumerate(zip(onset_frames, onset_times, onset_strengths)):
        # Skip weak onsets
        if strength < threshold:
            continue

        # Compute sharpness ratio
        sample_center = int(t * sr)
        short_start = max(0, sample_center - short_samples // 2)
        short_end = min(len(y), sample_center + short_samples // 2)
        long_start = max(0, sample_center - long_samples // 2)
        long_end = min(len(y), sample_center + long_samples // 2)

        short_energy = np.mean(y[short_start:short_end] ** 2) if short_end > short_start else 0
        long_energy = np.mean(y[long_start:long_end] ** 2) if long_end > long_start else 1e-10

        sharpness = short_energy / max(long_energy, 1e-10)

        if sharpness < SHARPNESS_THRESHOLD:
            continue

        confidence = float(strength / max_strength) if max_strength > 0 else 0.0

        results.append({
            "timestampMs": round(t * 1000),
            "confidence": round(confidence, 3),
            "onsetStrength": round(float(strength), 4),
        })

    return results


def map_to_frames(onsets: list[dict], interval_ms: int, duration_ms: int) -> list[dict]:
    """Add frameIndex to each onset based on intervalMs. Filter out-of-range."""
    total_frames = int(duration_ms / interval_ms) + 1
    contacts = []
    for o in onsets:
        fi = round(o["timestampMs"] / interval_ms)
        if 0 <= fi < total_frames:
            contacts.append({
                "frameIndex": fi,
                "timestampMs": o["timestampMs"],
                "confidence": o["confidence"],
                "type": "table",
            })
    return contacts


def get_video_duration_ms(video_path: str) -> int:
    """Get video duration in ms using librosa."""
    duration = librosa.get_duration(path=video_path)
    return round(duration * 1000)


def export_contacts(
    video_path: str,
    interval_ms: int,
    out_dir: str | None,
    sensitivity: str,
) -> str:
    """Main pipeline: extract audio → detect onsets → map to frames → write JSON."""
    video_path = os.path.abspath(video_path)
    video_name = os.path.basename(video_path)
    base_name = os.path.splitext(video_name)[0]

    if out_dir is None:
        out_dir = os.path.dirname(video_path)
    os.makedirs(out_dir, exist_ok=True)

    print(f"Loading audio from: {video_path}")
    y, sr = extract_audio(video_path)
    duration_ms = round(len(y) / sr * 1000)
    print(f"  Audio: {duration_ms}ms, {sr}Hz, {len(y)} samples")

    print(f"Detecting contacts (sensitivity={sensitivity})...")
    onsets = detect_onsets(y, sr, sensitivity)
    print(f"  Raw onsets: {len(onsets)}")

    total_frames = int(duration_ms / interval_ms) + 1
    contacts = map_to_frames(onsets, interval_ms, duration_ms)
    print(f"  Contacts mapped to frames: {len(contacts)}")

    # Deduplicate: if multiple onsets map to the same frame, keep highest confidence
    seen: dict[int, dict] = {}
    for c in contacts:
        fi = c["frameIndex"]
        if fi not in seen or c["confidence"] > seen[fi]["confidence"]:
            seen[fi] = c
    contacts = sorted(seen.values(), key=lambda c: c["frameIndex"])
    print(f"  After dedup: {len(contacts)}")

    output = {
        "videoName": video_name,
        "intervalMs": interval_ms,
        "videoDurationMs": duration_ms,
        "totalFrames": total_frames,
        "audioSampleRate": sr,
        "sensitivity": sensitivity,
        "exportTimestamp": int(time.time() * 1000),
        "contacts": contacts,
    }

    out_path = os.path.join(out_dir, f"{base_name}_contacts.json")
    with open(out_path, "w") as f:
        json.dump(output, f, indent=2)

    print(f"\nWrote {len(contacts)} contacts → {out_path}")

    # Print summary
    for i, c in enumerate(contacts):
        print(f"  #{i+1}  frame={c['frameIndex']:3d}  t={c['timestampMs']:5d}ms  conf={c['confidence']:.2f}")

    return out_path


def main():
    parser = argparse.ArgumentParser(
        description="Detect ball-table contacts from video audio track"
    )
    parser.add_argument("video", help="Path to the video file (.mp4, .mov, etc.)")
    parser.add_argument(
        "--interval", type=int, default=100,
        help="Frame interval in ms (default: 100)",
    )
    parser.add_argument(
        "--sensitivity", choices=["low", "medium", "high"], default="medium",
        help="Detection sensitivity (default: medium)",
    )
    parser.add_argument(
        "--out-dir", default=None,
        help="Output directory (default: same folder as video)",
    )
    args = parser.parse_args()

    if not os.path.isfile(args.video):
        print(f"ERROR: File not found: {args.video}", file=sys.stderr)
        sys.exit(1)

    export_contacts(args.video, args.interval, args.out_dir, args.sensitivity)


if __name__ == "__main__":
    main()
