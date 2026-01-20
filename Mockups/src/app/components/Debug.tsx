import { Card } from "@/app/components/ui/card";
import { Button } from "@/app/components/ui/button";
import { useAppSettings } from "@/app/contexts/AppSettingsContext";
import { Bug, Info, CheckCircle2, XCircle, Crown, Eye } from "lucide-react";

export function DebugView() {
  const { debugMode, isSubscribed, subscriptionEndDate } = useAppSettings();

  const systemInfo = [
    { label: "App Version", value: "1.0.0" },
    { label: "Build", value: "20260120.1" },
    { label: "Environment", value: "Development" },
    { label: "Theme", value: "System" },
  ];

  const features = [
    { name: "Camera Access", status: "Enabled", available: true },
    { name: "AI Coaching", status: isSubscribed ? "Active" : "Locked", available: isSubscribed },
    { name: "Pose Detection", status: "Ready", available: true },
    { name: "Audio Feedback", status: "Enabled", available: true },
    { name: "Premium Drills", status: isSubscribed ? "Unlocked" : "Locked", available: isSubscribed },
  ];

  return (
    <div className="space-y-6 pb-6">
      {/* Header */}
      <div className="flex items-center gap-3">
        <div className="w-12 h-12 rounded-full bg-orange-500/10 flex items-center justify-center">
          <Bug className="w-6 h-6 text-orange-500" />
        </div>
        <div>
          <h1 className="text-2xl font-bold">Debug Console</h1>
          <p className="text-sm text-muted-foreground">Development tools and diagnostics</p>
        </div>
      </div>

      {/* Debug Status */}
      <Card className="p-4 bg-orange-500/10 border-orange-500/20">
        <div className="flex items-center gap-2 mb-2">
          <Eye className="w-4 h-4 text-orange-500" />
          <h3 className="font-semibold text-sm">Debug Mode Status</h3>
        </div>
        <p className="text-sm text-muted-foreground">
          Debug mode is currently <strong>enabled</strong>. Disable it in App Settings to hide this tab.
        </p>
      </Card>

      {/* Subscription Info */}
      <div>
        <h3 className="font-semibold mb-3 flex items-center gap-2">
          <Crown className="w-5 h-5 text-yellow-500" />
          Subscription Status
        </h3>
        <Card className="p-4">
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <span className="text-sm text-muted-foreground">Status</span>
              <div className="flex items-center gap-2">
                {isSubscribed ? (
                  <>
                    <CheckCircle2 className="w-4 h-4 text-green-500" />
                    <span className="text-sm font-medium text-green-600 dark:text-green-400">Premium</span>
                  </>
                ) : (
                  <>
                    <XCircle className="w-4 h-4 text-red-500" />
                    <span className="text-sm font-medium text-red-600 dark:text-red-400">Free</span>
                  </>
                )}
              </div>
            </div>
            {isSubscribed && (
              <div className="flex items-center justify-between">
                <span className="text-sm text-muted-foreground">Expires</span>
                <span className="text-sm font-medium">{subscriptionEndDate}</span>
              </div>
            )}
            <div className="flex items-center justify-between">
              <span className="text-sm text-muted-foreground">Paywall Active</span>
              <span className="text-sm font-medium">{isSubscribed ? "No" : "Yes"}</span>
            </div>
          </div>
        </Card>
      </div>

      {/* System Information */}
      <div>
        <h3 className="font-semibold mb-3 flex items-center gap-2">
          <Info className="w-5 h-5 text-blue-500" />
          System Information
        </h3>
        <Card className="divide-y divide-border">
          {systemInfo.map((info, index) => (
            <div key={index} className="p-4 flex items-center justify-between">
              <span className="text-sm text-muted-foreground">{info.label}</span>
              <span className="text-sm font-medium font-mono">{info.value}</span>
            </div>
          ))}
        </Card>
      </div>

      {/* Feature Flags */}
      <div>
        <h3 className="font-semibold mb-3">Feature Status</h3>
        <Card className="divide-y divide-border">
          {features.map((feature, index) => (
            <div key={index} className="p-4 flex items-center justify-between">
              <span className="text-sm">{feature.name}</span>
              <div className="flex items-center gap-2">
                {feature.available ? (
                  <CheckCircle2 className="w-4 h-4 text-green-500" />
                ) : (
                  <XCircle className="w-4 h-4 text-red-500" />
                )}
                <span className={`text-sm font-medium ${
                  feature.available 
                    ? "text-green-600 dark:text-green-400" 
                    : "text-red-600 dark:text-red-400"
                }`}>
                  {feature.status}
                </span>
              </div>
            </div>
          ))}
        </Card>
      </div>

      {/* Test Actions */}
      <div>
        <h3 className="font-semibold mb-3">Test Actions</h3>
        <Card className="p-4 space-y-3">
          <Button variant="outline" className="w-full justify-start">
            <Bug className="w-4 h-4 mr-2" />
            Trigger Test Notification
          </Button>
          <Button variant="outline" className="w-full justify-start">
            <Info className="w-4 h-4 mr-2" />
            View Error Logs
          </Button>
          <Button variant="outline" className="w-full justify-start text-red-600 dark:text-red-400 hover:text-red-600 dark:hover:text-red-400">
            <XCircle className="w-4 h-4 mr-2" />
            Clear All Data
          </Button>
        </Card>
      </div>
    </div>
  );
}
