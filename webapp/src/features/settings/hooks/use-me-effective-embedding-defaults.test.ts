import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClientProvider } from "@tanstack/react-query";
import { createElement, type ReactNode } from "react";
import * as apiClient from "@/lib/api-client";
import { createTestQueryClient } from "@/test-utils/query-client";
import {
  meEffectiveEmbeddingDefaultsQueryKey,
  useMeEffectiveEmbeddingDefaults,
} from "./use-me-effective-embedding-defaults";

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
  return { wrapper: Wrapper };
}

describe("useMeEffectiveEmbeddingDefaults", () => {
  beforeEach(() => {
    apiFetch.mockReset();
  });

  it("loads effective embedding defaults from the product API", async () => {
    const payload = {
      effectiveProvider: "OPENAI_COMPATIBLE" as const,
      embeddingModel: "bge-m3",
      embeddingOptions: { encodingFormat: "float", dimensions: 768, timeoutSeconds: 30 },
      retrievalOptions: { topK: 10, similarityThreshold: 0.35, materializationStrategy: "CHUNK_LEVEL" },
      indexingOptions: { maxInputChars: 2048, batchSize: 16, normalize: false },
    };
    apiFetch.mockResolvedValueOnce(payload);
    const { wrapper } = createWrapper();
    const { result } = renderHook(() => useMeEffectiveEmbeddingDefaults(), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(payload);
    expect(apiFetch).toHaveBeenCalledWith(expect.stringMatching(/\/me\/embedding\/effective-defaults$/));
  });

  it("uses stable query key", () => {
    expect(meEffectiveEmbeddingDefaultsQueryKey).toEqual(["me", "embedding", "effective-defaults"]);
  });
});
