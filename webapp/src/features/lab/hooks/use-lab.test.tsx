import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { createTestQueryClient } from "@/test-utils/query-client";
import { useActivateClassifierModel, useClassifierModelsQuery } from "./use-classifier-registry";
import { useLabStatus } from "./use-lab-status";

vi.mock("@/lib/api-client", () => ({
  apiFetch: vi.fn(),
  apiProductPath: (p: string) => p,
}));

import { apiFetch } from "@/lib/api-client";

function wrap(qc: ReturnType<typeof createTestQueryClient>) {
  return function W({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={qc}>{children}</QueryClientProvider>;
  };
}

describe("lab hooks", () => {
  const qc = createTestQueryClient();

  beforeEach(() => vi.mocked(apiFetch).mockReset());

  it("useLabStatus fetches", async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce({
      datasets: { enabled: false, questionCount: 0 },
      evaluations: { llm: false, rag: false, classifierProxy: false },
      classifier: { configured: false, train: false, evaluate: false },
      message: "",
    });
    const { result } = renderHook(() => useLabStatus(), { wrapper: wrap(qc) });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it("useClassifierModelsQuery respects enabled", async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce([]);
    const { result } = renderHook(() => useClassifierModelsQuery(true), { wrapper: wrap(qc) });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it("activate classifier model", async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce({
      id: "m1",
      name: "x",
      inferenceTag: "t",
      status: "READY",
      trainedAt: null,
      accuracy: null,
      f1Macro: null,
      active: true,
      hyperparams: {},
    });
    const { result } = renderHook(() => useActivateClassifierModel(), { wrapper: wrap(qc) });
    await result.current.mutateAsync({
      modelId: "m1",
      body: { projectId: "p1" },
    });
  });
});
