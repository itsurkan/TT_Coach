import { useState } from "react";
import { Card } from "@/app/components/ui/card";
import { Switch } from "@/app/components/ui/switch";
import { Slider } from "@/app/components/ui/slider";
import { Label } from "@/app/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/app/components/ui/select";
import { Volume2, Video, Eye } from "lucide-react";

export function SettingsView() {
  const [audioEnabled, setAudioEnabled] = useState(true);
  const [volume, setVolume] = useState([75]);
  const [cameraQuality, setCameraQuality] = useState("720p");
  const [fps, setFps] = useState("30");
  const [showPoseSkeleton, setShowPoseSkeleton] = useState(true);

  return (
    <div className="space-y-6 pb-6">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold mb-1">Training Settings</h1>
        <p className="text-sm text-muted-foreground">
          Configure your training session preferences
        </p>
      </div>

      {/* Audio Feedback Settings */}
      <div>
        <h3 className="font-semibold mb-3 flex items-center gap-2">
          <Volume2 className="w-5 h-5 text-blue-500" />
          Audio Feedback
        </h3>
        <Card className="divide-y divide-border">
          <div className="p-4 flex items-center justify-between">
            <div>
              <Label htmlFor="audio-enabled" className="font-medium">
                Enable Audio Feedback
              </Label>
              <p className="text-sm text-muted-foreground mt-1">
                Receive audio cues during training
              </p>
            </div>
            <Switch
              id="audio-enabled"
              checked={audioEnabled}
              onCheckedChange={setAudioEnabled}
            />
          </div>

          <div className="p-4">
            <div className="flex items-center justify-between mb-3">
              <Label htmlFor="volume" className="font-medium">
                Volume
              </Label>
              <span className="text-sm text-muted-foreground">{volume[0]}%</span>
            </div>
            <Slider
              id="volume"
              value={volume}
              onValueChange={setVolume}
              max={100}
              step={1}
              disabled={!audioEnabled}
              className="w-full"
            />
            <p className="text-xs text-muted-foreground mt-2">
              Adjust the volume of voice feedback and sound effects
            </p>
          </div>
        </Card>
      </div>

      {/* Camera Settings */}
      <div>
        <h3 className="font-semibold mb-3 flex items-center gap-2">
          <Video className="w-5 h-5 text-purple-500" />
          Camera Settings
        </h3>
        <Card className="divide-y divide-border">
          <div className="p-4">
            <Label htmlFor="quality" className="font-medium">
              Video Quality
            </Label>
            <p className="text-sm text-muted-foreground mt-1 mb-3">
              Higher quality improves pose detection accuracy
            </p>
            <Select value={cameraQuality} onValueChange={setCameraQuality}>
              <SelectTrigger id="quality">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="480p">480p - Low (Saves battery)</SelectItem>
                <SelectItem value="720p">720p - Medium (Recommended)</SelectItem>
                <SelectItem value="1080p">1080p - High</SelectItem>
                <SelectItem value="4k">4K - Ultra (Requires powerful device)</SelectItem>
              </SelectContent>
            </Select>
          </div>

          <div className="p-4">
            <Label htmlFor="fps" className="font-medium">
              Frame Rate (FPS)
            </Label>
            <p className="text-sm text-muted-foreground mt-1 mb-3">
              Higher frame rates capture faster movements
            </p>
            <Select value={fps} onValueChange={setFps}>
              <SelectTrigger id="fps">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="24">24 FPS - Standard</SelectItem>
                <SelectItem value="30">30 FPS - Recommended</SelectItem>
                <SelectItem value="60">60 FPS - Smooth</SelectItem>
                <SelectItem value="120">120 FPS - High-speed (Premium)</SelectItem>
              </SelectContent>
            </Select>
          </div>

          <div className="p-4 flex items-center justify-between">
            <div>
              <Label htmlFor="skeleton" className="font-medium flex items-center gap-2">
                <Eye className="w-4 h-4" />
                Show Pose Skeleton
              </Label>
              <p className="text-sm text-muted-foreground mt-1">
                Overlay skeleton visualization during training
              </p>
            </div>
            <Switch
              id="skeleton"
              checked={showPoseSkeleton}
              onCheckedChange={setShowPoseSkeleton}
            />
          </div>
        </Card>
      </div>

      {/* Info Card */}
      <Card className="p-4 bg-blue-500/10 border-blue-500/20">
        <h4 className="font-semibold mb-2 text-sm">💡 Pro Tip</h4>
        <p className="text-sm text-muted-foreground leading-relaxed">
          For best results, use 720p at 30 FPS with pose skeleton enabled. 
          This balance provides accurate feedback without draining your battery.
        </p>
      </Card>
    </div>
  );
}
