import { create } from "zustand";
import { createJSONStorage, persist } from "zustand/middleware";

export type ActiveProject = {
  id: string;
  name: string;
};

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
