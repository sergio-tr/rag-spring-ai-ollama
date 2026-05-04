import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClientProvider } from "@tanstack/react-query";
import { createElement, type ReactNode } from "react";
import * as apiClient from "@/lib/api-client";
import { createTestQueryClient } from "@/test-utils/query-client";
import { useLabStatus } from "./use-lab-status";

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

const statusPayload = {
  datasetKindsReady: true,
  datasets: { enabled: true, datasetKindsReady: true, legacyQuestionCountDeprecated: null },
  evaluations: { llm: true, rag: true, classifierProxy: true, asyncJobs: true },
  classifier: { configured: true, train: true, evaluate: true },
  referenceBundleAvailable: true,
  referenceBundleValid: true,
  countsByDatasetKind: { llmReaderQuestions: 1, embeddingRetrievalQueries: 1, ragPresetQuestions: 1 },
  message: "ok",
};

describe("useLabStatus", () => {
  beforeEach(() => {
    apiFetch.mockReset();
  });

  it("loads lab status from /lab/status", async () => {
    apiFetch.mockResolvedValueOnce(statusPayload);
    const { wrapper } = createWrapper();
    const { result } = renderHook(() => useLabStatus(), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(statusPayload);
    expect(apiFetch).toHaveBeenCalledWith(expect.stringMatching(/\/lab\/status$/));
  });
});
