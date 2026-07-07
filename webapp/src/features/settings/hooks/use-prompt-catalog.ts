"use client";

import { useQuery } from "@tanstack/react-query";
import { apiFetch, apiProductPath } from "@/lib/api-client";

export type PromptCatalogGroup = {
  id: string;
  componentLabel: string;
  description: string;
  defaultContent: string;
  defaultSystemContent: string;
  requiredVariables: string[];
  optionalVariables: string[];
  runtimeEditable?: boolean;
};

export type PromptCatalogResponse = {
  version: number;
  groups: PromptCatalogGroup[];
  overridesMapKey: string;
};

export const promptCatalogQueryKey = ["config", "prompt-catalog"] as const;

export function usePromptCatalogQuery() {
  return useQuery({
    queryKey: promptCatalogQueryKey,
    queryFn: () => apiFetch<PromptCatalogResponse>(apiProductPath("/config/prompt-catalog")),
    staleTime: 10 * 60 * 1000,
  });
}

export type TaskLlmCatalogTask = {
  id: string;
  role?: string;
  label: string;
  inheritsMainModelByDefault: boolean;
  operationName: string;
  defaultModelId?: string;
  defaultParameters?: Record<string, unknown>;
  supportedParameters?: string[];
};

export type TaskLlmCatalogResponse = {
  version: number;
  tasks: TaskLlmCatalogTask[];
  overridesMapKey: string;
};

export const taskLlmCatalogQueryKey = ["config", "task-llm-catalog"] as const;

export function useTaskLlmCatalogQuery() {
  return useQuery({
    queryKey: taskLlmCatalogQueryKey,
    queryFn: () => apiFetch<TaskLlmCatalogResponse>(apiProductPath("/config/task-llm-catalog")),
    staleTime: 10 * 60 * 1000,
  });
}
