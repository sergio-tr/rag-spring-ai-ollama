import type { ReactNode } from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClientProvider } from "@tanstack/react-query";
import { NextIntlClientProvider } from "next-intl";
import { LabExperimentalDatasetPanel } from "@/features/lab/components/lab-experimental-dataset-panel";
import {
  useExperimentalDatasetsQuery,
  useUploadExperimentalDatasetMutation,
} from "@/features/lab/hooks/use-experimental-datasets";
import * as experimentalDatasetsApi from "@/features/lab/lib/experimental-datasets-api";
import { createTestQueryClient } from "@/test-utils/query-client";
import { IntlTestProvider } from "@/test-utils/intl";
import es from "../../../../messages/es.json";

vi.mock("@/features/lab/lib/experimental-datasets-api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/features/lab/lib/experimental-datasets-api")>();
  return {
    ...actual,
    downloadExperimentalDatasetTemplate: vi.fn(() => Promise.resolve(new Blob(["x"]))),
    triggerBrowserBlobDownload: vi.fn(),
  };
});

vi.mock("@/features/lab/hooks/use-experimental-datasets", () => ({
  experimentalDatasetsQueryKey: ["lab", "experimental-datasets"],
  useExperimentalDatasetsQuery: vi.fn(),
  useUploadExperimentalDatasetMutation: vi.fn(),
}));

vi.mock("@/features/help/HelpPopover", () => ({
  HelpPopover: () => <button type="button">Help</button>,
}));

function TestProviders({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <QueryClientProvider client={createTestQueryClient()}>
      <IntlTestProvider>{children}</IntlTestProvider>
    </QueryClientProvider>
  );
}

describe("LabExperimentalDatasetPanel", () => {
  const mockMutateAsync = vi.fn();
  const mockReset = vi.fn();
  const mockRefetch = vi.fn();

  beforeEach(() => {
    vi.mocked(experimentalDatasetsApi.downloadExperimentalDatasetTemplate).mockClear();
    vi.mocked(experimentalDatasetsApi.triggerBrowserBlobDownload).mockClear();
    vi.mocked(useExperimentalDatasetsQuery).mockReturnValue({
      data: [],
      isLoading: false,
      isError: false,
      refetch: mockRefetch,
    } as never);
    vi.mocked(useUploadExperimentalDatasetMutation).mockReturnValue({
      mutateAsync: mockMutateAsync,
      reset: mockReset,
      isPending: false,
      isError: false,
      error: null,
    } as never);
    mockMutateAsync.mockReset();
    mockReset.mockReset();
    mockRefetch.mockReset();
  });

  it("disables upload until a file is selected", () => {
    render(<LabExperimentalDatasetPanel />, { wrapper: TestProviders });
    expect(screen.getByRole("button", { name: /Upload & validate/i })).toBeDisabled();
  });

  it("uploads selected file and shows validation summary when server accepts workbook", async () => {
    const user = userEvent.setup();
    mockMutateAsync.mockResolvedValue({
      ok: true,
      data: {
        datasetId: "550e8400-e29b-41d4-a716-446655440000",
        experimentalDatasetType: "LLM_MODEL_BASELINE",
        persistedEvaluationDatasetType: "LLM_ONLY",
        validationStatus: "VALID",
        questionCount: 2,
        rowCount: 2,
        validationReport: { issues: [], hasErrors: false, hasWarnings: false },
      },
    });

    render(<LabExperimentalDatasetPanel />, { wrapper: TestProviders });

    const file = new File([new Uint8Array([1, 2])], "bench.xlsx", {
      type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    });
    await user.upload(screen.getByLabelText(/Excel file/i), file);

    expect(screen.getByRole("button", { name: /Upload & validate/i })).toBeEnabled();

    await user.click(screen.getByRole("button", { name: /Upload & validate/i }));

    expect(mockReset).toHaveBeenCalled();
    expect(mockMutateAsync).toHaveBeenCalledWith(
      expect.objectContaining({
        file,
        datasetType: "llm-model-baseline",
      }),
    );
    expect(await screen.findByText(/Validation \(accepted\)/i)).toBeInTheDocument();
  });

  it("downloads template when a template button is clicked", async () => {
    const user = userEvent.setup();
    render(<LabExperimentalDatasetPanel />, { wrapper: TestProviders });
    await user.click(screen.getByRole("button", { name: /LLM baseline \(\.xlsx\)/i }));
    await vi.waitFor(() =>
      expect(experimentalDatasetsApi.downloadExperimentalDatasetTemplate).toHaveBeenCalledWith(
        "llm-model-baseline",
      ),
    );
    expect(experimentalDatasetsApi.triggerBrowserBlobDownload).toHaveBeenCalled();
  });

  it("shows rejected validation panel when upload outcome is not ok", async () => {
    const user = userEvent.setup();
    mockMutateAsync.mockResolvedValue({
      ok: false,
      failed: {
        error: "EXPERIMENTAL_DATASET_INVALID",
        validationReport: {
          issues: [{ code: "E", severity: "ERROR", sheet: "S1", rowNumber: 2, column: "C", message: "bad cell" }],
          hasErrors: true,
          hasWarnings: false,
        },
      },
    });
    render(<LabExperimentalDatasetPanel />, { wrapper: TestProviders });
    const file = new File([new Uint8Array([1])], "bad.xlsx", {
      type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    });
    await user.upload(screen.getByLabelText(/Excel file/i), file);
    await user.click(screen.getByRole("button", { name: /Upload & validate/i }));
    expect(await screen.findByText(/Validation \(rejected\)/i)).toBeInTheDocument();
    expect(screen.getByText(/bad cell/i)).toBeInTheDocument();
  });

  it("shows Valid badge for datasets with VALID status", () => {
    vi.mocked(useExperimentalDatasetsQuery).mockReturnValue({
      data: [
        {
          id: "550e8400-e29b-41d4-a716-446655440001",
          name: "mine",
          experimentalDatasetType: "LLM_MODEL_BASELINE",
          persistedEvaluationDatasetType: "LLM_ONLY",
          readOnly: false,
          questionCount: 3,
          rowCount: 3,
          validationStatus: "VALID",
          uploadedAt: "2026-01-01T00:00:00Z",
          description: null,
        },
      ],
      isLoading: false,
      isError: false,
      refetch: mockRefetch,
    } as never);
    render(<LabExperimentalDatasetPanel />, { wrapper: TestProviders });
    expect(screen.getByText(/^Valid$/i)).toBeInTheDocument();
  });

  it("renders Spanish Lab strings when locale is es", () => {
    render(
      <QueryClientProvider client={createTestQueryClient()}>
        <NextIntlClientProvider locale="es" messages={es}>
          <LabExperimentalDatasetPanel />
        </NextIntlClientProvider>
      </QueryClientProvider>,
    );
    expect(screen.getByText("Conjuntos de datos experimentales")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Subir y validar/i })).toBeInTheDocument();
  });
});
