// RTMPose 2D pose — COCO 17 keypoints, rendered from the REAL detector export
// (video_4_poses_rtm.json). Two real frames drive an eased ping-pong loop:
//   RAW_A = viewer frame 52 (RTM 305, t=5185ms) — racket arm low (ready)
//   RAW_B = viewer frame 55 (RTM 323, t=5491ms) — racket driven to contact
// The viewer scrubber is the 171-frame (~10fps) timeline; those map to RTM
// frames 305/323 in the full 1003-frame @17ms export (ratio ≈ 5.87). Both
// frames score cleanly (mean ≈ 0.8, every keypoint > 0.3) — unlike the raw
// early frames, where the detector hadn't locked on. Coordinates are the
// detector's pixel landmarks, auto-fitted to fill the dark canvas. The COCO
// neckline (ear->shoulder) is omitted; two synthetic toe points (17,18) are
// appended off each ankle so the figure reads with feet (COCO-17 has none).
//
// Colour scheme matches the viewer overlay:
//   amber   = left arm, left leg, torso sides, head
//   green   = shoulder girdle [5,6] + hip girdle [11,12]
//   magenta = right arm [6,8,10] + right leg [12,14,16]  (the racket side)
//
// COCO order: 0 nose,1 l_eye,2 r_eye,3 l_ear,4 r_ear, 5 l_sh,6 r_sh,7 l_el,
//   8 r_el,9 l_wr,10 r_wr, 11 l_hip,12 r_hip,13 l_knee,14 r_knee,15 l_ank,16 r_ank

const POSE_VIDEO = { w: 720, h: 1280, name: 'video_4.mp4', frame: 52, tMs: 5185 };

// --- two real detector keyframes (video_4_poses_rtm.json) --------------------
// Racket arm = RIGHT side (magenta). RTM 305 -> paddle LOW (ready); RTM 334 ->
// paddle driven UP to the face (contact/follow-through). Coordinates are the
// detector's pixel landmarks (x*720, y*1280); fitFrames normalizes & centers
// them with one shared transform so both frames stay aligned.
const RAW_A = [
  [538, 502], //  0 nose
  [548, 489], //  1 l eye
  [529, 480], //  2 r eye
  [544, 480], //  3 l ear
  [492, 455], //  4 r ear
  [518, 541], //  5 l shoulder
  [402, 508], //  6 r shoulder (racket)
  [525, 638], //  7 l elbow
  [391, 618], //  8 r elbow (racket)
  [535, 644], //  9 l wrist
  [426, 700], // 10 r wrist (paddle LOW)
  [398, 683], // 11 l hip
  [320, 666], // 12 r hip
  [484, 817], // 13 l knee
  [387, 808], // 14 r knee
  [415, 922], // 15 l ankle
  [352, 1017],// 16 r ankle
  [465, 955], // 17 l toe (synthetic foot, off the ankle in facing dir)
  [411, 1027],// 18 r toe (synthetic foot)
];
const RAW_B = [
  [578, 456], //  0 nose
  [585, 442], //  1 l eye
  [573, 433], //  2 r eye
  [529, 430], //  3 l ear
  [531, 407], //  4 r ear
  [473, 477], //  5 l shoulder
  [450, 460], //  6 r shoulder (racket)
  [480, 574], //  7 l elbow
  [471, 586], //  8 r elbow (racket)
  [575, 574], //  9 l wrist
  [564, 565], // 10 r wrist (paddle, contact)
  [357, 635], // 11 l hip
  [317, 632], // 12 r hip
  [429, 783], // 13 l knee
  [399, 809], // 14 r knee
  [408, 927], // 15 l ankle
  [352, 1011],// 16 r ankle
  [467, 936], // 17 l toe (synthetic foot)
  [410, 1025],// 18 r toe (synthetic foot)
];

// --- fit both frames into the canvas (shared transform, centered) ----------
const CANVAS_W = 380, CANVAS_H = 494, FIT_PAD = 50;
function fitFrames(frames) {
  let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
  frames.forEach(f => f.forEach(([x, y]) => {
    if (x < minX) minX = x; if (x > maxX) maxX = x;
    if (y < minY) minY = y; if (y > maxY) maxY = y;
  }));
  const s = Math.min((CANVAS_W - 2 * FIT_PAD) / (maxX - minX), (CANVAS_H - 2 * FIT_PAD) / (maxY - minY));
  const ux = (maxX - minX) * s, uy = (maxY - minY) * s;
  const ox = (CANVAS_W - ux) / 2 - minX * s;
  const oy = (CANVAS_H - uy) / 2 - minY * s;
  return frames.map(f => f.map(([x, y]) => [+(x * s + ox).toFixed(1), +(y * s + oy).toFixed(1)]));
}
const [KEY_A, KEY_B] = fitFrames([RAW_A, RAW_B]);
const FOREHAND_DRIVE_TOP = KEY_A; // back-compat export

// --- skeleton edges, coloured to match the viewer --------------------------
const COL = { y: '#F5B547', g: '#86CF4E', m: '#C657CC' };
const EDGES = [
  { e: [15, 13], c: 'y' }, { e: [13, 11], c: 'y' },          // left leg
  { e: [16, 14], c: 'm' }, { e: [14, 12], c: 'm' },          // right leg
  { e: [11, 12], c: 'g' }, { e: [5, 6], c: 'g' },            // hip + shoulder girdles
  { e: [5, 11], c: 'y' },  { e: [6, 12], c: 'y' },           // torso sides
  { e: [5, 7], c: 'y' },   { e: [7, 9], c: 'y' },            // left arm
  { e: [6, 8], c: 'm' },   { e: [8, 10], c: 'm' },           // right arm (racket)
  { e: [15, 17], c: 'y' }, { e: [16, 18], c: 'm' },          // feet (synthetic toes)
  { e: [0, 1], c: 'y', head: true }, { e: [0, 2], c: 'y', head: true },
  { e: [1, 3], c: 'y', head: true }, { e: [2, 4], c: 'y', head: true },
];
const FACE = new Set([0, 1, 2, 3, 4]);

// Reference (ideal) racket forearm: same elbow (8), wrist (10) tucked halfway in.
const REF_TUCK = 0.5;
function refArm(pts) {
  const e = pts[8], w = pts[10];
  return { e, w: [e[0] + (w[0] - e[0]) * REF_TUCK, e[1] + (w[1] - e[1]) * REF_TUCK] };
}

// ---- animation clock -------------------------------------------------------
function easeInOut(t) { return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2; }

function usePoseAnim(period = 2800) {
  const reduce = typeof window !== 'undefined' && window.matchMedia
    && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  const [t, setT] = useState(0);
  useEffect(() => {
    if (reduce) return;
    let raf;
    const loop = () => {
      const ph = (performance.now() % period) / period;
      const tri = ph < 0.5 ? ph * 2 : (1 - ph) * 2;
      setT(easeInOut(tri));
      raf = requestAnimationFrame(loop);
    };
    raf = requestAnimationFrame(loop);
    return () => cancelAnimationFrame(raf);
  }, [reduce, period]);
  return reduce ? 0 : t;
}

function lerpPts(A, B, t) {
  return A.map((p, i) => [p[0] + (B[i][0] - p[0]) * t, p[1] + (B[i][1] - p[1]) * t]);
}

function angleAt(pts, a, b, c) {
  const v1 = [pts[a][0] - pts[b][0], pts[a][1] - pts[b][1]];
  const v2 = [pts[c][0] - pts[b][0], pts[c][1] - pts[b][1]];
  const dot = v1[0] * v2[0] + v1[1] * v2[1];
  const m = Math.hypot(v1[0], v1[1]) * Math.hypot(v2[0], v2[1]);
  return Math.acos(Math.max(-1, Math.min(1, dot / m))) * 180 / Math.PI;
}

// % the racket forearm over-reaches the tucked reference
function handDriftPct(pts = KEY_A) {
  const e = pts[8], w = pts[10], r = refArm(pts);
  const det = Math.hypot(w[0] - e[0], w[1] - e[1]);
  const rr = Math.hypot(r.w[0] - e[0], r.w[1] - e[1]);
  return Math.round(((det - rr) / det) * 100);
}

function AngleArc({ pts, triplet, color = '#F5B547', r = 20 }) {
  const [a, b, c] = triplet;
  const [bx, by] = pts[b];
  const t1 = Math.atan2(pts[a][1] - by, pts[a][0] - bx);
  const t2 = Math.atan2(pts[c][1] - by, pts[c][0] - bx);
  let d = t2 - t1;
  while (d > Math.PI) d -= 2 * Math.PI;
  while (d < -Math.PI) d += 2 * Math.PI;
  const sweep = d > 0 ? 1 : 0;
  const x1 = bx + r * Math.cos(t1), y1 = by + r * Math.sin(t1);
  const x2 = bx + r * Math.cos(t2), y2 = by + r * Math.sin(t2);
  const mid = t1 + d / 2;
  const lx = bx + (r + 18) * Math.cos(mid), ly = by + (r + 18) * Math.sin(mid);
  const deg = Math.round(angleAt(pts, a, b, c));
  return (
    <g>
      <path d={`M ${x1} ${y1} A ${r} ${r} 0 0 ${sweep} ${x2} ${y2}`}
        fill="none" stroke={color} strokeWidth="1.5" opacity="0.85" />
      <text x={lx} y={ly} fontSize="13" fontWeight="600" fill={color}
        fontFamily="JetBrains Mono, monospace" textAnchor="middle" dominantBaseline="middle">
        {deg}°
      </text>
    </g>
  );
}

function PoseFigure({ compact = false, showFlag = true }) {
  const t = usePoseAnim();
  const pts = lerpPts(KEY_A, KEY_B, t);
  const ref = refArm(pts);
  const annotations = [{ t: [6, 8, 10], c: '#F5B547' }]; // racket elbow

  return (
    <svg viewBox="0 0 380 494" style={{ width: '100%', height: '100%' }}>
      <defs>
        <filter id="kglow">
          <feGaussianBlur stdDeviation="2" result="b" />
          <feMerge><feMergeNode in="b" /><feMergeNode in="SourceGraphic" /></feMerge>
        </filter>
      </defs>

      {/* Bones */}
      <g strokeLinecap="round" fill="none" style={{ filter: 'url(#kglow)' }}>
        {EDGES.map(({ e: [i, j], c, head }, k) => (
          <line key={k}
            x1={pts[i][0]} y1={pts[i][1]} x2={pts[j][0]} y2={pts[j][1]}
            stroke={COL[c]} strokeWidth={head ? 2 : 3.2}
            opacity={head ? 0.6 : 1} />
        ))}
      </g>

      {/* Keypoints — all amber, like the viewer */}
      <g>
        {pts.map(([x, y], i) => (
          <circle key={i} cx={x} cy={y}
            r={FACE.has(i) ? 2.6 : 5}
            fill={FACE.has(i) ? '#FBE3B0' : '#F5B547'} />
        ))}
      </g>

      {/* Computed angle arc at the racket elbow */}
      {annotations.map((a, i) => <AngleArc key={i} pts={pts} triplet={a.t} color={a.c} />)}

      {/* Flag pill — coach correction (leader tracks the live racket elbow) */}
      {showFlag && (
        <g>
          <g transform="translate(16 462)">
            <rect x="0" y="0" width="268" height="28" rx="14" fill="#F5B547" />
            <text x="134" y="19" fontSize="12" fontWeight="700" fill="#1A1A1F" fontFamily="Inter" textAnchor="middle">
              Keep your elbow closer to the body
            </text>
          </g>
        </g>
      )}
    </svg>
  );
}

Object.assign(window, { PoseFigure, FOREHAND_DRIVE_TOP, angleAt, handDriftPct, POSE_VIDEO, usePoseAnim });
