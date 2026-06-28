import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClientProvider } from "@tanstack/react-query";
import { createElement, type ReactNode } from "react";
import * as apiClient from "@/lib/api-client";
import { createTestQueryClient } from "@/test-utils/query-client";
import { modelRegistryQueryKey, useModelRegistryQuery } from "./use-model-registry";

vi.mock("@/lib/api-client", async (importOriginal) => {
  const mod = await importOriginal<typeof import("@/lib/api-client")>();
  return { ...mod, apiFetch: vi.fn() };
});

const apiFetch = vi.mocked(apiClient.apiFetch);

function createWrapper() {
  const qc = createTestQueryClient();
  function Wrapper({ children }: { children: ReactNode }) {
    return createElement(QueryClientProvider, { client: qc }, children);
  }
  return { wrapper: Wrapper, qc };
}

describe("useModelRegistryQuery", () => {
  beforeEach(() => {
    apiFetch.mockReset();
  });

  it("loads curated registry from /model-registry", async () => {
    const payload = {
      ollamaReachable: true,
      ollamaErrorMessage: null,
      llmModels: [
        {
          modelId: "gemma3:4b",
          modelType: "LLM" as const,
          status: "AVAILABLE" as const,
          detail: null,
          embeddingCompatible: null,
        },
      ],
      embeddingModels: [],
    };
    apiFetch.mockResolvedValueOnce(payload);
    const { wrapper } = createWrapper();
    const { result } = renderHook(() => useModelRegistryQuery(), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(payload);
    expect(apiFetch).toHaveBeenCalledWith(expect.stringMatching(/\/model-registry$/));
  });

  it("uses stable query key", () => {
    expect(modelRegistryQueryKey).toEqual(["model-registry"]);
  });
});
