import { Card } from "@/app/components/ui/card";
import { Progress } from "@/app/components/ui/progress";
import { Button } from "@/app/components/ui/button";
import { Activity, Target, Trophy, TrendingUp, Zap } from "lucide-react";

interface DashboardProps {
  onStartTraining: () => void;
}

export function Dashboard({ onStartTraining }: DashboardProps) {
  return (
    <div className="space-y-6 pb-6">
      {/* Welcome Header */}
      <div>
        <h1 className="text-[28px] font-semibold mb-1">Welcome back!</h1>
        <p className="text-muted-foreground">Ready to improve your game?</p>
      </div>

      {/* Quick Start */}
      <Card className="bg-gradient-to-br from-blue-600 to-blue-700 border-0 p-6">
        <div className="flex items-center justify-between mb-4">
          <div>
            <h3 className="text-white text-lg font-semibold mb-1">Start Training</h3>
            <p className="text-blue-100 text-sm">AI-guided session</p>
          </div>
          <div className="w-12 h-12 rounded-full bg-white/20 flex items-center justify-center">
            <Zap className="w-6 h-6 text-white" />
          </div>
        </div>
        <Button 
          onClick={onStartTraining}
          className="w-full bg-white text-blue-700 hover:bg-blue-50"
        >
          Begin Session
        </Button>
      </Card>

      {/* Today's Stats */}
      <div>
        <h2 className="text-lg font-semibold mb-3">Today's Progress</h2>
        <div className="grid grid-cols-2 gap-3">
          <Card className="p-4">
            <div className="flex items-center gap-2 mb-2">
              <div className="w-8 h-8 rounded-lg bg-green-500/10 flex items-center justify-center">
                <Activity className="w-4 h-4 text-green-500" />
              </div>
            </div>
            <div className="text-2xl font-semibold">23</div>
            <div className="text-sm text-muted-foreground">Minutes</div>
          </Card>

          <Card className="p-4">
            <div className="flex items-center gap-2 mb-2">
              <div className="w-8 h-8 rounded-lg bg-orange-500/10 flex items-center justify-center">
                <Target className="w-4 h-4 text-orange-500" />
              </div>
            </div>
            <div className="text-2xl font-semibold">87%</div>
            <div className="text-sm text-muted-foreground">Accuracy</div>
          </Card>

          <Card className="p-4">
            <div className="flex items-center gap-2 mb-2">
              <div className="w-8 h-8 rounded-lg bg-blue-500/10 flex items-center justify-center">
                <Trophy className="w-4 h-4 text-blue-500" />
              </div>
            </div>
            <div className="text-2xl font-semibold">156</div>
            <div className="text-sm text-muted-foreground">Rallies</div>
          </Card>

          <Card className="p-4">
            <div className="flex items-center gap-2 mb-2">
              <div className="w-8 h-8 rounded-lg bg-purple-500/10 flex items-center justify-center">
                <TrendingUp className="w-4 h-4 text-purple-500" />
              </div>
            </div>
            <div className="text-2xl font-semibold">+12%</div>
            <div className="text-sm text-muted-foreground">vs Yesterday</div>
          </Card>
        </div>
      </div>

      {/* Weekly Goal */}
      <Card className="p-5">
        <div className="flex items-center justify-between mb-3">
          <h3 className="font-semibold">Weekly Goal</h3>
          <span className="text-sm text-muted-foreground">4/7 days</span>
        </div>
        <Progress value={57} className="h-2 mb-2" />
        <p className="text-sm text-muted-foreground">
          3 more sessions to reach your weekly goal
        </p>
      </Card>

      {/* Recent Achievement */}
      <Card className="p-5 bg-gradient-to-r from-amber-500/10 to-orange-500/10 border-amber-500/20">
        <div className="flex items-start gap-3">
          <div className="w-10 h-10 rounded-full bg-amber-500/20 flex items-center justify-center flex-shrink-0">
            <Trophy className="w-5 h-5 text-amber-500" />
          </div>
          <div>
            <h4 className="font-semibold mb-1">New Achievement!</h4>
            <p className="text-sm text-muted-foreground">
              100 consecutive forehand hits unlocked
            </p>
          </div>
        </div>
      </Card>
    </div>
  );
}
