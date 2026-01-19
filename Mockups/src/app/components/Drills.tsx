import { Card } from "@/app/components/ui/card";
import { Badge } from "@/app/components/ui/badge";
import { Target, Zap, Users, Timer, ChevronRight } from "lucide-react";

const drills = [
  {
    id: 1,
    name: "Forehand Drive",
    description: "Master the basic forehand stroke with consistent placement",
    difficulty: "Beginner",
    duration: "15 min",
    focus: "Technique",
    color: "blue",
    icon: Target,
  },
  {
    id: 2,
    name: "Backhand Loop",
    description: "Develop topspin control on backhand shots",
    difficulty: "Intermediate",
    duration: "20 min",
    focus: "Spin Control",
    color: "green",
    icon: Zap,
  },
  {
    id: 3,
    name: "Serve Practice",
    description: "Improve serve accuracy and variation",
    difficulty: "All Levels",
    duration: "10 min",
    focus: "Serving",
    color: "purple",
    icon: Target,
  },
  {
    id: 4,
    name: "Footwork Drill",
    description: "Enhance movement and positioning",
    difficulty: "Intermediate",
    duration: "25 min",
    focus: "Footwork",
    color: "orange",
    icon: Users,
  },
  {
    id: 5,
    name: "Multi-Ball Rally",
    description: "Fast-paced drill for reaction time",
    difficulty: "Advanced",
    duration: "30 min",
    focus: "Speed",
    color: "red",
    icon: Zap,
  },
  {
    id: 6,
    name: "Consistency Challenge",
    description: "Keep the ball in play for as long as possible",
    difficulty: "Beginner",
    duration: "15 min",
    focus: "Control",
    color: "blue",
    icon: Timer,
  },
];

const difficultyColors = {
  "Beginner": "bg-green-500/10 text-green-500 border-green-500/20",
  "Intermediate": "bg-orange-500/10 text-orange-500 border-orange-500/20",
  "Advanced": "bg-red-500/10 text-red-500 border-red-500/20",
  "All Levels": "bg-blue-500/10 text-blue-500 border-blue-500/20",
};

export function DrillsView() {
  return (
    <div className="space-y-6 pb-6">
      <div>
        <h1 className="text-[28px] font-semibold mb-1">Training Drills</h1>
        <p className="text-muted-foreground">Choose your practice routine</p>
      </div>

      {/* Featured Drill */}
      <Card className="p-5 bg-gradient-to-br from-blue-600 to-blue-700 border-0">
        <div className="flex items-start justify-between mb-3">
          <div>
            <Badge className="bg-white/20 text-white border-0 mb-2">
              Recommended
            </Badge>
            <h3 className="text-white text-lg font-semibold mb-1">
              Forehand Drive
            </h3>
            <p className="text-blue-100 text-sm">
              Perfect for building consistency
            </p>
          </div>
          <div className="w-12 h-12 rounded-full bg-white/20 flex items-center justify-center">
            <Target className="w-6 h-6 text-white" />
          </div>
        </div>
        <div className="flex items-center gap-4 text-sm text-white/80 mb-4">
          <span className="flex items-center gap-1">
            <Timer className="w-4 h-4" />
            15 min
          </span>
          <span>•</span>
          <span>Beginner</span>
        </div>
        <button className="w-full bg-white text-blue-700 hover:bg-blue-50 rounded-lg px-4 py-2.5 font-medium transition-colors">
          Start Drill
        </button>
      </Card>

      {/* Filter Pills */}
      <div className="flex gap-2 overflow-x-auto pb-2">
        <Badge variant="secondary" className="whitespace-nowrap cursor-pointer bg-primary text-primary-foreground">
          All Drills
        </Badge>
        <Badge variant="outline" className="whitespace-nowrap cursor-pointer">
          Beginner
        </Badge>
        <Badge variant="outline" className="whitespace-nowrap cursor-pointer">
          Intermediate
        </Badge>
        <Badge variant="outline" className="whitespace-nowrap cursor-pointer">
          Advanced
        </Badge>
      </div>

      {/* Drills List */}
      <div className="space-y-3">
        {drills.map((drill) => {
          const Icon = drill.icon;
          return (
            <Card
              key={drill.id}
              className="p-4 hover:bg-accent/50 transition-colors cursor-pointer"
            >
              <div className="flex items-start gap-4">
                <div className={`w-12 h-12 rounded-xl bg-${drill.color}-500/10 flex items-center justify-center flex-shrink-0`}>
                  <Icon className={`w-6 h-6 text-${drill.color}-500`} />
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-start justify-between gap-2 mb-1">
                    <h3 className="font-semibold">{drill.name}</h3>
                    <ChevronRight className="w-5 h-5 text-muted-foreground flex-shrink-0" />
                  </div>
                  <p className="text-sm text-muted-foreground mb-3 line-clamp-1">
                    {drill.description}
                  </p>
                  <div className="flex items-center gap-2 flex-wrap">
                    <Badge
                      variant="outline"
                      className={difficultyColors[drill.difficulty as keyof typeof difficultyColors]}
                    >
                      {drill.difficulty}
                    </Badge>
                    <span className="text-xs text-muted-foreground flex items-center gap-1">
                      <Timer className="w-3 h-3" />
                      {drill.duration}
                    </span>
                    <span className="text-xs text-muted-foreground">
                      {drill.focus}
                    </span>
                  </div>
                </div>
              </div>
            </Card>
          );
        })}
      </div>
    </div>
  );
}
