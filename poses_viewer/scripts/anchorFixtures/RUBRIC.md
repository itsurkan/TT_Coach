# Anchor fixture judging rubric (rubricVersion = 1)

You are judging how well a reconstructed mannequin matches a player's
pose in a video frame.

The image you read is a composite: the video frame, with our reconstructed
mannequin drawn on top in semi-transparent green. Your job is to score the
match.

## What to score

Score these three things, all integers 0–10:

### imageQualityScore — How usable is this frame for comparison?

- 10 = player fully visible, no occlusion, mannequin anchored sensibly
       (overlapping the player's body, not floating off in space).
- 5  = significant occlusion or mannequin clearly anchored to the wrong place.
- 0  = unusable: player off-frame, or mannequin drawn somewhere unrelated
       to the player.

A LOW SCORE HERE IS NOT THE MANNEQUIN'S FAULT. It means the input data was
bad. Frames with imageQualityScore < 5 are skipped, not blamed on the
extractor.

### torsoScore — How well does the mannequin's torso match the player's?

Considers spine, shoulders, hips.

- 10 = visually indistinguishable.
- 7  = matches in all major axes; minor lean or yaw difference visible only
       on close inspection.
- 5  = clear mismatch in one axis (e.g. tilt off by ~15°, yaw clearly wrong
       direction).
- 0  = bears no resemblance.

### rightArmScore — Same scale, applied to the right arm

Shoulder → elbow → wrist → hand.

## Reasons

For each of the three: write a one-sentence reason. Be specific about
WHAT differs, not whether it's good or bad.

- Good reason: "Elbow position drifts ~15cm forward of player's elbow."
- Bad reason:  "Right arm doesn't match well."

## What NOT to score

DO NOT score left arm or legs. The mannequin's left-arm/leg positions in
this rendering may not reflect the actual extraction output (single-camera
ambiguity zeroes them) and should be ignored.

## After scoring

Invoke `write-anchor-fixture-entry` with the scores. The CLI requires
`--judged-by <model identity>`; fill in the model snapshot you are running
as. The CLI looks up `extractedAnchor` from the meta file the prep CLI
wrote — you don't pass it.
