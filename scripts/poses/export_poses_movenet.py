#!/usr/bin/env python3
"""
export_poses_movenet.py

Runs MoveNet Thunder (TFLite, single-pose, COCO-17) on a video file and
exports a *_poses_movenet.json — pose JSON schema v2. Written as an
independently-verified reference implementation to compare against the
Android/Kotlin MoveNet decode prototype (which produced garbage keypoints
from unverified letterbox math).

Schema reference: docs/pose_json_schema_v2.md

Usage:
    python scripts/poses/export_poses_movenet.py <video_path> [--interval 100] [--out-dir <dir>]

Output:
    <video_name>_poses_movenet.json  written next to the video (or to --out-dir)

Requirements:
    pip install opencv-python ai-edge-litert numpy

Model:
    Reuses app/src/main/assets/movenet_thunder.tflite (uint8 input, 256x256x3;
    float32 output [1,1,17,3] = (y, x, score) per keypoint, normalized to the
    model's own padded-square 256x256 input — NOT the original frame).
"""

import argparse
import json
import os
import sys
import time

try:
    import cv2
except ImportError:
    print("ERROR: opencv-python not installed. Run: pip install opencv-python", file=sys.stderr)
    sys.exit(1)

try:
    import numpy as np
except ImportError:
    print("ERROR: numpy not installed. Run: pip install numpy", file=sys.stderr)
    sys.exit(1)

try:
    from ai_edge_litert.interpreter import Interpreter
except ImportError:
    print("ERROR: ai-edge-litert not installed. Run: pip install ai-edge-litert", file=sys.stderr)
    sys.exit(1)

SCHEMA_VERSION = 2
MODEL_INPUT_SIZE = 256
DEFAULT_MODEL_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..",
                                   "app", "src", "main", "assets", "movenet_thunder.tflite")


def letterbox_to_square(frame_bgr: "np.ndarray", size: int):
    """
    Aspect-preserving resize + pad (letterbox) of a BGR frame into a
    size x size square, matching the standard MoveNet preprocessing.

    Returns (square_rgb_uint8, scale, pad_x, pad_y) where:
      - scale is the factor applied to the ORIGINAL frame to get the resized
        (pre-pad) content dimensions: resized_w = round(orig_w * scale), etc.
      - pad_x / pad_y are the top-left padding offsets (in square-image pixels)
        added around the resized content to center it in the square canvas.
    """
    h, w = frame_bgr.shape[:2]
    scale = size / max(h, w)
    resized_w = max(1, round(w * scale))
    resized_h = max(1, round(h * scale))
    resized = cv2.resize(frame_bgr, (resized_w, resized_h), interpolation=cv2.INTER_LINEAR)

    canvas = np.zeros((size, size, 3), dtype=np.uint8)
    pad_x = (size - resized_w) // 2
    pad_y = (size - resized_h) // 2
    canvas[pad_y:pad_y + resized_h, pad_x:pad_x + resized_w] = resized

    canvas_rgb = cv2.cvtColor(canvas, cv2.COLOR_BGR2RGB)
    return canvas_rgb, scale, pad_x, pad_y


def decode_keypoints(raw_output: "np.ndarray", scale: float, pad_x: int, pad_y: int,
                      orig_w: int, orig_h: int, size: int = MODEL_INPUT_SIZE):
    """
    raw_output: shape (17, 3), each row = (y_norm, x_norm, score), normalized
    to the padded size x size square input.

    Inverts the letterbox transform to get coords normalized to the ORIGINAL
    decoded frame, per-axis (x / orig_w, y / orig_h) — matching schema v2's
    per-axis-by-original-dimension convention.
    """
    resized_w = max(1, round(orig_w * scale))
    resized_h = max(1, round(orig_h * scale))

    out = []
    for i in range(17):
        y_norm, x_norm, score = raw_output[i]
        # De-normalize to square-canvas pixel coords.
        px = float(x_norm) * size
        py = float(y_norm) * size
        # Remove padding offset -> pixel coords within the resized (pre-pad) content.
        px -= pad_x
        py -= pad_y
        # Un-scale back to original-frame pixel coords.
        px = px / scale if scale != 0 else px
        py = py / scale if scale != 0 else py
        # Normalize per-axis by the ORIGINAL frame dimensions.
        x_frame = min(1.0, max(0.0, px / orig_w))
        y_frame = min(1.0, max(0.0, py / orig_h))
        out.append((x_frame, y_frame, float(score)))
    return out


def export_poses(video_path: str, interval_ms: int, out_dir: str | None,
                  model_path: str = DEFAULT_MODEL_PATH) -> str:
    topology, model_name, num_keypoints = "coco17", "movenet-thunder", 17

    video_name = os.path.basename(video_path)
    base = video_name.rsplit(".", 1)[0]

    cap = cv2.VideoCapture(video_path)
    if not cap.isOpened():
        print(f"ERROR: cannot open video: {video_path}", file=sys.stderr)
        sys.exit(1)

    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    frame_count = cap.get(cv2.CAP_PROP_FRAME_COUNT)
    duration_ms = int(frame_count / fps * 1000)
    # Take dimensions from the DECODED frame, not header props (rotation
    # metadata can swap them) — same rationale as export_poses_rtmpose.py L-08.
    ok, probe = cap.read()
    if not ok:
        print(f"ERROR: cannot decode first frame: {video_path}", file=sys.stderr)
        sys.exit(1)
    height, width = probe.shape[:2]
    header_w = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    header_h = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    if (width, height) != (header_w, header_h):
        print(f"NOTE: rotation metadata applied: header {header_w}x{header_h} -> decoded {width}x{height}")
    cap.set(cv2.CAP_PROP_POS_MSEC, 0)  # rewind after the probe read

    print(f"Video: {video_name}  {width}x{height}  {duration_ms} ms  ({fps:.1f} fps)")

    interpreter = Interpreter(model_path=model_path)
    interpreter.allocate_tensors()
    input_details = interpreter.get_input_details()[0]
    output_details = interpreter.get_output_details()[0]

    frames = []
    frame_index = 0
    pos_ms = 0
    sanity_printed = False

    while pos_ms <= duration_ms:
        cap.set(cv2.CAP_PROP_POS_MSEC, pos_ms)
        ret, frame = cap.read()
        if not ret:
            break

        square_rgb, scale, pad_x, pad_y = letterbox_to_square(frame, MODEL_INPUT_SIZE)
        input_tensor = np.expand_dims(square_rgb, axis=0).astype(input_details["dtype"])

        interpreter.set_tensor(input_details["index"], input_tensor)
        interpreter.invoke()
        raw = interpreter.get_tensor(output_details["index"])  # shape (1, 1, 17, 3)
        raw = raw[0, 0]  # (17, 3) = (y, x, score)

        decoded = decode_keypoints(raw, scale, pad_x, pad_y, width, height)

        if not sanity_printed and any(s > 0.3 for (_, _, s) in decoded):
            # Manual sanity check: nose (0), shoulders (5,6), hips (11,12)
            # should cluster in a plausible body shape, not be scattered.
            nose = decoded[0]
            l_sh, r_sh = decoded[5], decoded[6]
            l_hip, r_hip = decoded[11], decoded[12]
            print(f"  SANITY frame {frame_index} t={pos_ms}ms:")
            print(f"    nose      x={nose[0]:.3f} y={nose[1]:.3f} score={nose[2]:.3f}")
            print(f"    l_shoulder x={l_sh[0]:.3f} y={l_sh[1]:.3f} score={l_sh[2]:.3f}")
            print(f"    r_shoulder x={r_sh[0]:.3f} y={r_sh[1]:.3f} score={r_sh[2]:.3f}")
            print(f"    l_hip     x={l_hip[0]:.3f} y={l_hip[1]:.3f} score={l_hip[2]:.3f}")
            print(f"    r_hip     x={r_hip[0]:.3f} y={r_hip[1]:.3f} score={r_hip[2]:.3f}")
            sanity_printed = True

        landmarks = []
        for i in range(num_keypoints):
            x_frame, y_frame, score = decoded[i]
            landmarks.append({
                "index": i,
                "x": round(x_frame, 4),
                "y": round(y_frame, 4),
                "score": round(min(1.0, max(0.0, score)), 4),
            })

        frames.append({
            "frameIndex": frame_index,
            "timestampMs": pos_ms,
            "landmarks": landmarks,
        })

        if frame_index % 10 == 0:
            print(f"  frame {frame_index:3d}  t={pos_ms:6d} ms  landmarks={len(landmarks)}")

        frame_index += 1
        pos_ms += interval_ms

    cap.release()

    data = {
        "schemaVersion":   SCHEMA_VERSION,
        "topology":        topology,
        "model":           model_name,
        "videoName":       video_name,
        "intervalMs":      interval_ms,
        "totalFrames":     frame_index,
        "videoDurationMs": duration_ms,
        "videoWidth":      width,
        "videoHeight":     height,
        "exportTimestamp": int(time.time() * 1000),
        "frames":          frames,
    }

    dest_dir = out_dir if out_dir else os.path.dirname(os.path.abspath(video_path))
    os.makedirs(dest_dir, exist_ok=True)
    out_path = os.path.join(dest_dir, base + "_poses_movenet.json")

    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, allow_nan=False)

    n_detected = sum(1 for fr in frames if fr["landmarks"])
    print(f"\n-> {out_path}")
    print(f"  {frame_index} frames, {n_detected} with pose detected")
    return out_path


def main():
    parser = argparse.ArgumentParser(description="Export MoveNet Thunder COCO-17 detections from a video (schema v2).")
    parser.add_argument("video", help="Path to the input video file")
    parser.add_argument("--interval", type=int, default=100,
                        help="Sampling interval in milliseconds (default: 100)")
    parser.add_argument("--out-dir", default=None,
                        help="Output directory (default: same folder as video)")
    parser.add_argument("--model", default=DEFAULT_MODEL_PATH,
                        help="Path to movenet_thunder.tflite (default: app/src/main/assets/movenet_thunder.tflite)")
    args = parser.parse_args()

    if not os.path.isfile(args.video):
        print(f"ERROR: file not found: {args.video}", file=sys.stderr)
        sys.exit(1)

    export_poses(args.video, args.interval, args.out_dir, args.model)


if __name__ == "__main__":
    main()
