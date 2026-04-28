import { useState } from "react";
import { motion, AnimatePresence } from "motion/react";
import { ChevronDown, CheckCircle2, AlertCircle, Info } from "lucide-react";
import { PoseVisualizer, PoseFrame } from "@/app/components/PoseVisualizer";

export type FeedbackType = "success" | "warning" | "info";

export interface Feedback {
  id: string;
  type: FeedbackType;
  message: string;
  details?: string;
  poseData?: {
    frames: PoseFrame[];
    duration: number;
    highlightedPoints?: number[];
    highlightedConnections?: [number, number][];
  };
}

interface FeedbackItemProps {
  feedback: Feedback;
}

export function FeedbackItem({ feedback }: FeedbackItemProps) {
  const [isExpanded, setIsExpanded] = useState(false);

  const getIcon = () => {
    switch (feedback.type) {
      case "success":
        return <CheckCircle2 className="w-4 h-4 text-green-500 flex-shrink-0" />;
      case "warning":
        return <AlertCircle className="w-4 h-4 text-orange-500 flex-shrink-0" />;
      case "info":
        return <Info className="w-4 h-4 text-blue-500 flex-shrink-0" />;
    }
  };

  const getBackgroundClass = () => {
    switch (feedback.type) {
      case "success":
        return "bg-green-500/10 hover:bg-green-500/20";
      case "warning":
        return "bg-orange-500/10 hover:bg-orange-500/20";
      case "info":
        return "bg-blue-500/10 hover:bg-blue-500/20";
    }
  };

  return (
    <div className="overflow-hidden">
      <button
        onClick={() => setIsExpanded(!isExpanded)}
        className={`w-full flex items-center gap-2 p-3 rounded-lg transition-colors ${getBackgroundClass()}`}
      >
        {getIcon()}
        <span className="text-sm flex-1 text-left">{feedback.message}</span>
        {feedback.poseData && (
          <motion.div
            animate={{ rotate: isExpanded ? 180 : 0 }}
            transition={{ duration: 0.2 }}
          >
            <ChevronDown className="w-4 h-4 text-muted-foreground" />
          </motion.div>
        )}
      </button>

      <AnimatePresence>
        {isExpanded && feedback.poseData && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: "auto", opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.3, ease: "easeInOut" }}
            className="overflow-hidden"
          >
            <div className="pt-3 px-2 space-y-3">
              {feedback.details && (
                <div className="text-sm text-muted-foreground leading-relaxed p-3 bg-muted/50 rounded-lg">
                  {feedback.details}
                </div>
              )}
              
              <div className="p-3 bg-card rounded-lg border border-border">
                <div className="mb-2">
                  <h4 className="text-sm font-semibold mb-1">Movement Analysis</h4>
                  <p className="text-xs text-muted-foreground">
                    Highlighted areas show the movement pattern
                  </p>
                </div>
                <PoseVisualizer
                  frames={feedback.poseData.frames}
                  duration={feedback.poseData.duration}
                  highlightedPoints={feedback.poseData.highlightedPoints}
                  highlightedConnections={feedback.poseData.highlightedConnections}
                  autoPlay={true}
                />
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
