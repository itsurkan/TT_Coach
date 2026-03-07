import { useState, useEffect, useRef } from "react";
import { Card } from "@/app/components/ui/card";
import { Button } from "@/app/components/ui/button";
import { Progress } from "@/app/components/ui/progress";
import { Drawer, DrawerContent } from "@/app/components/ui/drawer";
import { FeedbackItem, Feedback } from "@/app/components/FeedbackItem";
import { Pause, Play, X, Target, Zap, CheckCircle2, Camera, CameraOff, ChevronUp } from "lucide-react";
import {
  generateForehandPoseData,
  generateFollowThroughPoseData,
  generateContactPointPoseData,
} from "@/app/utils/mockPoseData";

interface TrainingSessionProps {
  onEndSession: () => void;
}

// Mock feedback data with pose animations
const mockFeedbacks: Feedback[] = [
  {
    id: "1",
    type: "success",
    message: "Perfect contact point!",
    details: "You made contact with the ball at the optimal point in front of your body. This allows for maximum control and power transfer.",
    poseData: {
      frames: generateContactPointPoseData(),
      duration: 0.9,
      highlightedPoints: [14, 16, 18], // Right arm: elbow, wrist, hand
      highlightedConnections: [
        [12, 14], // Shoulder to elbow
        [14, 16], // Elbow to wrist
        [16, 18], // Wrist to hand
      ],
    },
  },
  {
    id: "2",
    type: "success",
    message: "Good follow-through",
    details: "Excellent follow-through motion! Your racket continues upward after contact, which generates topspin and ensures consistency.",
    poseData: {
      frames: generateFollowThroughPoseData(),
      duration: 0.9,
      highlightedPoints: [12, 14, 16], // Right shoulder, elbow, wrist
      highlightedConnections: [
        [12, 14],
        [14, 16],
      ],
    },
  },
  {
    id: "3",
    type: "info",
    message: "Consistent pace - keep it up!",
    details: "You're maintaining a steady rhythm in your strokes. This consistency helps build muscle memory and improves accuracy over time.",
    poseData: {
      frames: generateForehandPoseData(),
      duration: 0.9,
      highlightedPoints: [11, 12, 13, 14, 15, 16], // Both arms and shoulders
      highlightedConnections: [
        [11, 13],
        [13, 15],
        [12, 14],
        [14, 16],
      ],
    },
  },
];

export function TrainingSession({ onEndSession }: TrainingSessionProps) {
  const [isActive, setIsActive] = useState(true);
  const [time, setTime] = useState(0);
  const [hits, setHits] = useState(0);
  const [accuracy, setAccuracy] = useState(0);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [cameraEnabled, setCameraEnabled] = useState(false);
  const [cameraError, setCameraError] = useState<string | null>(null);
  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);

  // Timer effect
  useEffect(() => {
    let interval: NodeJS.Timeout;
    if (isActive) {
      interval = setInterval(() => {
        setTime((prev) => prev + 1);
        // Simulate hits and accuracy increase
        if (Math.random() > 0.7) {
          setHits((prev) => prev + 1);
          setAccuracy(Math.min(95, Math.random() * 100));
        }
      }, 1000);
    }
    return () => clearInterval(interval);
  }, [isActive]);

  // Camera access effect
  useEffect(() => {
    const startCamera = async () => {
      try {
        const stream = await navigator.mediaDevices.getUserMedia({
          video: { facingMode: "user", width: { ideal: 1280 }, height: { ideal: 720 } },
          audio: false,
        });
        
        if (videoRef.current) {
          videoRef.current.srcObject = stream;
          streamRef.current = stream;
          setCameraEnabled(true);
          setCameraError(null);
        }
      } catch (error) {
        console.error("Error accessing camera:", error);
        setCameraError("Camera access denied or not available");
        setCameraEnabled(false);
      }
    };

    startCamera();

    // Cleanup function
    return () => {
      if (streamRef.current) {
        streamRef.current.getTracks().forEach((track) => track.stop());
      }
    };
  }, []);

  const formatTime = (seconds: number) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins.toString().padStart(2, "0")}:${secs.toString().padStart(2, "0")}`;
  };

  const toggleCamera = () => {
    if (streamRef.current) {
      const videoTrack = streamRef.current.getVideoTracks()[0];
      if (videoTrack) {
        videoTrack.enabled = !videoTrack.enabled;
        setCameraEnabled(videoTrack.enabled);
      }
    }
  };

  return (
    <div className="h-full relative bg-black">
      {/* Camera Video Feed */}
      <div className="absolute inset-0">
        {cameraError ? (
          <div className="w-full h-full flex items-center justify-center bg-muted/20">
            <div className="text-center p-8">
              <CameraOff className="w-16 h-16 mx-auto mb-4 text-muted-foreground" />
              <p className="text-muted-foreground mb-2">{cameraError}</p>
              <p className="text-sm text-muted-foreground/70">
                Training can continue without camera
              </p>
            </div>
          </div>
        ) : (
          <video
            ref={videoRef}
            autoPlay
            playsInline
            muted
            className="w-full h-full object-cover"
          />
        )}
      </div>

      {/* Overlay Controls */}
      <div className="absolute top-0 left-0 right-0 p-4 bg-gradient-to-b from-black/60 to-transparent z-10">
        <div className="flex items-center justify-between max-w-md mx-auto">
          <Button
            variant="ghost"
            size="icon"
            onClick={onEndSession}
            className="bg-black/40 hover:bg-black/60 text-white"
          >
            <X className="w-5 h-5" />
          </Button>
          
          <div className="bg-black/40 backdrop-blur-sm px-4 py-2 rounded-full">
            <span className="text-white font-mono text-lg">{formatTime(time)}</span>
          </div>

          <Button
            variant="ghost"
            size="icon"
            onClick={toggleCamera}
            className="bg-black/40 hover:bg-black/60 text-white"
          >
            {cameraEnabled ? (
              <Camera className="w-5 h-5" />
            ) : (
              <CameraOff className="w-5 h-5" />
            )}
          </Button>
        </div>
      </div>

      {/* Quick Stats Overlay - Top corners */}
      <div className="absolute top-20 left-4 right-4 flex justify-between max-w-md mx-auto z-10">
        <div className="bg-black/60 backdrop-blur-sm px-4 py-3 rounded-xl">
          <div className="text-white/70 text-xs mb-1">Hits</div>
          <div className="text-white text-2xl font-bold">{hits}</div>
        </div>
        
        <div className="bg-black/60 backdrop-blur-sm px-4 py-3 rounded-xl">
          <div className="text-white/70 text-xs mb-1">Accuracy</div>
          <div className="text-white text-2xl font-bold">{Math.round(accuracy)}%</div>
        </div>
      </div>

      {/* Pause/Resume Button - Center */}
      <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 z-10">
        <Button
          onClick={() => setIsActive(!isActive)}
          size="lg"
          className="bg-black/60 hover:bg-black/80 text-white backdrop-blur-sm w-20 h-20 rounded-full"
        >
          {isActive ? (
            <Pause className="w-8 h-8" />
          ) : (
            <Play className="w-8 h-8 ml-1" />
          )}
        </Button>
      </div>

      {/* Collapsed indicator at bottom */}
      {!drawerOpen && (
        <div className="absolute bottom-4 left-1/2 -translate-x-1/2 z-10">
          <button
            onClick={() => setDrawerOpen(true)}
            className="bg-black/60 backdrop-blur-sm text-white px-6 py-3 rounded-full flex items-center gap-2 hover:bg-black/80 transition-colors"
          >
            <span className="text-sm">View Details</span>
            <ChevronUp className="w-4 h-4" />
          </button>
        </div>
      )}

      {/* Draggable Bottom Drawer */}
      <Drawer open={drawerOpen} onOpenChange={setDrawerOpen}>
        <DrawerContent className="max-h-[85vh]">
          <div className="overflow-auto p-4 space-y-4 max-w-md mx-auto w-full">
            {/* Session Controls */}
            <div className="flex gap-3">
              <Button
                onClick={() => setIsActive(!isActive)}
                className="flex-1"
                variant={isActive ? "outline" : "default"}
              >
                {isActive ? (
                  <>
                    <Pause className="w-5 h-5 mr-2" />
                    Pause
                  </>
                ) : (
                  <>
                    <Play className="w-5 h-5 mr-2" />
                    Resume
                  </>
                )}
              </Button>
              <Button
                onClick={onEndSession}
                variant="destructive"
                className="flex-1"
              >
                End Session
              </Button>
            </div>

            {/* Live Stats - Big Numbers */}
            <div className="grid grid-cols-2 gap-3">
              <Card className="p-5 text-center">
                <div className="w-12 h-12 rounded-full bg-green-500/10 flex items-center justify-center mx-auto mb-3">
                  <Target className="w-6 h-6 text-green-500" />
                </div>
                <div className="text-4xl font-bold mb-1">{hits}</div>
                <div className="text-sm text-muted-foreground">Total Hits</div>
              </Card>

              <Card className="p-5 text-center">
                <div className="w-12 h-12 rounded-full bg-orange-500/10 flex items-center justify-center mx-auto mb-3">
                  <Zap className="w-6 h-6 text-orange-500" />
                </div>
                <div className="text-4xl font-bold mb-1">{Math.round(accuracy)}%</div>
                <div className="text-sm text-muted-foreground">Accuracy</div>
              </Card>
            </div>

            {/* Current Drill */}
            <Card className="p-4">
              <div className="flex items-center gap-3 mb-4">
                <div className="w-10 h-10 rounded-full bg-blue-500/10 flex items-center justify-center">
                  <CheckCircle2 className="w-5 h-5 text-blue-500" />
                </div>
                <div>
                  <h3 className="font-semibold">Forehand Drive</h3>
                  <p className="text-sm text-muted-foreground">Focus: Consistency</p>
                </div>
              </div>
              <Progress value={65} className="h-2 mb-2" />
              <p className="text-sm text-muted-foreground">13/20 successful hits</p>
            </Card>

            {/* AI Coaching Tips */}
            <Card className="p-4 bg-gradient-to-r from-purple-500/10 to-blue-500/10 border-purple-500/20">
              <h4 className="font-semibold mb-2 flex items-center gap-2">
                <Zap className="w-4 h-4 text-purple-500" />
                AI Coach Tip
              </h4>
              <p className="text-sm text-muted-foreground leading-relaxed">
                Great rhythm! Try to follow through more on your forehand. 
                Keep your elbow close to your body for better control.
              </p>
            </Card>

            {/* Real-time Feedback */}
            <div className="space-y-2">
              {mockFeedbacks.map((feedback) => (
                <FeedbackItem key={feedback.id} feedback={feedback} />
              ))}
            </div>
          </div>
        </DrawerContent>
      </Drawer>
    </div>
  );
}