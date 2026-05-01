import { create } from "zustand";

/**
 * Minimal session hint when the user leaves Lab UI waiting while a server job may continue.
 * Phase 6 may replace this with durable job recovery; keep bounded and in-memory only.
 */
export type LabBackgroundJobHint = Readonly<{
  jobId: string;
  stoppedWaiting: boolean;
}>;

type LabJobSessionState = {
  backgroundHint: LabBackgroundJobHint | null;
  setBackgroundHint: (hint: LabBackgroundJobHint | null) => void;
  clearBackgroundHint: () => void;
};

export const useLabJobSessionStore = create<LabJobSessionState>((set) => ({
  backgroundHint: null,
  setBackgroundHint: (hint) => set({ backgroundHint: hint }),
  clearBackgroundHint: () => set({ backgroundHint: null }),
}));
