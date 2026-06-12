# RTMPose iOS — parity reference (port of rtmlib `Body(mode="balanced")`)

This is the exact spec the Swift `RTMPoseBackend` must replicate so iOS exports match the
desktop golden (`scripts/poses/export_poses_rtmpose.py` → `_poses_rtm.json`). Every constant
below is transcribed from the `rtmlib` source the golden runs. **Do not "improve" any of it** —
parity with the golden is the goal, even where rtmlib is itself quirky (notably BGR color order).

## Pipeline (rtmlib `Body.__call__`, two-stage)

```
frame (BGR) ──▶ YOLOX(frame) ──▶ bboxes[] ──▶ RTMPose(frame, bboxes) ──▶ (keypoints[N,17,2], scores[N,17])
                                          ──▶ best_person: argmax over N of mean(scores[n])  (export's selector)
```

`PoseBackend.estimatePose` returns ONE person's 17 keypoints. Faithful port: run YOLOX, run
RTMPose on **each** returned bbox, then pick the detection whose **mean keypoint score** is
highest (matches `best_person` in the export script). Single-player footage usually yields one
bbox, so this is cheap; do not pre-filter to the top detection box (that selects by detection
score, not pose score, and can diverge).

## Color order — CRITICAL

`rtmlib` feeds OpenCV **BGR** frames straight through. YOLOX takes raw BGR uint8. RTMPose
subtracts an RGB-valued mean from BGR channels (an rtmlib quirk we must copy). On iOS, read the
`CVPixelBuffer` as `kCVPixelFormatType_32BGRA` and use channel order **B, G, R** — this already
matches. Do NOT swap to RGB.

## Stage 1 — YOLOX detector

Model `yolox_m_...onnx`, input 640×640. Defaults from rtmlib: `score_thr = 0.7`, `nms_thr = 0.45`,
mode `human`.

**Preprocess (`YOLOX.preprocess`):**
- `ratio = min(640 / imgH, 640 / imgW)`.
- Resize image to `(round? -> int(imgW*ratio), int(imgH*ratio))` with **bilinear** (`cv2.INTER_LINEAR`).
- Create a `640×640×3` canvas filled with **114** (uint8), paste the resized image at the
  **top-left** `[0:rh, 0:rw]`. No centering.
- Channel order BGR, **no** mean/std, values stay 0–255.
- Tensor: HWC→CHW, float32, shape `(1, 3, 640, 640)`.

**Postprocess (`YOLOX.postprocess`, "onnx without nms" branch — output last-dim is 85):**
- Strides `[8, 16, 32]`; for each, grid of size `(640/stride)²`. Build per-anchor `grid (x,y)`
  and `expanded_stride`. Concatenate across the three strides (8400 anchors total).
- `outputs[..., 0:2] = (raw_xy + grid) * stride` → box center cx,cy (in 640-space).
- `outputs[..., 2:4] = exp(raw_wh) * stride` → box w,h.
- `boxes_xyxy = [cx - w/2, cy - h/2, cx + w/2, cy + h/2]`, then `/= ratio` (back to image px).
- `scores = obj_conf (col 4) * class_probs (cols 5:)` → shape (8400, 80).
- `multiclass_nms(score_thr=0.7, nms_thr=0.45)`: per class, mask `score > 0.7`, run single-class
  IoU NMS (`nms_thr=0.45`; **note rtmlib's `+1` in area/overlap: `area=(x2-x1+1)*(y2-y1+1)`,
  `w=max(0, xx2-xx1+1)`**). Keep boxes; for `human` mode return all kept boxes (xyxy, image px).

If no detection clears threshold → no person → estimatePose returns `[]`.

## Stage 2 — RTMPose

Model `rtmpose-m_...onnx`, `model_input_size = (W=192, H=256)`,
`mean = (123.675, 116.28, 103.53)`, `std = (58.395, 57.12, 57.375)`, `simcc_split_ratio = 2.0`.

**Preprocess (`RTMPose.preprocess`):**
1. `bbox_xyxy2cs(bbox, padding=1.25)`:
   `center = ((x1+x2)/2, (y1+y2)/2)`, `scale = ((x2-x1)*1.25, (y2-y1)*1.25)`.
2. `top_down_affine(input_size=(192,256), scale, center, img)`:
   - `aspect_ratio = w/h = 192/256 = 0.75`. Fix scale to aspect ratio:
     `if scale_w > scale_h * 0.75: scale = (scale_w, scale_w/0.75) else: scale = (scale_h*0.75, scale_h)`.
   - `warp_mat = get_warp_matrix(center, scale, rot=0, output_size=(192,256))` (see below),
     then `cv2.warpAffine(img, warp_mat, (192,256), INTER_LINEAR)`.
3. Normalize: `(img - mean) / std`, channels in **BGR** order (mean[0]=123.675 applied to B, etc.).
4. Tensor: HWC→CHW, float32, shape `(1, 3, 256, 192)`.

**`get_warp_matrix(center, scale, rot=0, output_size=(w,h))`** (rot=0 simplifies, but implement
the general form for fidelity):
- `src_w = scale[0]`, `dst_w = w`, `dst_h = h`.
- `src_dir = rotate([0, -0.5*src_w], rot)`; with rot=0 → `[0, -0.5*src_w]`.
- `dst_dir = [0, -0.5*dst_w]`.
- `src[0]=center`, `src[1]=center+src_dir`, `src[2]=_get_3rd_point(src[0],src[1])`.
- `dst[0]=[w/2, h/2]`, `dst[1]=dst[0]+dst_dir`, `dst[2]=_get_3rd_point(dst[0],dst[1])`.
- `_get_3rd_point(a,b) = b + [-(a-b).y, (a-b).x]` (rotate a-b by 90° CCW about b).
- `warp_mat = affineFromThreePointPairs(src → dst)` (the 2×3 solving `dst = M·[src;1]`,
  i.e. `cv2.getAffineTransform(src, dst)`).
- Apply with bilinear sampling. On iOS use vImage (`vImageAffineWarp_ARGB8888`/Planar) or
  Accelerate; or sample manually with the inverse matrix. Must be bilinear to match `cv2`.

**Postprocess (`RTMPose.postprocess`):**
- Outputs: `simcc_x` shape `(1, 17, 384)`  (Wx = 192*2), `simcc_y` shape `(1, 17, 512)` (Wy = 256*2).
- `get_simcc_maximum`: per keypoint, `x_loc = argmax(simcc_x[k])`, `y_loc = argmax(simcc_y[k])`,
  `val = 0.5*(max(simcc_x[k]) + max(simcc_y[k]))`; if `val <= 0` set loc to `(-1,-1)`.
- `keypoints = [x_loc, y_loc] / 2.0` (simcc_split_ratio).
- Rescale to image px:
  `keypoints = keypoints / model_input_size * scale + center - scale/2`,
  where `model_input_size = (192, 256)`, and `scale`,`center` are the aspect-fixed values from
  preprocess. (Per-axis: `kx = kx/192*scale_w + center_x - scale_w/2`, similarly y with 256/scale_h.)
- `score = val` (clamp to [0,1] only at JSON-export, as the Python does).

## Output normalization (matches export script)

For the chosen person, per keypoint `i`:
- `x = clamp(px_x / videoWidth, 0, 1)`, `y = clamp(px_y / videoHeight, 0, 1)`,
  `score = clamp(val, 0, 1)`. Round to 4 decimals only in the CLI's JSON writer.
- `Keypoint2D(index=i, x, y, score)` for the in-app backend (no rounding needed there).
- No person detected → return `[]` (empty), which the shared `PoseFrame2D` tolerates.

## Sanity checks for the parity gate

- Detection coverage on `Videos/andrii_1`, `video_2` should be dense (near every frame), unlike Vision.
- Head (nose, idx 0) y < hips (idx 11/12) y in a normal standing frame (origin top-left).
- Shared pipeline `detect → ForwardStrokeFilter → RepFilter` forward-rep count within **±1** of the
  Python `_poses_rtm.json` golden.
