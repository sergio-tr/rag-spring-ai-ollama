import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClientProvider } from "@tanstack/react-query";
import { createElement, type ReactNode } from "react";
import * as apiClient from "@/lib/api-client";
import { createTestQueryClient } from "@/test-utils/query-client";
import { useActiveLabJobs } from "./use-active-lab-jobs";

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

describe("useActiveLabJobs", () => {
  beforeEach(() => {
    apiFetch.mockReset();
  });

  it("loads active lab jobs from /lab/jobs/active", async () => {
    apiFetch.mockResolvedValueOnce([
      {
        jobId: "job-1",
        benchmarkKind: "RAG_PRESET_END_TO_END",
        evaluationRunId: "run-1",
        projectId: "proj-1",
        datasetId: "ds-1",
        status: "RUNNING",
        progress: "step 1",
        startedAt: "2026-01-01T00:00:00Z",
        updatedAt: "2026-01-01T00:00:10Z",
        pollPath: "/lab/jobs/job-1",
        streamPath: "/lab/jobs/job-1/events",
        cancellable: true,
      },
    ]);
    const { wrapper } = createWrapper();
    const { result } = renderHook(() => useActiveLabJobs(), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.[0]?.jobId).toBe("job-1");
    expect(apiFetch).toHaveBeenCalledWith(expect.stringMatching(/\/lab\/jobs\/active$/));
  });
});

