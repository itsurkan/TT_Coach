import { useState } from "react";
import { ThemeProvider } from "@/app/components/ThemeProvider";
import { Dashboard } from "@/app/components/Dashboard";
import { TrainingSession } from "@/app/components/TrainingSession";
import { ProgressView } from "@/app/components/Progress";
import { DrillsView } from "@/app/components/Drills";
import { ProfileView } from "@/app/components/Profile";
import { Home, TrendingUp, Target, User } from "lucide-react";

type View = "home" | "progress" | "drills" | "profile" | "training";

export default function App() {
  const [currentView, setCurrentView] = useState<View>("home");

  const handleStartTraining = () => {
    setCurrentView("training");
  };

  const handleEndSession = () => {
    setCurrentView("home");
  };

  return (
    <ThemeProvider attribute="class" defaultTheme="system" enableSystem>
      {/* Full-screen training session view */}
      {currentView === "training" ? (
        <div className="h-screen bg-background text-foreground">
          <TrainingSession onEndSession={handleEndSession} />
        </div>
      ) : (
        <div className="h-screen bg-background text-foreground flex flex-col max-w-md mx-auto">
          {/* Main Content Area with Scroll */}
          <div className="flex-1 overflow-auto px-4 pt-6">
            {currentView === "home" && <Dashboard onStartTraining={handleStartTraining} />}
            {currentView === "progress" && <ProgressView />}
            {currentView === "drills" && <DrillsView />}
            {currentView === "profile" && <ProfileView />}
          </div>

          {/* Bottom Navigation */}
          <nav className="border-t border-border bg-card">
            <div className="flex items-center justify-around py-2">
              <button
                onClick={() => setCurrentView("home")}
                className={`flex flex-col items-center justify-center py-2 px-4 rounded-lg transition-colors ${
                  currentView === "home"
                    ? "text-blue-500"
                    : "text-muted-foreground"
                }`}
              >
                <Home className="w-6 h-6 mb-1" />
                <span className="text-xs">Home</span>
              </button>

              <button
                onClick={() => setCurrentView("progress")}
                className={`flex flex-col items-center justify-center py-2 px-4 rounded-lg transition-colors ${
                  currentView === "progress"
                    ? "text-blue-500"
                    : "text-muted-foreground"
                }`}
              >
                <TrendingUp className="w-6 h-6 mb-1" />
                <span className="text-xs">Progress</span>
              </button>

              <button
                onClick={() => setCurrentView("drills")}
                className={`flex flex-col items-center justify-center py-2 px-4 rounded-lg transition-colors ${
                  currentView === "drills"
                    ? "text-blue-500"
                    : "text-muted-foreground"
                }`}
              >
                <Target className="w-6 h-6 mb-1" />
                <span className="text-xs">Drills</span>
              </button>

              <button
                onClick={() => setCurrentView("profile")}
                className={`flex flex-col items-center justify-center py-2 px-4 rounded-lg transition-colors ${
                  currentView === "profile"
                    ? "text-blue-500"
                    : "text-muted-foreground"
                }`}
              >
                <User className="w-6 h-6 mb-1" />
                <span className="text-xs">Profile</span>
              </button>
            </div>
          </nav>
        </div>
      )}
    </ThemeProvider>
  );
}