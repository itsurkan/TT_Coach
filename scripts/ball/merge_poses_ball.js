#!/usr/bin/env node
/**
 * merge_poses_ball.js
 *
 * Merges *_poses.json + *_ball.json → *_poses_ball.json for every video
 * in Videos/.
 *
 * Output schema (matches pose_viewer expectations):
 * {
 *   "videoUri":        <from poses JSON>,
 *   "videoName":       <from ball JSON>,
 *   "intervalMs":      <from poses JSON>,
 *   "totalFrames":     <from poses JSON>,
 *   "videoDurationMs": <from ball JSON>,
 *   "videoWidth":      <from ball JSON>,
 *   "videoHeight":     <from ball JSON>,
 *   "exportTimestamp": <current time ms>,
 *   "frames": [
 *     {
 *       "frameIndex":  <int>,
 *       "timestampMs": <int>,
 *       "landmarks":   <33 MediaPipe landmarks, from poses JSON>,
 *       "ball":        <BallDetection or null, from ball JSON>
 *     }, ...
 *   ]
 * }
 */

const fs = require('fs');
const path = require('path');

const VIDEOS_DIR = path.resolve(__dirname, '..', '..', 'Videos');

function findPairs(videosDir) {
  const files = new Set(fs.readdirSync(videosDir));
  const pairs = [];
  for (const f of [...files].sort()) {
    if (!f.endsWith('_poses.json')) continue;
    const base = f.slice(0, -'_poses.json'.length);
    const ballFile = base + '_ball.json';
    if (files.has(ballFile)) {
      pairs.push({
        base,
        posesPath: path.join(videosDir, f),
        ballPath:  path.join(videosDir, ballFile),
        outPath:   path.join(videosDir, base + '_poses_ball.json'),
      });
    } else {
      console.warn(`  WARNING: no ball file for ${f}, skipping`);
    }
  }
  return pairs;
}

function merge(posesPath, ballPath) {
  const poses = JSON.parse(fs.readFileSync(posesPath, 'utf8'));
  const ball  = JSON.parse(fs.readFileSync(ballPath,  'utf8'));

  // Index ball frames by timestampMs
  const ballByTs = new Map();
  for (const frame of (ball.frames ?? [])) {
    ballByTs.set(frame.timestampMs, frame.ball ?? null);
  }

  const mergedFrames = (poses.frames ?? []).map(poseFrame => ({
    frameIndex:  poseFrame.frameIndex,
    timestampMs: poseFrame.timestampMs,
    landmarks:   poseFrame.landmarks ?? [],
    ball:        ballByTs.has(poseFrame.timestampMs)
                   ? ballByTs.get(poseFrame.timestampMs)
                   : null,
  }));

  return {
    videoUri:        poses.videoUri ?? null,
    videoName:       ball.videoName ?? null,
    intervalMs:      poses.intervalMs ?? ball.intervalMs ?? 100,
    totalFrames:     poses.totalFrames ?? ball.totalFrames ?? mergedFrames.length,
    videoDurationMs: ball.videoDurationMs ?? null,
    videoWidth:      ball.videoWidth ?? null,
    videoHeight:     ball.videoHeight ?? null,
    exportTimestamp: Date.now(),
    frames:          mergedFrames,
  };
}

if (!fs.existsSync(VIDEOS_DIR)) {
  console.error(`ERROR: directory not found: ${VIDEOS_DIR}`);
  process.exit(1);
}

const pairs = findPairs(VIDEOS_DIR);
if (pairs.length === 0) {
  console.log('No *_poses.json + *_ball.json pairs found.');
  process.exit(0);
}

for (const { base, posesPath, ballPath, outPath } of pairs) {
  console.log(`Merging ${base}...`);
  const merged = merge(posesPath, ballPath);
  fs.writeFileSync(outPath, JSON.stringify(merged, null, 2), 'utf8');
  const nDetected = merged.frames.filter(f => f.ball !== null).length;
  console.log(`  → ${outPath}`);
  console.log(`     ${merged.frames.length} frames, ${nDetected} with ball detected`);
}

console.log('\nDone.');
