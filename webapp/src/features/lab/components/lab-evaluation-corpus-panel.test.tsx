import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { LabEvaluationCorpusPanel } from "./lab-evaluation-corpus-panel";
import * as apiClient from "@/lib/api-client";

vi.mock("next-intl", () => ({
  useTranslations: () => (key: string, values?: Record<string, string | number>) => {
    if (key === "labCorpusSelectedSummary" && values) {
      return `Selected corpus: ${values.total} documents (${values.ready} ready)`;
    }
    if (key === "labCorpusUploadProgress" && values) {
      return `Uploading ${values.current}/${values.total}`;
    }
    if (key === "labCorpusUploadPartialFailed" && values) {
      return `Partial: ${values.details}`;
    }
    const map: Record<string, string> = {
      labCorpusTitle: "Evaluation documents",
      labCorpusHelp: "Attach documents for this evaluation.",
      labCorpusUploadLabel: "Upload",
      labCorpusAttachFromProject: "Use documents from project",
      labCorpusStatusProcessing: "Processing",
      labCorpusStatusFailed: "Failed",
      labCorpusStatusReady: "Ready",
      labCorpusFileTooLarge: "File too large",
      labCorpusUnsupportedType: "Unsupported type",
      labCorpusUploadFailed: "Upload failed",
      labCorpusAttachFailed: "Attach failed",
      labCorpusNoProjectDocuments: "No project documents",
    };
    return map[key] ?? key;
  },
}));

const refresh = vi.fn().mockResolvedValue(undefined);
const ensureCorpus = vi.fn().mockResolvedValue({
  id: "corpus-new",
  documentCount: 0,
  readyCount: 0,
  documents: [],
});
const uploadDocuments = vi.fn();
const attachFromProject = vi.fn().mockResolvedValue(undefined);
const deleteDocument = vi.fn().mockResolvedValue(undefined);
const deleteAllDocuments = vi.fn().mockResolvedValue(undefined);
const retryDocumentIngest = vi.fn().mockResolvedValue(undefined);
const apiFetch = vi.fn();

vi.mock("@/lib/api-client", async (importOriginal) => {
  const mod = await importOriginal<typeof import("@/lib/api-client")>();
  return {
    ...mod,
    apiFetch: (...args: unknown[]) => apiFetch(...args),
    apiProductPath: (path: string) => path,
  };
});

const useEvaluationCorpus = vi.fn();

vi.mock("@/features/lab/hooks/use-evaluation-corpus", () => ({
  useEvaluationCorpus: () => useEvaluationCorpus(),
}));

function corpusSummary(
  overrides: Partial<{
    documents: { id: string; fileName: string; status: string; errorMessage?: string | null }[];
    documentCount: number;
    readyCount: number;
  }> = {},
) {
  const documents = overrides.documents ?? [
    { id: "d1", fileName: "ready.pdf", status: "READY" },
    { id: "d2", fileName: "ingest.pdf", status: "INGESTING" },
    { id: "d3", fileName: "fail.pdf", status: "ERROR", errorMessage: "bad" },
    { id: "d4", fileName: "proc.pdf", status: "PROCESSING" },
    { id: "d5", fileName: "other.pdf", status: "QUEUED" },
  ];
  return {
    summary: {
      id: "corpus-1",
      documentCount: overrides.documentCount ?? documents.length,
      readyCount: overrides.readyCount ?? 1,
      documents,
    },
    loading: false,
    error: null,
    refresh,
    ensureCorpus,
    uploadDocuments,
    attachFromProject,
    deleteDocument,
    deleteAllDocuments,
    retryDocumentIngest,
  };
}

describe("LabEvaluationCorpusPanel", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useEvaluationCorpus.mockReturnValue(corpusSummary());
    apiFetch.mockResolvedValue([
      { id: "d1", corpusScope: "PROJECT_SHARED", fileName: "doc.pdf", status: "READY" },
    ]);
    uploadDocuments.mockResolvedValue({
      response: { uploads: [{ documentId: "d1", fileName: "ok.pdf", status: "READY", error: null }] },
      corpus: {},
    });
  });

  it("renders without requiring active project", () => {
    render(
      <LabEvaluationCorpusPanel corpusId="corpus-1" onCorpusIdChange={vi.fn()} optionalProjectId={null} />,
    );
    expect(screen.getByTestId("lab-evaluation-corpus-panel")).toBeInTheDocument();
    expect(screen.getByTestId("lab-corpus-summary")).toHaveTextContent(/5 documents/i);
    expect(screen.getByTestId("lab-corpus-upload-input")).toHaveAttribute("multiple");
    expect(screen.getByTestId("lab-corpus-doc-status-d1")).toHaveTextContent(/Ready/);
    expect(screen.getByTestId("lab-corpus-doc-status-d2")).toHaveTextContent(/Processing/);
    expect(screen.getByTestId("lab-corpus-doc-status-d3")).toHaveTextContent(/Failed \(bad\)/);
    expect(screen.getByTestId("lab-corpus-doc-status-d5")).toHaveTextContent(/QUEUED/);
  });

  it("shows attach-from-project only when optional project is set", async () => {
    const { rerender } = render(
      <LabEvaluationCorpusPanel corpusId={null} onCorpusIdChange={vi.fn()} optionalProjectId={null} />,
    );
    expect(screen.queryByTestId("lab-corpus-attach-project")).not.toBeInTheDocument();

    rerender(
      <LabEvaluationCorpusPanel corpusId={null} onCorpusIdChange={vi.fn()} optionalProjectId="p1" />,
    );
    expect(screen.getByTestId("lab-corpus-attach-project")).toBeInTheDocument();
    await userEvent.click(screen.getByTestId("lab-corpus-attach-project"));
    await waitFor(() => expect(attachFromProject).toHaveBeenCalledWith("corpus-new", "p1", ["d1"]));
  });

  it("shows error when project has no shared documents", async () => {
    apiFetch.mockResolvedValueOnce([{ id: "d9", corpusScope: "PRIVATE", fileName: "x.pdf", status: "READY" }]);
    render(
      <LabEvaluationCorpusPanel corpusId="corpus-1" onCorpusIdChange={vi.fn()} optionalProjectId="p1" />,
    );
    await userEvent.click(screen.getByTestId("lab-corpus-attach-project"));
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent(/No project documents/));
  });

  it("creates corpus on upload when corpusId is null", async () => {
    const onCorpusIdChange = vi.fn();
    render(
      <LabEvaluationCorpusPanel corpusId={null} onCorpusIdChange={onCorpusIdChange} optionalProjectId={null} />,
    );
    const input = screen.getByTestId("lab-corpus-upload-input");
    const file = new File(["x"], "sample.pdf", { type: "application/pdf" });
    await userEvent.upload(input, file);
    await waitFor(() => expect(onCorpusIdChange).toHaveBeenCalledWith("corpus-new"));
    expect(uploadDocuments).toHaveBeenCalled();
  });

  it("ignores empty file selection", async () => {
    render(
      <LabEvaluationCorpusPanel corpusId="corpus-1" onCorpusIdChange={vi.fn()} optionalProjectId={null} />,
    );
    const input = screen.getByTestId("lab-corpus-upload-input");
    fireEvent.change(input, { target: { files: [] } });
    expect(uploadDocuments).not.toHaveBeenCalled();
  });

  it("maps upload ApiError codes to user-facing messages", async () => {
    uploadDocuments.mockRejectedValueOnce(new apiClient.ApiError(413, "too big"));
    render(
      <LabEvaluationCorpusPanel corpusId="corpus-1" onCorpusIdChange={vi.fn()} optionalProjectId={null} />,
    );
    const input = screen.getByTestId("lab-corpus-upload-input");
    await userEvent.upload(input, new File(["x"], "big.pdf", { type: "application/pdf" }));
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent(/File too large/));
  });

  it("shows partial upload failure summary", async () => {
    uploadDocuments.mockResolvedValueOnce({
      response: {
        uploads: [{ documentId: null, fileName: "bad.pdf", status: "FAILED", error: "nope" }],
      },
      corpus: {},
    });
    render(
      <LabEvaluationCorpusPanel corpusId="corpus-1" onCorpusIdChange={vi.fn()} optionalProjectId={null} />,
    );
    await userEvent.upload(
      screen.getByTestId("lab-corpus-upload-input"),
      new File(["x"], "bad.pdf", { type: "application/pdf" }),
    );
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent(/Partial:.*bad\.pdf/));
  });

  it("does not upload when disabled", async () => {
    render(
      <LabEvaluationCorpusPanel corpusId="corpus-1" onCorpusIdChange={vi.fn()} optionalProjectId={null} disabled />,
    );
    const input = screen.getByTestId("lab-corpus-upload-input");
    expect(input).toBeDisabled();
    await userEvent.upload(input, new File(["x"], "x.pdf", { type: "application/pdf" }));
    expect(uploadDocuments).not.toHaveBeenCalled();
  });

  it("shows duplicate warning without error alert", async () => {
    uploadDocuments.mockResolvedValueOnce({
      response: {
        uploads: [{ documentId: "d1", fileName: "acta.txt", status: "DUPLICATE", error: "DUPLICATE_FILE" }],
      },
      corpus: {},
    });
    render(
      <LabEvaluationCorpusPanel corpusId="corpus-1" onCorpusIdChange={vi.fn()} optionalProjectId={null} />,
    );
    await userEvent.upload(
      screen.getByTestId("lab-corpus-upload-input"),
      new File(["x"], "acta.txt", { type: "text/plain" }),
    );
    await waitFor(() => expect(screen.getByTestId("lab-kb-duplicate-warning")).toBeInTheDocument());
    expect(screen.queryByTestId("lab-kb-error")).not.toBeInTheDocument();
  });

  it("deletes document via hook", async () => {
    render(
      <LabEvaluationCorpusPanel corpusId="corpus-1" onCorpusIdChange={vi.fn()} optionalProjectId={null} />,
    );
    await userEvent.click(screen.getByTestId("lab-corpus-delete-d1"));
    await waitFor(() => expect(deleteDocument).toHaveBeenCalledWith("corpus-1", "d1"));
  });

  it("surfaces hook error when present", () => {
    useEvaluationCorpus.mockReturnValue({
      ...corpusSummary(),
      error: "Corpus load failed",
    });
    render(
      <LabEvaluationCorpusPanel corpusId="corpus-1" onCorpusIdChange={vi.fn()} optionalProjectId={null} />,
    );
    expect(screen.getByRole("alert")).toHaveTextContent("Corpus load failed");
  });
});
