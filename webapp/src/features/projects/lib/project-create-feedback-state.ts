import { create } from "zustand";

export type ProjectCreateFeedbackPayload = {
  warning: string;
};

type ProjectCreateFeedbackStore = {
  feedback: ProjectCreateFeedbackPayload | null;
  showWarning: (warning: string) => void;
  clearFeedback: () => void;
};

export const useProjectCreateFeedbackStore = create<ProjectCreateFeedbackStore>((set) => ({
  feedback: null,
  showWarning: (warning) => set({ feedback: { warning } }),
  clearFeedback: () => set({ feedback: null }),
}));
