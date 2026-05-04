import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import {
  experimentalDatasetsQueryKey,
  useExperimentalDatasetsQuery,
  useUploadExperimentalDatasetMutation,
} from "@/features/lab/hooks/use-experimental-datasets";
import * as experimentalDatasetsApi from "@/features/lab/lib/experimental-datasets-api";

vi.mock("@/features/lab/lib/experimental-datasets-api", () => ({
  fetchExperimentalDatasets: vi.fn(),
  uploadExperimentalDatasetAllow422: vi.fn(),
}));

function createWrapper(qc: QueryClient) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={qc}>{children}</QueryClientProvider>;
  };
}

describe("useExperimentalDatasetsQuery", () => {
  beforeEach(() => {
    vi.mocked(experimentalDatasetsApi.fetchExperimentalDatasets).mockReset();
  });

  it("loads datasets via fetchExperimentalDatasets", async () => {
    vi.mocked(experimentalDatasetsApi.fetchExperimentalDatasets).mockResolvedValueOnce([
      {
        id: "550e8400-e29b-41d4-a716-446655440000",
        name: "ds",
        experimentalDatasetType: "LLM_MODEL_BASELINE",
        persistedEvaluationDatasetType: "LLM_ONLY",
        readOnly: false,
        questionCount: 1,
        rowCount: 1,
        validationStatus: "VALID",
        uploadedAt: "",
        description: null,
      },
    ]);
    const qc = new QueryClient({
      defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
    });
    const { result } = renderHook(() => useExperimentalDatasetsQuery(), {
      wrapper: createWrapper(qc),
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(1);
    expect(experimentalDatasetsApi.fetchExperimentalDatasets).toHaveBeenCalledTimes(1);
  });
});

describe("useUploadExperimentalDatasetMutation", () => {
  beforeEach(() => {
    vi.mocked(experimentalDatasetsApi.uploadExperimentalDatasetAllow422).mockReset();
  });

  it("invalidates list cache only when upload succeeds", async () => {
    const qc = new QueryClient({
      defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
    });
    const invalidateSpy = vi.spyOn(qc, "invalidateQueries");
    const file = new File([], "w.xlsx", {
      type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    });

    vi.mocked(experimentalDatasetsApi.uploadExperimentalDatasetAllow422).mockResolvedValueOnce({
      ok: true,
      data: {
        datasetId: "x",
        experimentalDatasetType: "LLM_MODEL_BASELINE",
        persistedEvaluationDatasetType: "LLM_ONLY",
        validationStatus: "VALID",
        questionCount: 1,
        rowCount: 1,
        validationReport: { issues: [], hasErrors: false, hasWarnings: false },
      },
    });

    const { result } = renderHook(() => useUploadExperimentalDatasetMutation(), {
      wrapper: createWrapper(qc),
    });
    await result.current.mutateAsync({ file, datasetType: "llm-model-baseline" });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: experimentalDatasetsQueryKey });

    invalidateSpy.mockClear();
    vi.mocked(experimentalDatasetsApi.uploadExperimentalDatasetAllow422).mockResolvedValueOnce({
      ok: false,
      failed: {
        error: "EXPERIMENTAL_DATASET_INVALID",
        validationReport: { issues: [], hasErrors: true, hasWarnings: false },
      },
    });
    await result.current.mutateAsync({ file, datasetType: "llm-model-baseline" });
    expect(invalidateSpy).not.toHaveBeenCalled();
  });
});
