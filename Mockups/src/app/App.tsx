import { useState } from "react";
import { ThemeProvider } from "@/app/components/ThemeProvider";
import { AppSettingsProvider, useAppSettings } from "@/app/contexts/AppSettingsContext";
import { Dashboard } from "@/app/components/Dashboard";
import { TrainingSession } from "@/app/components/TrainingSession";
import { ProgressView } from "@/app/components/Progress";
import { DrillsView } from "@/app/components/Drills";
import { ProfileView } from "@/app/components/Profile";
import { SettingsView } from "@/app/components/Settings";
import { AppSettingsView } from "@/app/components/AppSettings";
import { DebugView } from "@/app/components/Debug";
import { Subscribe } from "@/app/components/Subscribe";
import { Home, TrendingUp, Target, User, Settings, Bug } from "lucide-react";

type View = "home" | "progress" | "drills" | "profile" | "settings" | "appSettings" | "debug" | "training" | "subscribe";

function AppContent() {
  const [currentView, setCurrentView] = useState<View>("home");
  const { debugMode } = useAppSettings();

  const handleStartTraining = () => {
    setCurrentView("training");
  };

  const handleEndSession = () => {
    setCurrentView("home");
  };

  const handleNavigateToSubscribe = () => {
    setCurrentView("subscribe");
  };

  const handleNavigateBack = () => {
    setCurrentView("profile");
  };

  const handleNavigateToAppSettings = () => {
    setCurrentView("appSettings");
  };

  return (
    <>
      {/* Full-screen training session view */}
      {currentView === "training" ? (
        <div className="h-screen bg-background text-foreground">
          <TrainingSession onEndSession={handleEndSession} />
        </div>
      ) : currentView === "subscribe" ? (
        <div className="h-screen bg-background text-foreground flex flex-col max-w-md mx-auto">
          <div className="flex-1 overflow-auto px-4 pt-6">
            <Subscribe onNavigateBack={handleNavigateBack} />
          </div>
        </div>
      ) : (
        <div className="h-screen bg-background text-foreground flex flex-col max-w-md mx-auto">
          {/* Main Content Area with Scroll */}
          <div className="flex-1 overflow-auto px-4 pt-6">
            {currentView === "home" && (
              <Dashboard
                onStartTraining={handleStartTraining}
                onNavigateToSubscribe={handleNavigateToSubscribe}
              />
            )}
            {currentView === "progress" && <ProgressView />}
            {currentView === "drills" && (
              <DrillsView onNavigateToSubscribe={handleNavigateToSubscribe} />
            )}
            {currentView === "profile" && (
              <ProfileView
                onNavigateToSubscribe={handleNavigateToSubscribe}
                onNavigateToAppSettings={handleNavigateToAppSettings}
              />
            )}
            {currentView === "settings" && <SettingsView />}
            {currentView === "appSettings" && <AppSettingsView />}
            {currentView === "debug" && <DebugView />}
          </div>

          {/* Bottom Navigation */}
          <nav className="border-t border-border bg-card">
            <div className="flex items-center justify-around py-2">
              <button
                onClick={() => setCurrentView("home")}
                className={`flex flex-col items-center justify-center py-2 px-3 rounded-lg transition-colors ${
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
                className={`flex flex-col items-center justify-center py-2 px-3 rounded-lg transition-colors ${
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
                className={`flex flex-col items-center justify-center py-2 px-3 rounded-lg transition-colors ${
                  currentView === "drills"
                    ? "text-blue-500"
                    : "text-muted-foreground"
                }`}
              >
                <Target className="w-6 h-6 mb-1" />
                <span className="text-xs">Drills</span>
              </button>

              {debugMode && (
                <button
                  onClick={() => setCurrentView("debug")}
                  className={`flex flex-col items-center justify-center py-2 px-3 rounded-lg transition-colors ${
                    currentView === "debug"
                      ? "text-blue-500"
                      : "text-muted-foreground"
                  }`}
                >
                  <Bug className="w-6 h-6 mb-1" />
                  <span className="text-xs">Debug</span>
                </button>
              )}

              <button
                onClick={() => setCurrentView("settings")}
                className={`flex flex-col items-center justify-center py-2 px-3 rounded-lg transition-colors ${
                  currentView === "settings"
                    ? "text-blue-500"
                    : "text-muted-foreground"
                }`}
              >
                <Settings className="w-6 h-6 mb-1" />
                <span className="text-xs">Settings</span>
              </button>

              <button
                onClick={() => setCurrentView("profile")}
                className={`flex flex-col items-center justify-center py-2 px-3 rounded-lg transition-colors ${
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
    </>
  );
}

export default function App() {
  return (
    <ThemeProvider attribute="class" defaultTheme="system" enableSystem>
      <AppSettingsProvider>
        <AppContent />
      </AppSettingsProvider>
    </ThemeProvider>
  );
}