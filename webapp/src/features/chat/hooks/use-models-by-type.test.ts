import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClientProvider } from "@tanstack/react-query";
import { createElement, type ReactNode } from "react";
import * as apiClient from "@/lib/api-client";
import { createTestQueryClient } from "@/test-utils/query-client";
import { useModelsByType } from "./use-models-by-type";

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

describe("useModelsByType", () => {
  beforeEach(() => {
    apiFetch.mockReset();
  });

  it("loads models for the requested type", async () => {
    apiFetch.mockResolvedValueOnce([
      {
        modelId: "m1",
        displayName: "Model",
        type: "LLM",
        tags: [],
        available: true,
        lastCheckedAt: null,
      },
    ]);
    const { wrapper } = createWrapper();
    const { result } = renderHook(() => useModelsByType("LLM"), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.[0]?.modelId).toBe("m1");
    expect(apiFetch).toHaveBeenCalledWith(expect.stringMatching(/\/models\?type=LLM/));
  });

  it("encodes EMBEDDING type in the query string", async () => {
    apiFetch.mockResolvedValueOnce([]);
    const { wrapper } = createWrapper();
    const { result } = renderHook(() => useModelsByType("EMBEDDING"), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(apiFetch).toHaveBeenCalledWith(expect.stringMatching(/\/models\?type=EMBEDDING/));
  });
});
