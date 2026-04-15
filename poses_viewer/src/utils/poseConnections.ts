export type Side = 'left' | 'right' | 'center'
export type Connection = [number, number, Side]

// MediaPipe Pose landmark indices:
//  0=nose  1=l-eye-inner  2=l-eye  3=l-eye-outer  4=r-eye-inner  5=r-eye  6=r-eye-outer
//  7=l-ear  8=r-ear  9=mouth-l  10=mouth-r
// 11=l-shoulder  12=r-shoulder  13=l-elbow  14=r-elbow  15=l-wrist  16=r-wrist
// 17=l-pinky  18=r-pinky  19=l-index  20=r-index  21=l-thumb  22=r-thumb
// 23=l-hip  24=r-hip  25=l-knee  26=r-knee  27=l-ankle  28=r-ankle
// 29=l-heel  30=r-heel  31=l-foot  32=r-foot
export const POSE_CONNECTIONS: Connection[] = [
  // Face
  [0, 1, 'center'], [1, 2, 'left'], [2, 3, 'left'], [3, 7, 'left'],
  [0, 4, 'center'], [4, 5, 'right'], [5, 6, 'right'], [6, 8, 'right'],
  [9, 10, 'center'],
  // Torso
  [11, 12, 'center'], [11, 23, 'left'], [12, 24, 'right'], [23, 24, 'center'],
  // Left arm
  [11, 13, 'left'], [13, 15, 'left'],
  [15, 17, 'left'], [15, 19, 'left'], [15, 21, 'left'], [17, 19, 'left'],
  // Right arm
  [12, 14, 'right'], [14, 16, 'right'],
  [16, 18, 'right'], [16, 20, 'right'], [16, 22, 'right'], [18, 20, 'right'],
  // Left leg
  [23, 25, 'left'], [25, 27, 'left'], [27, 29, 'left'], [27, 31, 'left'], [29, 31, 'left'],
  // Right leg
  [24, 26, 'right'], [26, 28, 'right'], [28, 30, 'right'], [28, 32, 'right'], [30, 32, 'right'],
]

export const SIDE_COLORS: Record<Side, string> = {
  left: '#3b82f6',   // blue-500
  right: '#ef4444',  // red-500
  center: '#22c55e', // green-500
}
