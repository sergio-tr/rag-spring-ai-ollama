import { create } from "zustand";
import { createJSONStorage, persist } from "zustand/middleware";
import type { ProjectSummary } from "@/types/api";

export type ActiveProject = {
  id: string;
  name: string;
  iconKey?: string | null;
  colorHex?: string | null;
};

/** Normalize API project summary into persisted active-project shape (visuals optional). */
export function activeProjectFromSummary(
  p: Pick<ProjectSummary, "id" | "name" | "iconKey" | "colorHex">,
): ActiveProject {
  return {
    id: p.id,
    name: p.name,
    iconKey: p.iconKey ?? undefined,
    colorHex: p.colorHex ?? undefined,
  };
}

type AppState = {
  activeProject: ActiveProject | null;
  setActiveProject: (project: ActiveProject | null) => void;
};

export const useAppStore = create<AppState>()(
  persist(
    (set) => ({
      activeProject: null,
      setActiveProject: (activeProject) => set({ activeProject }),
    }),
    {
      name: "rag-app",
      storage: createJSONStorage(() => localStorage),
      partialize: (state) => ({ activeProject: state.activeProject }),
    },
  ),
);
