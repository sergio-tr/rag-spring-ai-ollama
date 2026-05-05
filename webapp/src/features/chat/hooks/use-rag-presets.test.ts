import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClientProvider } from "@tanstack/react-query";
import { createElement, type ReactNode } from "react";
import * as apiClient from "@/lib/api-client";
import { createTestQueryClient } from "@/test-utils/query-client";
import { useRagPresets } from "./use-rag-presets";

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

describe("useRagPresets", () => {
  beforeEach(() => {
    apiFetch.mockReset();
  });

  it("loads product-facing presets from /presets without demo_worst", async () => {
    const presets = [
      {
        id: "pr1",
        name: "Demo_Best",
        description: null,
        tags: [] as string[],
        values: {},
        system: false,
        createdAt: "",
        updatedAt: "",
      },
      {
        id: "pr2",
        name: "Demo_Worst",
        description: null,
        tags: [] as string[],
        values: {},
        system: false,
        createdAt: "",
        updatedAt: "",
      },
    ];
    apiFetch.mockResolvedValueOnce(presets);
    const { wrapper } = createWrapper();
    const { result } = renderHook(() => useRagPresets(), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual([
      {
        ...presets[0],
        name: "RAG balanced",
      },
    ]);
    expect(apiFetch).toHaveBeenCalledWith(expect.stringMatching(/\/presets$/));
  });
});
