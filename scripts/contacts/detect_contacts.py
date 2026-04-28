#!/usr/bin/env python3
"""
detect_contacts.py

Detects ball-table contact events from the audio track of a table tennis video.

Usage:
    python scripts/detect_contacts.py <video_path> [--interval 100] [--sensitivity medium] [--out-dir <dir>]

Examples:
    python scripts/contacts/detect_contacts.py Videos/forehand_drive/forehand_drive.mp4
    python scripts/detect_contacts.py my_video.mp4 --interval 33 --sensitivity high

Output:
    <video_name>_contacts.json  written next to the video (or to --out-dir)

Requirements:
    pip install librosa soundfile numpy
"""

import argparse
import json
import os
import subprocess
import sys
import tempfile
import time
from pathlib import Path

import numpy as np

try:
    import librosa
except ImportError:
    print("ERROR: librosa is required. Install with: pip install librosa soundfile", file=sys.stderr)
    sys.exit(1)


def _find_ffmpeg() -> str | None:
    """Try to find an ffmpeg binary (system PATH or imageio-ffmpeg bundled)."""
    import shutil
    path = shutil.which("ffmpeg")
    if path:
        return path
    try:
        import imageio_ffmpeg
        return imageio_ffmpeg.get_ffmpeg_exe()
    except ImportError:
        return None


# ── Sensitivity presets ──────────────────────────────────────────────────────
# delta = minimum onset strength to count as a peak (lower = more sensitive)
SENSITIVITY = {
    "low":    {"delta": 0.15, "energy_percentile": 70},
    "medium": {"delta": 0.08, "energy_percentile": 40},
    "high":   {"delta": 0.04, "energy_percentile": 0},
}

SAMPLE_RATE = 22050
HOP_LENGTH = 512          # ~23 ms per hop at 22050 Hz
FMIN = 1000               # ball-table transient lower bound (Hz)
FMAX = 10000              # ball-table transient upper bound (Hz)
MIN_GAP_FRAMES = 3        # ~70 ms minimum gap between contacts (physical limit)
SHARPNESS_WINDOW_SHORT = 5   # ms  — energy window for the transient
SHARPNESS_WINDOW_LONG = 50   # ms  — energy window for background
SHARPNESS_THRESHOLD = 0.15   # minimum ratio to keep
AUDIO_OFFSET_MS = -25         # ms — small shift earlier (backtrack slightly overshoots)
PRE_SILENCE_WINDOW_MS = 100   # ms — window to check for silence before onset
PRE_SILENCE_THRESHOLD = 1e-5  # if mean energy before onset is below this, it's recording start noise

# ── Contact type classification ──────────────────────────────────────────────
# Table hits: lower spectral centroid, more energy in 1-3 kHz (table resonance)
# Racket hits: higher spectral centroid, more energy in 3-8 kHz (rubber/sponge snap)
CLASSIFY_WINDOW_MS = 15       # ms — window around onset for spectral analysis
CLASSIFY_LOW_BAND = (800, 3000)    # Hz — table resonance range
CLASSIFY_HIGH_BAND = (3000, 8000)  # Hz — racket snap range


def extract_audio(video_path: str) -> tuple[np.ndarray, int]:
    """Load mono audio from a video file at SAMPLE_RATE.

    First tries librosa directly; falls back to extracting via ffmpeg to a temp WAV.
    """
    # Try direct load first
    try:
        import warnings
        with warnings.catch_warnings():
            warnings.simplefilter("ignore")
            y, sr = librosa.load(video_path, sr=SAMPLE_RATE, mono=True)
        if len(y) > 0:
            return y, sr
    except Exception:
        pass

    # Fallback: extract audio via ffmpeg to temp WAV, then load
    ffmpeg = _find_ffmpeg()
    if not ffmpeg:
        print("ERROR: Cannot load audio. Install ffmpeg or: pip install imageio[ffmpeg]", file=sys.stderr)
        sys.exit(1)

    print(f"  Extracting audio via ffmpeg...")
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        tmp_path = tmp.name

    try:
        result = subprocess.run(
            [ffmpeg, "-i", video_path, "-vn", "-acodec", "pcm_s16le",
             "-ar", str(SAMPLE_RATE), "-ac", "1", "-y", tmp_path],
            capture_output=True, text=True, timeout=60,
        )
        if result.returncode != 0:
            print(f"ERROR: ffmpeg failed: {result.stderr[:500]}", file=sys.stderr)
            sys.exit(1)

        y, sr = librosa.load(tmp_path, sr=SAMPLE_RATE, mono=True)
    finally:
        try:
            os.unlink(tmp_path)
        except OSError:
            pass

    if len(y) == 0:
        print(f"ERROR: Video has no audio track or audio is empty: {video_path}", file=sys.stderr)
        sys.exit(1)

    return y, sr


def classify_contact(y: np.ndarray, sr: int, center_sample: int) -> dict:
    """Classify a contact as 'table' or 'racket' based on spectral features.

    Table hits produce a lower-pitched "tok" with energy concentrated in 1-3 kHz
    (table surface resonance). Racket hits produce a higher "pop"/"thwack" with
    more energy in 3-8 kHz (rubber/sponge).

    Returns dict with 'type', 'spectralCentroid', 'lowHighRatio'.
    """
    window_samples = int(CLASSIFY_WINDOW_MS * sr / 1000)
    start = max(0, center_sample - window_samples // 2)
    end = min(len(y), center_sample + window_samples // 2)
    segment = y[start:end]

    if len(segment) < 16:
        return {"type": "unknown", "spectralCentroid": 0.0, "lowHighRatio": 0.0}

    # Compute FFT magnitude spectrum
    n_fft = max(256, len(segment))
    fft_mag = np.abs(np.fft.rfft(segment, n=n_fft))
    freqs = np.fft.rfftfreq(n_fft, d=1.0 / sr)

    # Spectral centroid
    total_energy = np.sum(fft_mag)
    if total_energy < 1e-10:
        return {"type": "unknown", "spectralCentroid": 0.0, "lowHighRatio": 0.0}
    centroid = float(np.sum(freqs * fft_mag) / total_energy)

    # Band energy ratio: low (table resonance) vs high (racket snap)
    low_mask = (freqs >= CLASSIFY_LOW_BAND[0]) & (freqs <= CLASSIFY_LOW_BAND[1])
    high_mask = (freqs >= CLASSIFY_HIGH_BAND[0]) & (freqs <= CLASSIFY_HIGH_BAND[1])
    low_energy = float(np.sum(fft_mag[low_mask] ** 2))
    high_energy = float(np.sum(fft_mag[high_mask] ** 2))
    ratio = low_energy / max(high_energy, 1e-10)

    # Classification heuristic using a weighted score:
    # Lower centroid → more table-like; higher lo/hi ratio → more table-like
    # Score > 0 → table, score <= 0 → racket
    centroid_score = (3800 - centroid) / 1000   # positive if centroid < 3800
    ratio_score = (ratio - 2.0) / 5.0           # positive if ratio > 2.0 (more low-band)
    score = centroid_score + ratio_score

    if score > 0:
        contact_type = "table"
    else:
        contact_type = "racket"

    return {
        "type": contact_type,
        "spectralCentroid": round(centroid, 1),
        "lowHighRatio": round(ratio, 3),
    }


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

    # Pick onsets at peak positions (for strength filtering)
    peak_frames = librosa.onset.onset_detect(
        onset_envelope=onset_env,
        sr=sr, hop_length=HOP_LENGTH,
        delta=params["delta"],
        wait=MIN_GAP_FRAMES,
        backtrack=False,
    )

    if len(peak_frames) == 0:
        return []

    # Backtrack to get corrected timing (start of transient, not peak)
    onset_frames = librosa.onset.onset_backtrack(peak_frames, onset_env)

    # Use peak strengths for filtering/confidence, backtracked times for output
    peak_strengths = onset_env[peak_frames]
    onset_times = librosa.frames_to_time(onset_frames, sr=sr, hop_length=HOP_LENGTH)

    # ── Post-filtering ───────────────────────────────────────────────────
    # 1. Energy threshold: reject onsets with strength below percentile
    if len(peak_strengths) > 1:
        threshold = np.percentile(peak_strengths, params["energy_percentile"])
    else:
        threshold = 0

    # 2. Sharpness ratio: energy in short window vs long window
    short_samples = int(SHARPNESS_WINDOW_SHORT * sr / 1000)
    long_samples = int(SHARPNESS_WINDOW_LONG * sr / 1000)

    results = []
    max_strength = float(np.max(peak_strengths)) if len(peak_strengths) > 0 else 1.0

    # Precompute peak times for sharpness and classification (at peak, not backtracked)
    peak_times = librosa.frames_to_time(peak_frames, sr=sr, hop_length=HOP_LENGTH)

    for i, (t_bt, t_peak, strength) in enumerate(zip(onset_times, peak_times, peak_strengths)):
        # Skip weak onsets
        if strength < threshold:
            continue

        # Compute sharpness ratio at the PEAK position (not backtracked)
        sample_center = int(t_peak * sr)
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

        # Classify contact type at peak position (cleaner signal)
        classification = classify_contact(y, sr, sample_center)

        # Skip recording-start noise: if the audio before the onset is silent,
        # this is likely a mic power-on transient, not a ball contact
        pre_window = int(PRE_SILENCE_WINDOW_MS * sr / 1000)
        pre_start = max(0, sample_center - pre_window)
        pre_end = max(0, sample_center - short_samples)  # exclude the transient itself
        if pre_end > pre_start:
            pre_energy = np.mean(y[pre_start:pre_end] ** 2)
            if pre_energy < PRE_SILENCE_THRESHOLD:
                continue

        # Apply A/V sync offset and clamp to >= 0
        adjusted_ms = max(0, round(t_bt * 1000) + AUDIO_OFFSET_MS)

        results.append({
            "timestampMs": adjusted_ms,
            "confidence": round(confidence, 3),
            "onsetStrength": round(float(strength), 4),
            "type": classification["type"],
            "spectralCentroid": classification["spectralCentroid"],
            "lowHighRatio": classification["lowHighRatio"],
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
                "type": o.get("type", "unknown"),
                "spectralCentroid": o.get("spectralCentroid", 0.0),
                "lowHighRatio": o.get("lowHighRatio", 0.0),
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

    print(f"\nWrote {len(contacts)} contacts -> {out_path}")

    # Print summary
    for i, c in enumerate(contacts):
        print(f"  #{i+1}  frame={c['frameIndex']:3d}  t={c['timestampMs']:5d}ms  conf={c['confidence']:.2f}  {c['type']:6s}  centroid={c['spectralCentroid']:.0f}Hz  lo/hi={c['lowHighRatio']:.2f}")

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
