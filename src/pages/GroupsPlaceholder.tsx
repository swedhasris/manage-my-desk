import React from "react";
import { useLocation } from "react-router-dom";
import { Users, AlertCircle } from "lucide-react";

export function GroupsPlaceholder() {
  const location = useLocation();

  // Map route path to clean human-readable name
  const getPageTitle = (path: string) => {
    switch (path) {
      case "/groups/my-groups": return "My Groups";
      case "/groups/all-groups": return "All Groups";
      case "/groups/members": return "Group Members";
      case "/groups/activities": return "Group Activities";
      case "/groups/reports": return "Group Reports";
      case "/groups/settings": return "Group Settings";
      default: return "Groups Workspace";
    }
  };

  const title = getPageTitle(location.pathname);

  return (
    <div className="max-w-4xl mx-auto py-12 px-6">
      <div className="bg-card/50 backdrop-blur-md border border-border p-12 rounded-3xl shadow-2xl flex flex-col items-center text-center space-y-6">
        <div className="p-6 bg-blue-500/10 rounded-full border border-blue-500/20 text-blue-500">
          <Users className="w-16 h-16 animate-pulse" />
        </div>
        <div className="space-y-2">
          <h1 className="text-3xl font-black tracking-tight text-sn-dark dark:text-white">{title}</h1>
          <p className="text-muted-foreground max-w-md mx-auto">
            This module is structured for future team sync, workload planning, analytics, and messaging.
          </p>
        </div>
        <div className="flex items-center gap-2 px-4 py-2 bg-blue-50 dark:bg-blue-950/20 text-blue-600 dark:text-blue-400 border border-blue-100 dark:border-blue-900/30 rounded-xl text-xs font-semibold">
          <AlertCircle className="w-4 h-4 shrink-0" />
          Placeholder workspace loaded successfully.
        </div>
      </div>
    </div>
  );
}
