import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClientProvider } from "@tanstack/react-query";
import { createElement, type ReactNode } from "react";
import * as apiClient from "@/lib/api-client";
import { createTestQueryClient } from "@/test-utils/query-client";
import { useExperimentalPresetCatalog } from "./use-experimental-preset-catalog";

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

describe("useExperimentalPresetCatalog", () => {
  beforeEach(() => {
    apiFetch.mockReset();
  });

  it("loads experimental preset catalog", async () => {
    apiFetch.mockResolvedValueOnce([
      {
        productPresetId: "p1",
        code: "P0",
        family: "baseline",
        label: "Preset 1",
        description: "",
        requiredCapabilities: [],
        supported: true,
        supportStatus: "EXECUTABLE",
        reasonIfUnsupported: null,
        requiresMultiTurn: false,
        mapsToRuntimeCapabilities: {},
        allowedOutcomes: ["EXECUTED"],
        chatSelectable: true,
        labSelectable: true,
        labOnly: false,
      },
    ]);
    const { wrapper } = createWrapper();
    const { result } = renderHook(() => useExperimentalPresetCatalog(), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.[0]?.productPresetId).toBe("p1");
    expect(apiFetch).toHaveBeenCalledWith(expect.stringMatching(/\/lab\/experimental-presets$/));
  });
});
