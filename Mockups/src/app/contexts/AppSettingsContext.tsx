import { createContext, useContext, useState, ReactNode } from "react";

interface AppSettingsContextType {
  debugMode: boolean;
  setDebugMode: (enabled: boolean) => void;
  isSubscribed: boolean;
  setIsSubscribed: (subscribed: boolean) => void;
  subscriptionEndDate: string;
}

const AppSettingsContext = createContext<AppSettingsContextType | undefined>(undefined);

export function AppSettingsProvider({ children }: { children: ReactNode }) {
  const [debugMode, setDebugMode] = useState(false);
  const [isSubscribed, setIsSubscribed] = useState(false);
  const subscriptionEndDate = "March 15, 2026";

  return (
    <AppSettingsContext.Provider
      value={{
        debugMode,
        setDebugMode,
        isSubscribed,
        setIsSubscribed,
        subscriptionEndDate,
      }}
    >
      {children}
    </AppSettingsContext.Provider>
  );
}

export function useAppSettings() {
  const context = useContext(AppSettingsContext);
  if (context === undefined) {
    throw new Error("useAppSettings must be used within an AppSettingsProvider");
  }
  return context;
}
