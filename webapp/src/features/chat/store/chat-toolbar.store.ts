"use client";

import { create } from "zustand";
import type {
  ChatRuntimeStateDto,
  ExperimentalPresetCatalogItemDto,
  ModelsCatalogResponse,
  RagPresetDto,
} from "@/types/api";

/** Callback bundle registered by the chat page so the shell toolbar menu stays functional without duplicating UI. */
export type ChatToolbarApi = {
  projectId: string;
  conversationId: string | null;
  openDeleteForActiveConversation: () => void;
  openMoveDialog: () => void;
  openDocumentsSheet: () => void;
  onAddDocuments: (files: FileList | null) => void;
  llmModelChoice: string;
  setLlmModelChoice: (v: string) => void;
  classifierModelChoice: string;
  setClassifierModelChoice: (v: string) => void;
  modelsCatalog: ModelsCatalogResponse | undefined;
  modelsError: boolean;
  modelsErrorMessage: string;
  presetSelectValue: string;
  onPresetChange: (value: string) => void;
  presets: RagPresetDto[] | undefined;
  presetsError: boolean;
  presetsLoading: boolean;
  experimentalPresets: ExperimentalPresetCatalogItemDto[] | undefined;
  experimentalPresetsLoading: boolean;
  experimentalPresetsError: boolean;
  presetSelectDisabled: boolean;
  syntheticPresetOptionNeeded: boolean;
  presetLabelOpts: {
    systemSuffix: string;
    recommendedDefault: string;
    defaultConfiguration: string;
  };
  limitDocs: boolean;
  onLimitDocsChange: (checked: boolean) => void;
  limitDocsDisabled: boolean;
  limitDocsToggleNotice: string | null;
  patchConvPending: boolean;
  uploadPending: boolean;
  uploadError: string | null;
  uploadNotice: string | null;
  runtimeOverride: Record<string, unknown>;
  saveRuntimeOverride: (next: Record<string, unknown>) => void;
  clearRuntimeOverride: () => void;
  runtimeState: ChatRuntimeStateDto | null;
  runtimeStateLoading: boolean;
  runtimeStateError: string | null;
  refreshRuntimeState: () => void;
};

type ChatToolbarState = {
  api: ChatToolbarApi | null;
  setApi: (next: ChatToolbarApi | null) => void;
};

export const useChatToolbarStore = create<ChatToolbarState>((set) => ({
  api: null,
  setApi: (next) => set({ api: next }),
}));
