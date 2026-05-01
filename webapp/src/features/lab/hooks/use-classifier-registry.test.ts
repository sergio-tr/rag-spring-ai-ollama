import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClientProvider } from "@tanstack/react-query";
import { createElement, type ReactNode } from "react";
import * as apiClient from "@/lib/api-client";
import { createTestQueryClient } from "@/test-utils/query-client";
import { classifierModelsQueryKey, useActivateClassifierModel, useClassifierModelsQuery } from "./use-classifier-registry";

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

const entry = {
  id: "cm1",
  name: "Model",
  inferenceTag: "t",
  status: "READY",
  trainedAt: null,
  accuracy: null,
  f1Macro: null,
  active: false,
  hyperparams: {},
};

describe("use-classifier-registry hooks", () => {
  beforeEach(() => {
    apiFetch.mockReset();
  });

  it("useClassifierModelsQuery does not fetch when disabled", () => {
    const { wrapper } = createWrapper();
    const { result } = renderHook(() => useClassifierModelsQuery(false), { wrapper });
    expect(result.current.fetchStatus).toBe("idle");
    expect(apiFetch).not.toHaveBeenCalled();
  });

  it("useClassifierModelsQuery loads registry when enabled", async () => {
    apiFetch.mockResolvedValueOnce([entry]);
    const { wrapper } = createWrapper();
    const { result } = renderHook(() => useClassifierModelsQuery(true), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual([entry]);
    expect(apiFetch).toHaveBeenCalledWith(expect.stringMatching(/\/lab\/classifier\/models$/));
  });

  it("useActivateClassifierModel posts and invalidates classifier and project config queries", async () => {
    apiFetch.mockResolvedValueOnce(entry);
    const { wrapper, qc } = createWrapper();
    const invalidateSpy = vi.spyOn(qc, "invalidateQueries");
    const { result } = renderHook(() => useActivateClassifierModel(), { wrapper });
    await result.current.mutateAsync({ modelId: "cm1", body: { projectId: "p1" } });
    expect(apiFetch).toHaveBeenCalledWith(
      expect.stringMatching(/\/lab\/classifier\/models\/cm1\/activate$/),
      expect.objectContaining({ method: "POST" }),
    );
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: classifierModelsQueryKey });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["config", "project", "p1"] });
  });
});
