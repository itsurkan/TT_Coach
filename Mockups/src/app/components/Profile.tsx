import { Card } from "@/app/components/ui/card";
import { Button } from "@/app/components/ui/button";
import { Switch } from "@/app/components/ui/switch";
import { Avatar, AvatarFallback } from "@/app/components/ui/avatar";
import { useTheme } from "next-themes";
import { 
  User, 
  Bell, 
  Target, 
  Award, 
  Settings, 
  HelpCircle,
  LogOut,
  ChevronRight,
  Trophy,
  Sun,
  Moon,
  Monitor
} from "lucide-react";

export function ProfileView() {
  const { theme, setTheme } = useTheme();

  return (
    <div className="space-y-6 pb-6">
      {/* Profile Header */}
      <Card className="p-6">
        <div className="flex items-center gap-4 mb-4">
          <Avatar className="w-16 h-16">
            <AvatarFallback className="bg-gradient-to-br from-blue-500 to-blue-600 text-white text-xl">
              JD
            </AvatarFallback>
          </Avatar>
          <div className="flex-1">
            <h2 className="text-xl font-semibold mb-1">John Doe</h2>
            <p className="text-sm text-muted-foreground">Intermediate Player</p>
          </div>
        </div>
        <div className="grid grid-cols-3 gap-3 pt-4 border-t border-border">
          <div className="text-center">
            <div className="text-xl font-bold">86</div>
            <div className="text-xs text-muted-foreground">Skill Score</div>
          </div>
          <div className="text-center">
            <div className="text-xl font-bold">24</div>
            <div className="text-xs text-muted-foreground">Day Streak</div>
          </div>
          <div className="text-center">
            <div className="text-xl font-bold">12</div>
            <div className="text-xs text-muted-foreground">Achievements</div>
          </div>
        </div>
      </Card>

      {/* Training Goals */}
      <div>
        <h3 className="font-semibold mb-3">Training Goals</h3>
        <Card className="p-4">
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-lg bg-blue-500/10 flex items-center justify-center">
                <Target className="w-5 h-5 text-blue-500" />
              </div>
              <div>
                <div className="font-medium">Weekly Sessions</div>
                <div className="text-sm text-muted-foreground">Train 7 days/week</div>
              </div>
            </div>
            <ChevronRight className="w-5 h-5 text-muted-foreground" />
          </div>
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-lg bg-green-500/10 flex items-center justify-center">
                <Trophy className="w-5 h-5 text-green-500" />
              </div>
              <div>
                <div className="font-medium">Skill Target</div>
                <div className="text-sm text-muted-foreground">Reach 90 score</div>
              </div>
            </div>
            <ChevronRight className="w-5 h-5 text-muted-foreground" />
          </div>
        </Card>
      </div>

      {/* Settings */}
      <div>
        <h3 className="font-semibold mb-3">Settings</h3>
        <Card className="divide-y divide-border">
          {/* Theme Setting */}
          <div className="p-4">
            <div className="flex items-center gap-3 mb-3">
              <Sun className="w-5 h-5 text-muted-foreground" />
              <span>Theme</span>
            </div>
            <div className="grid grid-cols-3 gap-2">
              <button
                onClick={() => setTheme("light")}
                className={`p-3 rounded-lg border transition-all ${
                  theme === "light"
                    ? "bg-primary text-primary-foreground border-primary"
                    : "border-border hover:bg-accent"
                }`}
              >
                <Sun className="w-5 h-5 mx-auto mb-1" />
                <div className="text-xs">Light</div>
              </button>
              <button
                onClick={() => setTheme("dark")}
                className={`p-3 rounded-lg border transition-all ${
                  theme === "dark"
                    ? "bg-primary text-primary-foreground border-primary"
                    : "border-border hover:bg-accent"
                }`}
              >
                <Moon className="w-5 h-5 mx-auto mb-1" />
                <div className="text-xs">Dark</div>
              </button>
              <button
                onClick={() => setTheme("system")}
                className={`p-3 rounded-lg border transition-all ${
                  theme === "system"
                    ? "bg-primary text-primary-foreground border-primary"
                    : "border-border hover:bg-accent"
                }`}
              >
                <Monitor className="w-5 h-5 mx-auto mb-1" />
                <div className="text-xs">System</div>
              </button>
            </div>
          </div>
          <div className="p-4 flex items-center justify-between">
            <div className="flex items-center gap-3">
              <Bell className="w-5 h-5 text-muted-foreground" />
              <span>Push Notifications</span>
            </div>
            <Switch defaultChecked />
          </div>
          <div className="p-4 flex items-center justify-between">
            <div className="flex items-center gap-3">
              <Target className="w-5 h-5 text-muted-foreground" />
              <span>Daily Reminders</span>
            </div>
            <Switch defaultChecked />
          </div>
          <div className="p-4 flex items-center justify-between">
            <div className="flex items-center gap-3">
              <Award className="w-5 h-5 text-muted-foreground" />
              <span>Achievement Alerts</span>
            </div>
            <Switch />
          </div>
        </Card>
      </div>

      {/* Menu Items */}
      <div>
        <h3 className="font-semibold mb-3">More</h3>
        <Card className="divide-y divide-border">
          <button className="w-full p-4 flex items-center justify-between hover:bg-accent/50 transition-colors">
            <div className="flex items-center gap-3">
              <User className="w-5 h-5 text-muted-foreground" />
              <span>Edit Profile</span>
            </div>
            <ChevronRight className="w-5 h-5 text-muted-foreground" />
          </button>
          <button className="w-full p-4 flex items-center justify-between hover:bg-accent/50 transition-colors">
            <div className="flex items-center gap-3">
              <Settings className="w-5 h-5 text-muted-foreground" />
              <span>App Settings</span>
            </div>
            <ChevronRight className="w-5 h-5 text-muted-foreground" />
          </button>
          <button className="w-full p-4 flex items-center justify-between hover:bg-accent/50 transition-colors">
            <div className="flex items-center gap-3">
              <HelpCircle className="w-5 h-5 text-muted-foreground" />
              <span>Help & Support</span>
            </div>
            <ChevronRight className="w-5 h-5 text-muted-foreground" />
          </button>
        </Card>
      </div>

      {/* Logout */}
      <Button variant="outline" className="w-full text-destructive hover:text-destructive">
        <LogOut className="w-5 h-5 mr-2" />
        Log Out
      </Button>

      {/* App Version */}
      <div className="text-center text-sm text-muted-foreground">
        Version 1.0.0
      </div>
    </div>
  );
}