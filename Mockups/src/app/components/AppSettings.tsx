import { Card } from "@/app/components/ui/card";
import { Switch } from "@/app/components/ui/switch";
import { Label } from "@/app/components/ui/label";
import { useAppSettings } from "@/app/contexts/AppSettingsContext";
import { Bug, Crown, AlertCircle, CheckCircle2 } from "lucide-react";

export function AppSettingsView() {
  const { debugMode, setDebugMode, isSubscribed, setIsSubscribed, subscriptionEndDate } = useAppSettings();

  return (
    <div className="space-y-6 pb-6">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold mb-1">App Settings</h1>
        <p className="text-sm text-muted-foreground">
          Configure application-wide settings
        </p>
      </div>

      {/* Debug Mode */}
      <div>
        <h3 className="font-semibold mb-3 flex items-center gap-2">
          <Bug className="w-5 h-5 text-orange-500" />
          Developer Options
        </h3>
        <Card className="divide-y divide-border">
          <div className="p-4 flex items-center justify-between">
            <div>
              <Label htmlFor="debug-mode" className="font-medium">
                Debug Mode
              </Label>
              <p className="text-sm text-muted-foreground mt-1">
                Show debug activity in navigation for testing
              </p>
            </div>
            <Switch
              id="debug-mode"
              checked={debugMode}
              onCheckedChange={setDebugMode}
            />
          </div>
        </Card>
        
        {debugMode && (
          <Card className="p-4 mt-3 bg-orange-500/10 border-orange-500/20">
            <div className="flex items-center gap-2 mb-2">
              <AlertCircle className="w-4 h-4 text-orange-500" />
              <h4 className="font-semibold text-sm">Debug Mode Active</h4>
            </div>
            <p className="text-sm text-muted-foreground">
              A new "Debug" tab will appear in the bottom navigation. Use it to test features and view diagnostic information.
            </p>
          </Card>
        )}
      </div>

      {/* Subscription Management */}
      <div>
        <h3 className="font-semibold mb-3 flex items-center gap-2">
          <Crown className="w-5 h-5 text-yellow-500" />
          Subscription Management
        </h3>
        <Card className="divide-y divide-border">
          <div className="p-4 flex items-center justify-between">
            <div>
              <Label htmlFor="subscription-status" className="font-medium">
                Subscription Active
              </Label>
              <p className="text-sm text-muted-foreground mt-1">
                Simulate premium subscription status
              </p>
            </div>
            <Switch
              id="subscription-status"
              checked={isSubscribed}
              onCheckedChange={setIsSubscribed}
            />
          </div>
          
          <div className="p-4">
            <div className="flex items-center gap-2 mb-2">
              {isSubscribed ? (
                <>
                  <CheckCircle2 className="w-4 h-4 text-green-500" />
                  <span className="font-medium text-sm">Premium Active</span>
                </>
              ) : (
                <>
                  <AlertCircle className="w-4 h-4 text-red-500" />
                  <span className="font-medium text-sm">Free Plan</span>
                </>
              )}
            </div>
            <p className="text-sm text-muted-foreground">
              {isSubscribed
                ? `Your premium subscription is active until ${subscriptionEndDate}.`
                : "You're on the free plan. Upgrade to unlock all features."}
            </p>
          </div>
        </Card>
        
        <Card className="p-4 mt-3 bg-blue-500/10 border-blue-500/20">
          <h4 className="font-semibold mb-2 text-sm">ℹ️ How it works</h4>
          <ul className="text-sm text-muted-foreground space-y-1.5 leading-relaxed">
            <li>• When subscription is <strong>active</strong>: Full access to training and drills</li>
            <li>• When subscription is <strong>inactive</strong>: Starting training or drills will redirect to subscription page</li>
            <li>• This setting affects the subscription status shown in your Profile</li>
          </ul>
        </Card>
      </div>
    </div>
  );
}
