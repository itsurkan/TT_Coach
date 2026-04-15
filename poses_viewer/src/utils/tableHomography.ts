/**
 * Table Homography — maps 2D screen coordinates to real-world table coordinates (cm).
 *
 * Table dimensions: 274cm (length) × 152.5cm (width), net at 137cm (midpoint).
 *
 * Keypoint order:
 *   pt1=farL(0), pt2=farR(1), pt3=nearR(2), pt4=nearL(3), pt5=netL(4), pt6=netR(5)
 *
 * Real-world coordinate system (cm, top-down view):
 *   (0, 0) = far-left corner
 *   (152.5, 0) = far-right corner
 *   (152.5, 274) = near-right corner
 *   (0, 274) = near-left corner
 *   Net at y = 137
 */

// Table physical dimensions in cm
export const TABLE_WIDTH = 152.5
export const TABLE_LENGTH = 274.0
export const NET_Y = TABLE_LENGTH / 2  // 137 cm

// 3x3 matrix type (row-major)
export type Mat3 = [number, number, number, number, number, number, number, number, number]

export interface TablePoint2D {
  x: number  // normalized 0-1 screen coordinate
  y: number
}

export interface TablePoint3D {
  x_cm: number  // width axis (0 = left edge, 152.5 = right edge)
  y_cm: number  // length axis (0 = far end, 274 = near end)
}

export interface TableHomography {
  /** Screen → Table transform matrix */
  H: Mat3
  /** Table → Screen transform matrix (inverse) */
  H_inv: Mat3
  /** Whether the homography is valid */
  valid: boolean
}

/**
 * Real-world coordinates (cm) for each of the 6 keypoints.
 * Index matches the labeling order: 0=farL, 1=farR, 2=nearR, 3=nearL, 4=netL, 5=netR
 */
const KEYPOINT_CM: [number, number][] = [
  [0, 0],                    // 0: farL
  [TABLE_WIDTH, 0],          // 1: farR
  [TABLE_WIDTH, TABLE_LENGTH], // 2: nearR
  [0, TABLE_LENGTH],         // 3: nearL
  [0, NET_Y],                // 4: netL
  [TABLE_WIDTH, NET_Y],      // 5: netR
]

/**
 * Compute homography from 4 screen corner points to real-world table rectangle.
 * Uses Direct Linear Transform (DLT) algorithm.
 *
 * @param corners - 4 corner keypoints [farL, farR, nearR, nearL] in normalized screen coords
 * @param videoWidth - video pixel width (for denormalization)
 * @param videoHeight - video pixel height
 */
export function computeHomography(
  corners: [TablePoint2D, TablePoint2D, TablePoint2D, TablePoint2D],
  videoWidth: number,
  videoHeight: number,
): TableHomography {
  // Source points: screen pixel coordinates
  const src = corners.map(p => [p.x * videoWidth, p.y * videoHeight])

  // Destination points: real-world table coordinates (cm)
  const dst = [
    KEYPOINT_CM[0],  // farL → (0, 0)
    KEYPOINT_CM[1],  // farR → (152.5, 0)
    KEYPOINT_CM[2],  // nearR → (152.5, 274)
    KEYPOINT_CM[3],  // nearL → (0, 274)
  ]

  const H = solveHomography(src, dst)
  if (!H) {
    return { H: identity(), H_inv: identity(), valid: false }
  }

  const H_inv = invertMat3(H)
  if (!H_inv) {
    return { H: identity(), H_inv: identity(), valid: false }
  }

  return { H, H_inv, valid: true }
}

/**
 * Compute homography from any 4+ of the 6 labeled keypoints.
 *
 * Computes a preliminary homography from 4 well-distributed available points,
 * then back-projects any missing points through it. This correctly handles
 * perspective foreshortening (unlike screen-space midpoint reflection).
 *
 * @param points - Array of 6 keypoints (null for missing ones)
 * @param videoWidth - video pixel width
 * @param videoHeight - video pixel height
 */
export function computeHomographyFromPartial(
  points: Array<{ x: number; y: number } | null>,
  videoWidth: number,
  videoHeight: number,
): { homography: TableHomography; allKeypoints: Array<{ x: number; y: number }> } | null {
  // If all 4 corners present, compute directly
  if (points[0] && points[1] && points[2] && points[3]) {
    const homography = computeHomography(
      [points[0], points[1], points[2], points[3]],
      videoWidth, videoHeight,
    )
    if (!homography.valid) return null
    const allKeypoints = points.map((p, i) => {
      if (p) return { x: p.x, y: p.y }
      const proj = tableToScreen(homography, KEYPOINT_CM[i][0], KEYPOINT_CM[i][1], videoWidth, videoHeight)
      return proj ?? { x: 0, y: 0 }
    })
    return { homography, allKeypoints }
  }

  // Infer missing corners using vanishing point geometry
  if (points.filter(Boolean).length < 4) return null
  const allKeypoints = inferMissingByVanishingPoint(points)
  if (!allKeypoints) return null

  // Compute homography from the 4 corners (original + inferred)
  const homography = computeHomography(
    [allKeypoints[0], allKeypoints[1], allKeypoints[2], allKeypoints[3]],
    videoWidth, videoHeight,
  )
  if (!homography.valid) return null

  // Fill any still-missing net points via homography
  for (let i = 4; i < 6; i++) {
    if (!points[i]) {
      const proj = tableToScreen(homography, KEYPOINT_CM[i][0], KEYPOINT_CM[i][1], videoWidth, videoHeight)
      if (proj) allKeypoints[i] = proj
    }
  }

  return { homography, allKeypoints }
}

/**
 * Project a screen point to table surface coordinates.
 * @returns Table position in cm, or null if projection fails.
 */
export function screenToTable(
  homography: TableHomography,
  screenX: number,  // normalized 0-1
  screenY: number,
  videoWidth: number,
  videoHeight: number,
): TablePoint3D | null {
  if (!homography.valid) return null

  const px = screenX * videoWidth
  const py = screenY * videoHeight

  const result = applyHomography(homography.H, px, py)
  if (!result) return null

  return { x_cm: result[0], y_cm: result[1] }
}

/**
 * Project a table coordinate back to screen space.
 * @returns Normalized screen coordinates (0-1), or null if projection fails.
 */
export function tableToScreen(
  homography: TableHomography,
  x_cm: number,
  y_cm: number,
  videoWidth: number,
  videoHeight: number,
): TablePoint2D | null {
  if (!homography.valid) return null

  const result = applyHomography(homography.H_inv, x_cm, y_cm)
  if (!result) return null

  return {
    x: result[0] / videoWidth,
    y: result[1] / videoHeight,
  }
}

// ── Matrix math ─────────────────────────────────────────────────────────────

function identity(): Mat3 {
  return [1, 0, 0, 0, 1, 0, 0, 0, 1]
}

/**
 * Apply a 3x3 homography to a 2D point.
 * H * [x, y, 1]^T → [x', y', w] → [x'/w, y'/w]
 */
function applyHomography(H: Mat3, x: number, y: number): [number, number] | null {
  const w = H[6] * x + H[7] * y + H[8]
  if (Math.abs(w) < 1e-10) return null

  const xp = (H[0] * x + H[1] * y + H[2]) / w
  const yp = (H[3] * x + H[4] * y + H[5]) / w
  return [xp, yp]
}

// ── Line intersection geometry ──────────────────────────────────────────────

type Point2D = { x: number; y: number }

/** Intersect two lines defined by two points each. Returns null if parallel. */
function intersectLines(
  p1: Point2D, p2: Point2D,
  p3: Point2D, p4: Point2D,
): Point2D | null {
  const d1x = p2.x - p1.x, d1y = p2.y - p1.y
  const d2x = p4.x - p3.x, d2y = p4.y - p3.y
  const denom = d1x * d2y - d1y * d2x
  if (Math.abs(denom) < 1e-12) return null  // parallel
  const t = ((p3.x - p1.x) * d2y - (p3.y - p1.y) * d2x) / denom
  return { x: p1.x + t * d1x, y: p1.y + t * d1y }
}

/**
 * Infer missing keypoints using vanishing point geometry.
 *
 * A corner = intersection of two table edges. Each edge line needs ≥2 known points,
 * or 1 known point + the edge's vanishing point direction.
 *
 * Vanishing points:
 * - Length VP: where left edge (pt1-pt5-pt4) and right edge (pt2-pt6-pt3) meet
 * - Width VP: where far edge (pt1-pt2), near edge (pt4-pt3), and net (pt5-pt6) meet
 *
 * Key case: IMG_6370-like — have pt2, pt3, pt4, pt6 (missing pt1, pt5).
 *   Width VP = intersect(near edge pt4→pt3, net line thru pt6 ∥ near edge... NO)
 *   Instead: use right edge (pt2→pt3) to define length line, and near edge (pt4→pt3)
 *   to define width line. pt1 = intersection of (left edge ∥ right edge thru pt4) and
 *   (far edge ∥ near edge thru pt2). But "parallel in perspective" = "through same VP".
 *
 * Strategy:
 * 1. Find width VP from any 2 width-parallel lines (near/far/net)
 * 2. Find length VP from any 2 length-parallel lines (left/right edges)
 *    OR from right-edge cross-ratio if only 1 left-edge point
 * 3. Missing corner = intersection of its two edges (each defined by 1 point + VP direction)
 */
function inferMissingByVanishingPoint(
  points: Array<Point2D | null>,
): Array<Point2D> | null {
  const p = (i: number): Point2D | null => points[i]
  const result: Array<Point2D | null> = [...points]

  // Edge point groups
  const rightEdge = [p(1), p(5), p(2)].filter(Boolean) as Point2D[]  // pt2, pt6, pt3
  const leftEdge = [p(0), p(4), p(3)].filter(Boolean) as Point2D[]   // pt1, pt5, pt4

  // Width lines (horizontal in 3D): far(pt1,pt2), net(pt5,pt6), near(pt4,pt3)
  const widthLines: [Point2D, Point2D][] = []
  if (p(0) && p(1)) widthLines.push([p(0)!, p(1)!])
  if (p(4) && p(5)) widthLines.push([p(4)!, p(5)!])
  if (p(3) && p(2)) widthLines.push([p(3)!, p(2)!])

  // ── Width vanishing point (where far/near/net edges converge) ──
  let widthVP: Point2D | null = null
  if (widthLines.length >= 2) {
    widthVP = intersectLines(widthLines[0][0], widthLines[0][1], widthLines[1][0], widthLines[1][1])
  }

  // ── Length vanishing point (where left/right edges converge) ──
  let lengthVP: Point2D | null = null
  if (rightEdge.length >= 2 && leftEdge.length >= 2) {
    // Exact: intersect right and left edge lines
    lengthVP = intersectLines(rightEdge[0], rightEdge[rightEdge.length - 1], leftEdge[0], leftEdge[leftEdge.length - 1])
  }
  // If we have 3 points on one edge (far, net, near), compute exact VP via cross-ratio.
  // The net point is the real-world midpoint (137 of 274cm). In perspective, the
  // screen midpoint parameter t_B differs from 0.5, revealing the VP location.
  // Cross-ratio: VP = A + t_V * (C - A) where t_V = t_B / (2*t_B - 1).
  if (!lengthVP && rightEdge.length === 3) {
    const [A, B, C] = rightEdge  // pt2(far), pt6(net), pt3(near)
    const dx = C.x - A.x, dy = C.y - A.y
    const len2 = dx * dx + dy * dy
    if (len2 > 1e-10) {
      const tB = ((B.x - A.x) * dx + (B.y - A.y) * dy) / len2
      const denom = 2 * tB - 1
      if (Math.abs(denom) > 1e-6) {
        const tV = tB / denom
        lengthVP = { x: A.x + tV * dx, y: A.y + tV * dy }
      }
    }
  }
  if (!lengthVP && leftEdge.length === 3) {
    const [A, B, C] = leftEdge  // pt1(far), pt5(net), pt4(near)
    const dx = C.x - A.x, dy = C.y - A.y
    const len2 = dx * dx + dy * dy
    if (len2 > 1e-10) {
      const tB = ((B.x - A.x) * dx + (B.y - A.y) * dy) / len2
      const denom = 2 * tB - 1
      if (Math.abs(denom) > 1e-6) {
        const tV = tB / denom
        lengthVP = { x: A.x + tV * dx, y: A.y + tV * dy }
      }
    }
  }
  // Last resort: approximate from edge direction
  if (!lengthVP && rightEdge.length >= 2) {
    const dx = rightEdge[0].x - rightEdge[rightEdge.length - 1].x
    const dy = rightEdge[0].y - rightEdge[rightEdge.length - 1].y
    lengthVP = { x: rightEdge[0].x + dx * 100, y: rightEdge[0].y + dy * 100 }
  }
  if (!lengthVP && leftEdge.length >= 2) {
    const dx = leftEdge[0].x - leftEdge[leftEdge.length - 1].x
    const dy = leftEdge[0].y - leftEdge[leftEdge.length - 1].y
    lengthVP = { x: leftEdge[0].x + dx * 100, y: leftEdge[0].y + dy * 100 }
  }

  // If widthVP still unknown but we have lengthVP + pt6(netR) + pt4(nearL),
  // infer pt5(netL) on the left edge, then get widthVP from near edge ∩ net line.
  //
  // The net is at 50% of the table length. On the right edge (A=far, C=near),
  // pt6(net) is at parameter tB. The cross-ratio VP is at tV = tB/(2*tB-1).
  // On the left edge parameterized as P(s) = pt4 + s*(lengthVP - pt4),
  // where s=0 is near(pt4) and s=1 is VP, the net point is at:
  //   s_net = (tB - 1) / (tV - 1)
  if (!widthVP && lengthVP && leftEdge.length >= 1 && rightEdge.length === 3 && p(5)) {
    const [A, B, C] = rightEdge  // far, net, near
    const dx = C.x - A.x, dy = C.y - A.y
    const len2 = dx * dx + dy * dy
    if (len2 > 1e-10) {
      const tB = ((B.x - A.x) * dx + (B.y - A.y) * dy) / len2
      const denom = 2 * tB - 1
      if (Math.abs(denom) > 1e-6) {
        const tV = tB / denom
        const sNet = (tB - 1) / (tV - 1)
        const nearPt = leftEdge[leftEdge.length - 1]  // pt4(near end of left edge)
        const pt5Inferred: Point2D = {
          x: nearPt.x + sNet * (lengthVP.x - nearPt.x),
          y: nearPt.y + sNet * (lengthVP.y - nearPt.y),
        }
        // widthVP = intersection of near edge (pt4→pt3) and net line (pt5→pt6)
        widthVP = intersectLines(nearPt, rightEdge[2], pt5Inferred, p(5)!)
      }
    }
  }
  // Last resort: approximate from single width line
  if (!widthVP && widthLines.length >= 1) {
    const dx = widthLines[0][1].x - widthLines[0][0].x
    const dy = widthLines[0][1].y - widthLines[0][0].y
    widthVP = { x: widthLines[0][0].x + dx * 100, y: widthLines[0][0].y + dy * 100 }
  }

  if (!widthVP || !lengthVP) return null

  // ── Infer missing corners ──
  // Each corner = intersection of its length-edge line and width-edge line

  // pt1(farL): left edge (leftEdge[0]→lengthVP) ∩ far edge (pt2→widthVP)
  if (!result[0] && leftEdge.length >= 1 && p(1)) {
    result[0] = intersectLines(leftEdge[0], lengthVP, p(1)!, widthVP)
  }

  // pt2(farR): right edge (rightEdge[0]→lengthVP) ∩ far edge (pt1→widthVP)
  if (!result[1] && rightEdge.length >= 1 && p(0)) {
    result[1] = intersectLines(rightEdge[0], lengthVP, p(0)!, widthVP)
  }

  // pt3(nearR): right edge (rightEdge[0]→lengthVP) ∩ near edge (pt4→widthVP)
  if (!result[2] && rightEdge.length >= 1 && p(3)) {
    result[2] = intersectLines(rightEdge[0], lengthVP, p(3)!, widthVP)
  }

  // pt4(nearL): left edge (leftEdge[0]→lengthVP) ∩ near edge (pt3→widthVP)
  if (!result[3] && leftEdge.length >= 1 && p(2)) {
    result[3] = intersectLines(leftEdge[0], lengthVP, p(2)!, widthVP)
  }

  if (!result[0] || !result[1] || !result[2] || !result[3]) return null
  return result.map(pt => pt ?? { x: 0, y: 0 })
}

/**
 * Solve homography using DLT (Direct Linear Transform).
 * Given 4 point correspondences (src[i] → dst[i]), solve for 3x3 matrix H
 * such that dst = H * src (in homogeneous coordinates).
 *
 * Sets up 8 equations (2 per point) for 8 unknowns (H has 9 entries but is defined up to scale).
 */
function solveHomography(
  src: number[][],
  dst: number[][],
): Mat3 | null {
  if (src.length !== 4 || dst.length !== 4) return null

  // Build 8x9 matrix A for Ah = 0
  // For each point pair (x, y) → (x', y'):
  //   [-x, -y, -1,  0,  0,  0, x*x', y*x', x'] = 0
  //   [ 0,  0,  0, -x, -y, -1, x*y', y*y', y'] = 0
  const A: number[][] = []

  for (let i = 0; i < 4; i++) {
    const [x, y] = src[i]
    const [xp, yp] = dst[i]

    A.push([-x, -y, -1, 0, 0, 0, x * xp, y * xp, xp])
    A.push([0, 0, 0, -x, -y, -1, x * yp, y * yp, yp])
  }

  // Solve using the constraint h[8] = 1 (works when h[8] ≠ 0, which is typical)
  // Rearrange: A' * h' = -a8 where A' is 8x8, h' is first 8 elements, a8 is last column
  const A8: number[][] = []
  const b8: number[] = []

  for (let i = 0; i < 8; i++) {
    A8.push(A[i].slice(0, 8))
    b8.push(-A[i][8])
  }

  const h8 = solveLinear8x8(A8, b8)
  if (!h8) return null

  return [...h8, 1] as Mat3
}

/**
 * Solve 8x8 linear system Ax = b using Gaussian elimination with partial pivoting.
 */
function solveLinear8x8(A: number[][], b: number[]): number[] | null {
  const n = 8
  // Augmented matrix
  const aug: number[][] = A.map((row, i) => [...row, b[i]])

  for (let col = 0; col < n; col++) {
    // Partial pivoting
    let maxRow = col
    let maxVal = Math.abs(aug[col][col])
    for (let row = col + 1; row < n; row++) {
      if (Math.abs(aug[row][col]) > maxVal) {
        maxVal = Math.abs(aug[row][col])
        maxRow = row
      }
    }
    if (maxVal < 1e-12) return null
    if (maxRow !== col) {
      [aug[col], aug[maxRow]] = [aug[maxRow], aug[col]]
    }

    // Eliminate below
    for (let row = col + 1; row < n; row++) {
      const factor = aug[row][col] / aug[col][col]
      for (let j = col; j <= n; j++) {
        aug[row][j] -= factor * aug[col][j]
      }
    }
  }

  // Back substitution
  const x: number[] = []
  for (let k = 0; k < n; k++) x.push(0)
  for (let i = n - 1; i >= 0; i--) {
    if (Math.abs(aug[i][i]) < 1e-12) return null
    let sum = aug[i][n]
    for (let j = i + 1; j < n; j++) {
      sum -= aug[i][j] * x[j]
    }
    x[i] = sum / aug[i][i]
  }

  return x
}

/**
 * Invert a 3x3 matrix using cofactor expansion.
 * Normalizes before inversion to avoid numerical issues with homography matrices
 * where entries span many orders of magnitude.
 */
function invertMat3(m: Mat3): Mat3 | null {
  // Normalize by Frobenius norm so entries are O(1)
  const norm = Math.sqrt(m.reduce((s, v) => s + v * v, 0))
  if (norm < 1e-15) return null
  const mn = m.map(v => v / norm) as Mat3
  const [a, b, c, d, e, f, g, h, i] = mn

  const det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
  if (Math.abs(det) < 1e-15) return null

  // inv(M) = inv(norm * Mn) = (1/norm) * inv(Mn)
  const invDet = 1 / (det * norm)

  return [
    (e * i - f * h) * invDet,
    (c * h - b * i) * invDet,
    (b * f - c * e) * invDet,
    (f * g - d * i) * invDet,
    (a * i - c * g) * invDet,
    (c * d - a * f) * invDet,
    (d * h - e * g) * invDet,
    (b * g - a * h) * invDet,
    (a * e - b * d) * invDet,
  ]
}
