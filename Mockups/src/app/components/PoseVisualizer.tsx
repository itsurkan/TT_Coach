import { useEffect, useRef, useState } from "react";
import { motion } from "motion/react";

export interface PoseLandmark {
  x: number;
  y: number;
  z: number;
  visibility: number;
}

export interface PoseFrame {
  landmarks: PoseLandmark[];
  timestamp: number;
}

interface PoseVisualizerProps {
  frames: PoseFrame[];
  duration: number; // in seconds
  highlightedPoints?: number[]; // indices of landmarks to highlight
  highlightedConnections?: [number, number][]; // pairs of landmark indices to highlight
  autoPlay?: boolean;
}

// MediaPipe Pose landmark connections
const POSE_CONNECTIONS: [number, number][] = [
  // Face
  [0, 1], [1, 2], [2, 3], [3, 7], [0, 4], [4, 5], [5, 6], [6, 8],
  // Torso
  [9, 10], [11, 12], [11, 13], [13, 15], [12, 14], [14, 16],
  [11, 23], [12, 24], [23, 24],
  // Left arm
  [11, 13], [13, 15], [15, 17], [15, 19], [15, 21], [17, 19],
  // Right arm
  [12, 14], [14, 16], [16, 18], [16, 20], [16, 22], [18, 20],
  // Left leg
  [23, 25], [25, 27], [27, 29], [27, 31], [29, 31],
  // Right leg
  [24, 26], [26, 28], [28, 30], [28, 32], [30, 32],
];

export function PoseVisualizer({
  frames,
  duration,
  highlightedPoints = [],
  highlightedConnections = [],
  autoPlay = true,
}: PoseVisualizerProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [currentFrameIndex, setCurrentFrameIndex] = useState(0);
  const [isPlaying, setIsPlaying] = useState(autoPlay);
  const animationRef = useRef<number>();
  const startTimeRef = useRef<number>();

  useEffect(() => {
    if (!isPlaying || frames.length === 0) return;

    const animate = (timestamp: number) => {
      if (!startTimeRef.current) {
        startTimeRef.current = timestamp;
      }

      const elapsed = (timestamp - startTimeRef.current) / 1000; // Convert to seconds
      const progress = (elapsed % duration) / duration;
      const frameIndex = Math.floor(progress * frames.length);

      setCurrentFrameIndex(frameIndex);
      drawPose(frames[frameIndex]);

      animationRef.current = requestAnimationFrame(animate);
    };

    animationRef.current = requestAnimationFrame(animate);

    return () => {
      if (animationRef.current) {
        cancelAnimationFrame(animationRef.current);
      }
    };
  }, [isPlaying, frames, duration]);

  const drawPose = (frame: PoseFrame) => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    // Clear canvas
    ctx.clearRect(0, 0, canvas.width, canvas.height);

    const width = canvas.width;
    const height = canvas.height;

    // Draw connections
    POSE_CONNECTIONS.forEach(([start, end]) => {
      const startLandmark = frame.landmarks[start];
      const endLandmark = frame.landmarks[end];

      if (
        !startLandmark ||
        !endLandmark ||
        startLandmark.visibility < 0.5 ||
        endLandmark.visibility < 0.5
      ) {
        return;
      }

      const isHighlighted = highlightedConnections.some(
        ([s, e]) => (s === start && e === end) || (s === end && e === start)
      );

      ctx.beginPath();
      ctx.moveTo(startLandmark.x * width, startLandmark.y * height);
      ctx.lineTo(endLandmark.x * width, endLandmark.y * height);
      ctx.strokeStyle = isHighlighted
        ? "rgba(59, 130, 246, 0.9)" // blue-500
        : "rgba(255, 255, 255, 0.6)";
      ctx.lineWidth = isHighlighted ? 4 : 2;
      ctx.stroke();
    });

    // Draw landmarks
    frame.landmarks.forEach((landmark, index) => {
      if (!landmark || landmark.visibility < 0.5) return;

      const isHighlighted = highlightedPoints.includes(index);
      const x = landmark.x * width;
      const y = landmark.y * height;

      ctx.beginPath();
      ctx.arc(x, y, isHighlighted ? 6 : 3, 0, 2 * Math.PI);
      ctx.fillStyle = isHighlighted
        ? "rgba(59, 130, 246, 1)" // blue-500
        : "rgba(255, 255, 255, 0.8)";
      ctx.fill();

      // Add outer ring for highlighted points
      if (isHighlighted) {
        ctx.beginPath();
        ctx.arc(x, y, 10, 0, 2 * Math.PI);
        ctx.strokeStyle = "rgba(59, 130, 246, 0.5)";
        ctx.lineWidth = 2;
        ctx.stroke();
      }
    });
  };

  const togglePlayback = () => {
    if (isPlaying) {
      setIsPlaying(false);
    } else {
      startTimeRef.current = undefined;
      setIsPlaying(true);
    }
  };

  return (
    <div className="relative">
      <canvas
        ref={canvasRef}
        width={400}
        height={400}
        className="w-full h-auto bg-gradient-to-br from-slate-900 to-slate-800 rounded-lg"
      />
      <div className="absolute bottom-2 left-2 right-2 flex items-center gap-2">
        <button
          onClick={togglePlayback}
          className="bg-black/60 hover:bg-black/80 text-white px-3 py-1.5 rounded-md text-sm backdrop-blur-sm"
        >
          {isPlaying ? "Pause" : "Play"}
        </button>
        <div className="flex-1 bg-black/40 rounded-full h-1.5 overflow-hidden">
          <motion.div
            className="bg-blue-500 h-full rounded-full"
            initial={{ width: "0%" }}
            animate={{ width: `${(currentFrameIndex / frames.length) * 100}%` }}
            transition={{ duration: 0.1 }}
          />
        </div>
        <span className="text-white text-xs bg-black/60 px-2 py-1 rounded-md backdrop-blur-sm">
          {currentFrameIndex + 1}/{frames.length}
        </span>
      </div>
    </div>
  );
}
