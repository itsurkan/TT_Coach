import { Card } from "@/app/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/app/components/ui/tabs";
import { BarChart, Bar, XAxis, YAxis, ResponsiveContainer, LineChart, Line, CartesianGrid } from "recharts";
import { Calendar, Target, TrendingUp, Award } from "lucide-react";

const weeklyData = [
  { day: "Mon", minutes: 25, accuracy: 82 },
  { day: "Tue", minutes: 30, accuracy: 85 },
  { day: "Wed", minutes: 0, accuracy: 0 },
  { day: "Thu", minutes: 35, accuracy: 88 },
  { day: "Fri", minutes: 28, accuracy: 87 },
  { day: "Sat", minutes: 45, accuracy: 91 },
  { day: "Sun", minutes: 23, accuracy: 87 },
];

const skillData = [
  { skill: "Forehand", level: 85 },
  { skill: "Backhand", level: 78 },
  { skill: "Serve", level: 92 },
  { skill: "Footwork", level: 73 },
];

export function ProgressView() {
  return (
    <div className="space-y-6 pb-6">
      <div>
        <h1 className="text-[28px] font-semibold mb-1">Progress</h1>
        <p className="text-muted-foreground">Track your improvement</p>
      </div>

      {/* Stats Overview */}
      <div className="grid grid-cols-3 gap-3">
        <Card className="p-4 text-center">
          <div className="w-10 h-10 rounded-full bg-blue-500/10 flex items-center justify-center mx-auto mb-2">
            <Calendar className="w-5 h-5 text-blue-500" />
          </div>
          <div className="text-2xl font-bold">24</div>
          <div className="text-xs text-muted-foreground">Days Streak</div>
        </Card>

        <Card className="p-4 text-center">
          <div className="w-10 h-10 rounded-full bg-green-500/10 flex items-center justify-center mx-auto mb-2">
            <Target className="w-5 h-5 text-green-500" />
          </div>
          <div className="text-2xl font-bold">186</div>
          <div className="text-xs text-muted-foreground">Total Hours</div>
        </Card>

        <Card className="p-4 text-center">
          <div className="w-10 h-10 rounded-full bg-purple-500/10 flex items-center justify-center mx-auto mb-2">
            <Award className="w-5 h-5 text-purple-500" />
          </div>
          <div className="text-2xl font-bold">12</div>
          <div className="text-xs text-muted-foreground">Achievements</div>
        </Card>
      </div>

      {/* Charts */}
      <Card className="p-5">
        <Tabs defaultValue="time" className="w-full">
          <TabsList className="grid w-full grid-cols-2 mb-4">
            <TabsTrigger value="time">Training Time</TabsTrigger>
            <TabsTrigger value="accuracy">Accuracy</TabsTrigger>
          </TabsList>
          
          <TabsContent value="time" className="mt-0">
            <div className="mb-2">
              <h3 className="font-semibold">Weekly Training</h3>
              <p className="text-sm text-muted-foreground">Minutes per day</p>
            </div>
            <ResponsiveContainer width="100%" height={200}>
              <BarChart data={weeklyData}>
                <XAxis 
                  dataKey="day" 
                  tick={{ fill: 'hsl(var(--muted-foreground))', fontSize: 12 }}
                  axisLine={false}
                  tickLine={false}
                />
                <YAxis 
                  tick={{ fill: 'hsl(var(--muted-foreground))', fontSize: 12 }}
                  axisLine={false}
                  tickLine={false}
                />
                <Bar 
                  dataKey="minutes" 
                  fill="hsl(var(--chart-1))"
                  radius={[8, 8, 0, 0]}
                />
              </BarChart>
            </ResponsiveContainer>
          </TabsContent>

          <TabsContent value="accuracy" className="mt-0">
            <div className="mb-2">
              <h3 className="font-semibold">Weekly Accuracy</h3>
              <p className="text-sm text-muted-foreground">Percentage trend</p>
            </div>
            <ResponsiveContainer width="100%" height={200}>
              <LineChart data={weeklyData}>
                <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" />
                <XAxis 
                  dataKey="day" 
                  tick={{ fill: 'hsl(var(--muted-foreground))', fontSize: 12 }}
                  axisLine={false}
                  tickLine={false}
                />
                <YAxis 
                  tick={{ fill: 'hsl(var(--muted-foreground))', fontSize: 12 }}
                  axisLine={false}
                  tickLine={false}
                  domain={[70, 100]}
                />
                <Line 
                  type="monotone" 
                  dataKey="accuracy" 
                  stroke="hsl(var(--chart-2))"
                  strokeWidth={3}
                  dot={{ fill: 'hsl(var(--chart-2))', r: 4 }}
                />
              </LineChart>
            </ResponsiveContainer>
          </TabsContent>
        </Tabs>
      </Card>

      {/* Skill Breakdown */}
      <Card className="p-5">
        <div className="flex items-center gap-2 mb-4">
          <TrendingUp className="w-5 h-5 text-blue-500" />
          <h3 className="font-semibold">Skill Levels</h3>
        </div>
        <div className="space-y-4">
          {skillData.map((item) => (
            <div key={item.skill}>
              <div className="flex items-center justify-between mb-2">
                <span className="text-sm font-medium">{item.skill}</span>
                <span className="text-sm text-muted-foreground">{item.level}%</span>
              </div>
              <div className="h-2 bg-muted rounded-full overflow-hidden">
                <div
                  className="h-full bg-gradient-to-r from-blue-500 to-blue-600 transition-all"
                  style={{ width: `${item.level}%` }}
                />
              </div>
            </div>
          ))}
        </div>
      </Card>

      {/* Recent Milestones */}
      <Card className="p-5">
        <h3 className="font-semibold mb-4">Recent Milestones</h3>
        <div className="space-y-3">
          <div className="flex items-start gap-3 p-3 bg-green-500/10 rounded-lg">
            <Award className="w-5 h-5 text-green-500 flex-shrink-0 mt-0.5" />
            <div>
              <div className="font-medium text-sm">100 Consecutive Hits</div>
              <div className="text-xs text-muted-foreground">2 days ago</div>
            </div>
          </div>
          <div className="flex items-start gap-3 p-3 bg-blue-500/10 rounded-lg">
            <Award className="w-5 h-5 text-blue-500 flex-shrink-0 mt-0.5" />
            <div>
              <div className="font-medium text-sm">30-Day Streak</div>
              <div className="text-xs text-muted-foreground">5 days ago</div>
            </div>
          </div>
          <div className="flex items-start gap-3 p-3 bg-purple-500/10 rounded-lg">
            <Award className="w-5 h-5 text-purple-500 flex-shrink-0 mt-0.5" />
            <div>
              <div className="font-medium text-sm">Master Server</div>
              <div className="text-xs text-muted-foreground">1 week ago</div>
            </div>
          </div>
        </div>
      </Card>
    </div>
  );
}
