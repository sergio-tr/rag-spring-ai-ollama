import { create } from "zustand";
import type { StreamDonePayload } from "@/types/api";

type ChatExplainState = {
  lastDone: StreamDonePayload | null;
  streamingText: string;
  isStreaming: boolean;
  setLastDone: (p: StreamDonePayload | null) => void;
  appendStreaming: (chunk: string) => void;
  /** Replace full streamed text (e.g. job poll carries full partial answer). */
  setStreamingText: (text: string) => void;
  resetStreaming: () => void;
  setStreaming: (v: boolean) => void;
};

export const useChatExplainStore = create<ChatExplainState>((set) => ({
  lastDone: null,
  streamingText: "",
  isStreaming: false,
  setLastDone: (lastDone) => set({ lastDone }),
  appendStreaming: (chunk) =>
    set((s) => ({ streamingText: s.streamingText + chunk })),
  setStreamingText: (text) => set({ streamingText: text }),
  resetStreaming: () => set({ streamingText: "" }),
  setStreaming: (isStreaming) => set({ isStreaming }),
}));
