import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClientProvider } from "@tanstack/react-query";
import { createElement, type ReactNode } from "react";
import * as apiClient from "@/lib/api-client";
import { createTestQueryClient } from "@/test-utils/query-client";
import { useModelsCatalog } from "./use-models-catalog";

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

describe("useModelsCatalog", () => {
  beforeEach(() => {
    apiFetch.mockReset();
  });

  it("loads models catalog from /models", async () => {
    const payload = {
      ollamaReachable: true,
      installedModelNames: ["m1"],
      allowlist: [{ name: "m1", type: "LLM" as const, inAllowlist: true, installedInOllama: true }],
    };
    apiFetch.mockResolvedValueOnce(payload);
    const { wrapper } = createWrapper();
    const { result } = renderHook(() => useModelsCatalog(), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(payload);
    expect(apiFetch).toHaveBeenCalledWith(expect.stringMatching(/\/models$/));
  });
});
