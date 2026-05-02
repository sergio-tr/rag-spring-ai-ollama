"use client";

import { create } from "zustand";
import type { ModelsCatalogResponse, RagPresetDto } from "@/types/api";

/** Callback bundle registered by the chat page so the shell toolbar menu stays functional without duplicating UI. */
export type ChatToolbarApi = {
  projectId: string;
  conversationId: string | null;
  openDeleteForActiveConversation: () => void;
  openMoveDialog: () => void;
  openDocumentsSheet: () => void;
  llmModelChoice: string;
  setLlmModelChoice: (v: string) => void;
  modelsCatalog: ModelsCatalogResponse | undefined;
  modelsError: boolean;
  modelsErrorMessage: string;
  presetSelectValue: string;
  onPresetChange: (value: string) => void;
  presets: RagPresetDto[] | undefined;
  presetsError: boolean;
  presetsLoading: boolean;
  presetSelectDisabled: boolean;
  syntheticPresetOptionNeeded: boolean;
  presetLabelOpts: {
    systemSuffix: string;
    recommendedDefault: string;
    defaultConfiguration: string;
  };
  limitDocs: boolean;
  onLimitDocsChange: (checked: boolean) => void;
  limitDocsToggleNotice: string | null;
  patchConvPending: boolean;
};

type ChatToolbarState = {
  api: ChatToolbarApi | null;
  setApi: (next: ChatToolbarApi | null) => void;
};

export const useChatToolbarStore = create<ChatToolbarState>((set) => ({
  api: null,
  setApi: (next) => set({ api: next }),
}));
