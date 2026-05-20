"use client";

import { useQuery } from "@tanstack/react-query";
import { apiFetch, apiProductPath } from "@/lib/api-client";
import type { RagPresetDto } from "@/types/api";

/** Shared with settings/presets — list visible RAG presets for the product API. */
export const ragPresetsQueryKey = ["presets"] as const;

function toProductPresetName(name: string): string {
  const n = name.trim().toLowerCase();
  if (n === "demo_best") return "RAG balanced";
  if (n === "demo_naivefullcorpus") return "RAG fast";
  if (n === "demo_worst") return "Direct LLM (experimental)";
  return name;
}

export function useRagPresets() {
  return useQuery({
    queryKey: ragPresetsQueryKey,
    queryFn: async () => {
      const raw = await apiFetch<RagPresetDto[]>(apiProductPath("/presets"));
      return raw
        .filter((p) => p.name.trim().toLowerCase() !== "demo_worst")
        .map((p) => ({ ...p, name: toProductPresetName(p.name) }));
    },
    staleTime: 30_000,
  });
}
