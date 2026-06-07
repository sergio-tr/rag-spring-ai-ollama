import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClientProvider } from "@tanstack/react-query";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { LabEvaluationCorpusPanel } from "./lab-evaluation-corpus-panel";
import * as apiClient from "@/lib/api-client";
import { createTestQueryClient } from "@/test-utils/query-client";
import type { ReactElement } from "react";

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
      labCorpusDocumentsAdded: "Documents added.",
      labCorpusIndexReady: "Index is ready.",
      labCorpusPrepareIndexInProgress: "Preparing index…",
      labCorpusPrepareIndexFailed: "Index preparation failed.",
      labCorpusRefresh: "Refresh",
      labEvalAddDocuments: "Add documents to run this evaluation.",
      labEvalDocumentsProcessing: "Documents are still being processed.",
      labEvalIndexWillPrepare: "The system will prepare the required index before running.",
      labEvalIndexPrepareFailed: "The system could not prepare the required index.",
      labEvalIndexPrepareFailedDetails: "Technical details",
      labCorpusHelpDocumentCentric: "Add documents for this evaluation.",
    };
    return map[key] ?? key;
  },
}));

const refreshAll = vi.fn().mockResolvedValue(undefined);
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
const prepareIndex = vi.fn().mockResolvedValue(undefined);
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

vi.mock("@/features/lab/hooks/use-evaluation-corpus", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/features/lab/hooks/use-evaluation-corpus")>();
  return {
    ...actual,
    useEvaluationCorpus: () => useEvaluationCorpus(),
  };
});

function renderPanel(ui: ReactElement) {
  return render(<QueryClientProvider client={createTestQueryClient()}>{ui}</QueryClientProvider>);
}

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
    refreshAll,
    ensureCorpus,
    uploadDocuments,
    attachFromProject,
    deleteDocument,
    deleteAllDocuments,
    retryDocumentIngest,
    prepareIndex,
    preparingIndex: false,
    effectiveCorpusId: "corpus-1",
    readiness: null as {
      reindexRequired?: boolean;
      activeSnapshotId?: string | null;
      primaryBlocker?: string | null;
      indexProjectId?: string | null;
      snapshotBlocker?: string | null;
    } | null,
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
    renderPanel(
      <LabEvaluationCorpusPanel corpusId="corpus-1" onCorpusIdChange={vi.fn()} optionalProjectId={null} />,
    );
    expect(screen.getByTestId("lab-evaluation-corpus-panel")).toBeInTheDocument();
    expect(screen.getByTestId("lab-corpus-summary")).toHaveTextContent(/5 documents/i);
    expect(screen.getByTestId("lab-corpus-upload-input")).toHaveAttribute("multiple");
    expect(screen.getByTestId("lab-corpus-doc-status-d1")).toHaveTextContent(/Ready/);
    expect(screen.getByTestId("lab-corpus-doc-status-d2")).toHaveTextContent(/Processing/);
    expect(screen.getByTestId("lab-corpus-doc-status-d3")).toHaveTextContent(/Failed \(bad\)/);
    expect(screen.getByTestId("lab-corpus-doc-status-d5")).toHaveTextContent(/queued|en cola/i);
  });

  it("shows attach-from-project only when optional project has shared docs", async () => {
    const { rerender } = renderPanel(
      <LabEvaluationCorpusPanel corpusId={null} onCorpusIdChange={vi.fn()} optionalProjectId={null} />,
    );
    expect(screen.queryByTestId("lab-corpus-attach-project")).not.toBeInTheDocument();

    useEvaluationCorpus.mockReturnValue({
      ...corpusSummary(),
      effectiveCorpusId: null,
    });
    rerender(
      <QueryClientProvider client={createTestQueryClient()}>
        <LabEvaluationCorpusPanel corpusId={null} onCorpusIdChange={vi.fn()} optionalProjectId="p1" />
      </QueryClientProvider>,
    );
    await waitFor(() => expect(screen.getByTestId("lab-corpus-attach-project")).toBeInTheDocument());
    await userEvent.click(screen.getByTestId("lab-corpus-attach-project"));
    await waitFor(() => expect(attachFromProject).toHaveBeenCalledWith("corpus-new", "p1", ["d1"]));
  });

  it("hides attach when project has no shared ready documents", async () => {
    apiFetch.mockResolvedValueOnce([{ id: "d9", corpusScope: "PRIVATE", fileName: "x.pdf", status: "READY" }]);
    renderPanel(
      <LabEvaluationCorpusPanel corpusId="corpus-1" onCorpusIdChange={vi.fn()} optionalProjectId="p1" />,
    );
    await waitFor(() =>
      expect(screen.getByTestId("lab-corpus-attach-unavailable-hint")).toBeInTheDocument(),
    );
    expect(screen.queryByTestId("lab-corpus-attach-project")).not.toBeInTheDocument();
  });

  it("shows prepare index when snapshot is required", () => {
    useEvaluationCorpus.mockReturnValue({
      ...corpusSummary({ readyCount: 1 }),
      readiness: {
        reindexRequired: true,
        activeSnapshotId: null,
        primaryBlocker: null,
        snapshotBlocker: "REINDEX_REQUIRED",
        indexProjectId: "idx-proj",
      },
    });
    renderPanel(
      <LabEvaluationCorpusPanel corpusId="corpus-1" onCorpusIdChange={vi.fn()} optionalProjectId={null} />,
    );
    expect(screen.getByTestId("lab-corpus-prepare-index")).toBeInTheDocument();
    expect(screen.getByTestId("lab-corpus-snapshot-hint")).toBeInTheDocument();
  });

  it("creates corpus on upload when corpusId is null", async () => {
    const onCorpusIdChange = vi.fn();
    useEvaluationCorpus.mockReturnValue({
      ...corpusSummary(),
      effectiveCorpusId: null,
    });
    renderPanel(
      <LabEvaluationCorpusPanel corpusId={null} onCorpusIdChange={onCorpusIdChange} optionalProjectId={null} />,
    );
    const input = screen.getByTestId("lab-corpus-upload-input");
    const file = new File(["x"], "sample.pdf", { type: "application/pdf" });
    await userEvent.upload(input, file);
    await waitFor(() => expect(onCorpusIdChange).toHaveBeenCalledWith("corpus-new"));
    expect(uploadDocuments).toHaveBeenCalled();
  });

  it("ignores empty file selection", async () => {
    renderPanel(
      <LabEvaluationCorpusPanel corpusId="corpus-1" onCorpusIdChange={vi.fn()} optionalProjectId={null} />,
    );
    const input = screen.getByTestId("lab-corpus-upload-input");
    fireEvent.change(input, { target: { files: [] } });
    expect(uploadDocuments).not.toHaveBeenCalled();
  });

  it("maps upload ApiError codes to user-facing messages", async () => {
    uploadDocuments.mockRejectedValueOnce(new apiClient.ApiError(413, "too big"));
    renderPanel(
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
    renderPanel(
      <LabEvaluationCorpusPanel corpusId="corpus-1" onCorpusIdChange={vi.fn()} optionalProjectId={null} />,
    );
    await userEvent.upload(
      screen.getByTestId("lab-corpus-upload-input"),
      new File(["x"], "bad.pdf", { type: "application/pdf" }),
    );
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent(/Partial:.*bad\.pdf/));
  });

  it("does not upload when disabled", async () => {
    renderPanel(
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
    renderPanel(
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
    renderPanel(
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
    renderPanel(
      <LabEvaluationCorpusPanel corpusId="corpus-1" onCorpusIdChange={vi.fn()} optionalProjectId={null} />,
    );
    expect(screen.getByRole("alert")).toHaveTextContent("Corpus load failed");
  });

  it("refresh button refetches all corpus state including attachable project docs", async () => {
    renderPanel(
      <LabEvaluationCorpusPanel corpusId="corpus-1" onCorpusIdChange={vi.fn()} optionalProjectId="p1" />,
    );
    await userEvent.click(screen.getByTestId("lab-corpus-refresh"));
    await waitFor(() =>
      expect(refreshAll).toHaveBeenCalledWith("corpus-1", { invalidateAttachableProjectId: "p1" }),
    );
  });

  it("shows documents added after successful upload", async () => {
    renderPanel(
      <LabEvaluationCorpusPanel corpusId="corpus-1" onCorpusIdChange={vi.fn()} optionalProjectId={null} />,
    );
    await userEvent.upload(
      screen.getByTestId("lab-corpus-upload-input"),
      new File(["x"], "ok.pdf", { type: "application/pdf" }),
    );
    await waitFor(() => expect(screen.getByTestId("lab-corpus-success")).toHaveTextContent("Documents added."));
  });

  it("prepare index shows progress and success message", async () => {
    useEvaluationCorpus.mockReturnValue({
      ...corpusSummary({ readyCount: 1 }),
      readiness: {
        reindexRequired: true,
        activeSnapshotId: null,
        primaryBlocker: null,
        snapshotBlocker: "REINDEX_REQUIRED",
        indexProjectId: "idx-proj",
      },
      prepareIndex: vi.fn().mockResolvedValue({
        readiness: { reindexRequired: false, activeSnapshotId: "snap-1" },
      }),
    });
    renderPanel(
      <LabEvaluationCorpusPanel corpusId="corpus-1" onCorpusIdChange={vi.fn()} optionalProjectId={null} />,
    );
    await userEvent.click(screen.getByTestId("lab-corpus-prepare-index"));
    await waitFor(() => expect(screen.getByTestId("lab-corpus-success")).toHaveTextContent("Index is ready."));
  });

  it("document-centric mode hides project attach and prepare index UI", async () => {
    useEvaluationCorpus.mockReturnValue({
      ...corpusSummary({ readyCount: 1 }),
      readiness: {
        reindexRequired: true,
        activeSnapshotId: null,
        primaryBlocker: null,
        snapshotBlocker: "INDEX_PREPARATION_REQUIRED",
        runnable: true,
      },
    });
    renderPanel(
      <LabEvaluationCorpusPanel
        corpusId="corpus-1"
        onCorpusIdChange={vi.fn()}
        optionalProjectId="p1"
        documentCentric
      />,
    );
    expect(screen.queryByTestId("lab-corpus-attach-project")).not.toBeInTheDocument();
    expect(screen.queryByTestId("lab-corpus-import-hint")).not.toBeInTheDocument();
    expect(screen.queryByTestId("lab-corpus-prepare-index")).not.toBeInTheDocument();
    expect(screen.queryByTestId("lab-corpus-attach-unavailable-hint")).not.toBeInTheDocument();
    expect(screen.getByTestId("lab-corpus-index-will-prepare")).toHaveTextContent(
      /prepare the required index/i,
    );
    expect(screen.queryByText(/INDEX_PREPARATION_REQUIRED/)).not.toBeInTheDocument();
  });

  it("document-centric mode shows add-documents message when empty", () => {
    useEvaluationCorpus.mockReturnValue({
      ...corpusSummary({ documentCount: 0, readyCount: 0, documents: [] }),
      readiness: {
        primaryBlocker: "NO_DOCUMENTS",
        runnable: false,
        documentCount: 0,
        readyCount: 0,
      },
    });
    renderPanel(
      <LabEvaluationCorpusPanel
        corpusId="corpus-1"
        onCorpusIdChange={vi.fn()}
        documentCentric
      />,
    );
    expect(screen.getByTestId("lab-corpus-readiness-blocker")).toHaveTextContent(
      /Add documents to run this evaluation/i,
    );
  });

  it("refresh invokes onRefreshed callback", async () => {
    const onRefreshed = vi.fn();
    renderPanel(
      <LabEvaluationCorpusPanel
        corpusId="corpus-1"
        onCorpusIdChange={vi.fn()}
        onRefreshed={onRefreshed}
      />,
    );
    await userEvent.click(screen.getByTestId("lab-corpus-refresh"));
    await waitFor(() => expect(onRefreshed).toHaveBeenCalled());
  });
});
