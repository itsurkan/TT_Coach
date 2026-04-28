import { PoseFrame } from "@/app/components/PoseVisualizer";

// Generate mock pose data for different movements
export function generateForehandPoseData(): PoseFrame[] {
  const frames: PoseFrame[] = [];
  const numFrames = 10;
  
  for (let i = 0; i < numFrames; i++) {
    const progress = i / (numFrames - 1);
    
    // Simulate a forehand stroke motion
    // Right arm moves from back to front
    const armSwing = Math.sin(progress * Math.PI) * 0.3;
    const bodyRotation = progress * 0.1;
    
    frames.push({
      timestamp: progress * 0.9,
      landmarks: [
        // 0-10: Face and head (simplified)
        { x: 0.5, y: 0.15, z: 0, visibility: 1 },
        { x: 0.48, y: 0.14, z: 0, visibility: 1 },
        { x: 0.47, y: 0.13, z: 0, visibility: 1 },
        { x: 0.46, y: 0.14, z: 0, visibility: 1 },
        { x: 0.52, y: 0.14, z: 0, visibility: 1 },
        { x: 0.53, y: 0.13, z: 0, visibility: 1 },
        { x: 0.54, y: 0.14, z: 0, visibility: 1 },
        { x: 0.45, y: 0.15, z: 0, visibility: 1 },
        { x: 0.55, y: 0.15, z: 0, visibility: 1 },
        { x: 0.49, y: 0.18, z: 0, visibility: 1 },
        { x: 0.51, y: 0.18, z: 0, visibility: 1 },
        
        // 11-12: Shoulders
        { x: 0.42 - bodyRotation, y: 0.35, z: 0, visibility: 1 }, // Left shoulder
        { x: 0.58 + bodyRotation, y: 0.35, z: 0, visibility: 1 }, // Right shoulder
        
        // 13-14: Elbows
        { x: 0.35 - bodyRotation, y: 0.45, z: 0, visibility: 1 }, // Left elbow
        { x: 0.65 + armSwing, y: 0.42, z: 0, visibility: 1 }, // Right elbow (swinging)
        
        // 15-16: Wrists
        { x: 0.32 - bodyRotation, y: 0.55, z: 0, visibility: 1 }, // Left wrist
        { x: 0.70 + armSwing, y: 0.50, z: 0, visibility: 1 }, // Right wrist (swinging)
        
        // 17-22: Hands (simplified)
        { x: 0.30 - bodyRotation, y: 0.58, z: 0, visibility: 1 },
        { x: 0.72 + armSwing, y: 0.52, z: 0, visibility: 1 },
        { x: 0.31 - bodyRotation, y: 0.59, z: 0, visibility: 1 },
        { x: 0.71 + armSwing, y: 0.51, z: 0, visibility: 1 },
        { x: 0.32 - bodyRotation, y: 0.57, z: 0, visibility: 1 },
        { x: 0.73 + armSwing, y: 0.53, z: 0, visibility: 1 },
        
        // 23-24: Hips
        { x: 0.43, y: 0.60, z: 0, visibility: 1 }, // Left hip
        { x: 0.57, y: 0.60, z: 0, visibility: 1 }, // Right hip
        
        // 25-26: Knees
        { x: 0.42, y: 0.75, z: 0, visibility: 1 }, // Left knee
        { x: 0.58, y: 0.75, z: 0, visibility: 1 }, // Right knee
        
        // 27-28: Ankles
        { x: 0.41, y: 0.90, z: 0, visibility: 1 }, // Left ankle
        { x: 0.59, y: 0.90, z: 0, visibility: 1 }, // Right ankle
        
        // 29-32: Feet (simplified)
        { x: 0.40, y: 0.95, z: 0, visibility: 1 },
        { x: 0.60, y: 0.95, z: 0, visibility: 1 },
        { x: 0.42, y: 0.96, z: 0, visibility: 1 },
        { x: 0.58, y: 0.96, z: 0, visibility: 1 },
      ],
    });
  }
  
  return frames;
}

export function generateFollowThroughPoseData(): PoseFrame[] {
  const frames: PoseFrame[] = [];
  const numFrames = 10;
  
  for (let i = 0; i < numFrames; i++) {
    const progress = i / (numFrames - 1);
    
    // Simulate follow-through motion - arm continues upward
    const armLift = progress * 0.25;
    const shoulderRotation = progress * 0.15;
    
    frames.push({
      timestamp: progress * 0.9,
      landmarks: [
        // Head
        { x: 0.5, y: 0.15, z: 0, visibility: 1 },
        { x: 0.48, y: 0.14, z: 0, visibility: 1 },
        { x: 0.47, y: 0.13, z: 0, visibility: 1 },
        { x: 0.46, y: 0.14, z: 0, visibility: 1 },
        { x: 0.52, y: 0.14, z: 0, visibility: 1 },
        { x: 0.53, y: 0.13, z: 0, visibility: 1 },
        { x: 0.54, y: 0.14, z: 0, visibility: 1 },
        { x: 0.45, y: 0.15, z: 0, visibility: 1 },
        { x: 0.55, y: 0.15, z: 0, visibility: 1 },
        { x: 0.49, y: 0.18, z: 0, visibility: 1 },
        { x: 0.51, y: 0.18, z: 0, visibility: 1 },
        
        // Shoulders
        { x: 0.40, y: 0.35, z: 0, visibility: 1 },
        { x: 0.60 + shoulderRotation, y: 0.33, z: 0, visibility: 1 }, // Right shoulder rotating
        
        // Elbows
        { x: 0.35, y: 0.45, z: 0, visibility: 1 },
        { x: 0.68, y: 0.32 - armLift, z: 0, visibility: 1 }, // Right elbow lifting
        
        // Wrists
        { x: 0.32, y: 0.55, z: 0, visibility: 1 },
        { x: 0.65, y: 0.25 - armLift, z: 0, visibility: 1 }, // Right wrist following through
        
        // Hands
        { x: 0.30, y: 0.58, z: 0, visibility: 1 },
        { x: 0.63, y: 0.22 - armLift, z: 0, visibility: 1 },
        { x: 0.31, y: 0.59, z: 0, visibility: 1 },
        { x: 0.64, y: 0.23 - armLift, z: 0, visibility: 1 },
        { x: 0.32, y: 0.57, z: 0, visibility: 1 },
        { x: 0.66, y: 0.24 - armLift, z: 0, visibility: 1 },
        
        // Hips
        { x: 0.43, y: 0.60, z: 0, visibility: 1 },
        { x: 0.57, y: 0.60, z: 0, visibility: 1 },
        
        // Knees
        { x: 0.42, y: 0.75, z: 0, visibility: 1 },
        { x: 0.58, y: 0.75, z: 0, visibility: 1 },
        
        // Ankles
        { x: 0.41, y: 0.90, z: 0, visibility: 1 },
        { x: 0.59, y: 0.90, z: 0, visibility: 1 },
        
        // Feet
        { x: 0.40, y: 0.95, z: 0, visibility: 1 },
        { x: 0.60, y: 0.95, z: 0, visibility: 1 },
        { x: 0.42, y: 0.96, z: 0, visibility: 1 },
        { x: 0.58, y: 0.96, z: 0, visibility: 1 },
      ],
    });
  }
  
  return frames;
}

export function generateContactPointPoseData(): PoseFrame[] {
  const frames: PoseFrame[] = [];
  const numFrames = 10;
  
  for (let i = 0; i < numFrames; i++) {
    const progress = i / (numFrames - 1);
    
    // Contact point - moment of impact, slight wrist rotation
    const wristRotation = Math.sin(progress * Math.PI) * 0.05;
    const impact = progress === 0.5 ? 0.02 : 0;
    
    frames.push({
      timestamp: progress * 0.9,
      landmarks: [
        // Head
        { x: 0.5, y: 0.15, z: 0, visibility: 1 },
        { x: 0.48, y: 0.14, z: 0, visibility: 1 },
        { x: 0.47, y: 0.13, z: 0, visibility: 1 },
        { x: 0.46, y: 0.14, z: 0, visibility: 1 },
        { x: 0.52, y: 0.14, z: 0, visibility: 1 },
        { x: 0.53, y: 0.13, z: 0, visibility: 1 },
        { x: 0.54, y: 0.14, z: 0, visibility: 1 },
        { x: 0.45, y: 0.15, z: 0, visibility: 1 },
        { x: 0.55, y: 0.15, z: 0, visibility: 1 },
        { x: 0.49, y: 0.18, z: 0, visibility: 1 },
        { x: 0.51, y: 0.18, z: 0, visibility: 1 },
        
        // Shoulders
        { x: 0.42, y: 0.35, z: 0, visibility: 1 },
        { x: 0.58, y: 0.35, z: 0, visibility: 1 },
        
        // Elbows
        { x: 0.35, y: 0.45, z: 0, visibility: 1 },
        { x: 0.68 + impact, y: 0.42, z: 0, visibility: 1 },
        
        // Wrists - showing rotation at contact
        { x: 0.32, y: 0.55, z: 0, visibility: 1 },
        { x: 0.72 + wristRotation + impact, y: 0.50, z: 0, visibility: 1 },
        
        // Hands
        { x: 0.30, y: 0.58, z: 0, visibility: 1 },
        { x: 0.74 + wristRotation + impact, y: 0.51, z: 0, visibility: 1 },
        { x: 0.31, y: 0.59, z: 0, visibility: 1 },
        { x: 0.73 + wristRotation + impact, y: 0.50, z: 0, visibility: 1 },
        { x: 0.32, y: 0.57, z: 0, visibility: 1 },
        { x: 0.75 + wristRotation + impact, y: 0.52, z: 0, visibility: 1 },
        
        // Hips
        { x: 0.43, y: 0.60, z: 0, visibility: 1 },
        { x: 0.57, y: 0.60, z: 0, visibility: 1 },
        
        // Knees
        { x: 0.42, y: 0.75, z: 0, visibility: 1 },
        { x: 0.58, y: 0.75, z: 0, visibility: 1 },
        
        // Ankles
        { x: 0.41, y: 0.90, z: 0, visibility: 1 },
        { x: 0.59, y: 0.90, z: 0, visibility: 1 },
        
        // Feet
        { x: 0.40, y: 0.95, z: 0, visibility: 1 },
        { x: 0.60, y: 0.95, z: 0, visibility: 1 },
        { x: 0.42, y: 0.96, z: 0, visibility: 1 },
        { x: 0.58, y: 0.96, z: 0, visibility: 1 },
      ],
    });
  }
  
  return frames;
}
