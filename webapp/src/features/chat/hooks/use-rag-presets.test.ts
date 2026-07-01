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

  it("loads product-facing presets from /presets with mapped system names", async () => {
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
        name: "Production assistant configuration",
      },
      {
        ...presets[1],
        name: "Basic baseline configuration",
      },
    ]);
    expect(apiFetch).toHaveBeenCalledWith(expect.stringMatching(/\/presets$/));
  });

  it("maps known demo presets to product-facing names", async () => {
    apiFetch.mockResolvedValueOnce([
      {
        id: "a",
        name: " demo_best ",
        description: null,
        tags: [] as string[],
        values: {},
        system: false,
        createdAt: "",
        updatedAt: "",
      },
      {
        id: "b",
        name: "DEMO_NAIVEFULLCORPUS",
        description: null,
        tags: [] as string[],
        values: {},
        system: false,
        createdAt: "",
        updatedAt: "",
      },
    ]);
    const { wrapper } = createWrapper();
    const { result } = renderHook(() => useRagPresets(), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.map((p) => p.name)).toEqual([
      "Production assistant configuration",
      "Full-context baseline",
    ]);
  });

  it("keeps unknown preset names untouched (no remap)", async () => {
    apiFetch.mockResolvedValueOnce([
      {
        id: "x",
        name: "My Custom Preset",
        description: null,
        tags: [] as string[],
        values: {},
        system: false,
        createdAt: "",
        updatedAt: "",
      },
    ]);
    const { wrapper } = createWrapper();
    const { result } = renderHook(() => useRagPresets(), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.[0]?.name).toBe("My Custom Preset");
  });
});
