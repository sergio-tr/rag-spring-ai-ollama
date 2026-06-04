import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { ApiError, apiFetch } from "@/lib/api-client";
import type {
  EvaluationCorpusDocumentsUploadResponseDto,
  EvaluationCorpusReadinessDto,
  EvaluationCorpusSummaryDto,
} from "@/types/api";
import { useEvaluationCorpus } from "./use-evaluation-corpus";

vi.mock("@/lib/api-client", async () => {
  const actual = await vi.importActual<typeof import("@/lib/api-client")>("@/lib/api-client");
  return {
    ...actual,
    apiFetch: vi.fn(),
    apiProductPath: (p: string) => p,
  };
});

const corpus: EvaluationCorpusSummaryDto = {
  id: "corpus-1",
  name: "Lab corpus",
  sourceType: "UPLOAD",
  documentCount: 2,
  readyCount: 1,
  failedCount: 0,
  documents: [],
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

const readinessRunnable: EvaluationCorpusReadinessDto = {
  corpusId: "corpus-1",
  indexProjectId: "proj-1",
  documentCount: 2,
  readyCount: 1,
  processingCount: 0,
  failedCount: 0,
  primaryBlocker: null,
  primaryBlockerMessage: null,
  activeSnapshotId: null,
  reindexRequired: true,
  snapshotBlocker: "REINDEX_REQUIRED",
  snapshotBlockerDetailCode: "NO_ACTIVE_INDEX",
  selectedSnapshotIds: [],
  runnable: true,
};

function mockCorpusFetch(summary: EvaluationCorpusSummaryDto = corpus) {
  vi.mocked(apiFetch).mockImplementation((url: string) => {
    if (String(url).includes("/readiness")) {
      return Promise.resolve(readinessRunnable);
    }
    return Promise.resolve(summary);
  });
}

describe("useEvaluationCorpus", () => {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
  });

  function wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={qc}>{children}</QueryClientProvider>;
  }

  beforeEach(() => {
    vi.mocked(apiFetch).mockReset();
    qc.clear();
  });

  it("returns null summary when corpusId is null", () => {
    const { result } = renderHook(() => useEvaluationCorpus(null), { wrapper });
    expect(result.current.summary).toBeNull();
    expect(apiFetch).not.toHaveBeenCalled();
  });

  it("loads corpus summary when corpusId is set", async () => {
    mockCorpusFetch();
    const { result } = renderHook(() => useEvaluationCorpus("corpus-1"), { wrapper });

    await waitFor(() => expect(result.current.summary).toEqual(corpus));
    expect(apiFetch).toHaveBeenCalledWith("/lab/evaluation-corpora/corpus-1");
  });

  it("surfaces ApiError message on fetch failure", async () => {
    vi.mocked(apiFetch).mockRejectedValue(
      new ApiError(404, "The selected knowledge base does not exist", {
        kind: "http",
        safeMessage: "The selected knowledge base does not exist",
        parsedJson: { code: "KB_NOT_FOUND", message: "x" },
      }),
    );
    const { result } = renderHook(() => useEvaluationCorpus("corpus-1"), { wrapper });

    await waitFor(() => expect(result.current.errorCode).toBe("KB_NOT_FOUND"));
  });

  it("onCorpusStale clears resolved id when corpus 404", async () => {
    const onCorpusStale = vi.fn();
    vi.mocked(apiFetch).mockRejectedValue(
      new ApiError(404, "missing", {
        kind: "http",
        safeMessage: "missing",
        parsedJson: { code: "KB_NOT_FOUND" },
      }),
    );
    const { result } = renderHook(() => useEvaluationCorpus("stale-id", { onCorpusStale }), { wrapper });

    await waitFor(() => expect(onCorpusStale).toHaveBeenCalled());
    // Parent must clear draft corpusId; hook still receives prop until re-render.
  });

  it("refresh refetches by id", async () => {
    mockCorpusFetch();
    const { result } = renderHook(() => useEvaluationCorpus("corpus-1"), { wrapper });
    await waitFor(() => expect(result.current.summary).toEqual(corpus));

    const updated = { ...corpus, documentCount: 3 };
    vi.mocked(apiFetch).mockResolvedValue(updated);
    await result.current.refresh("corpus-1");
    await waitFor(() => expect(result.current.summary?.documentCount).toBe(3));
  });

  it("ensureCorpus creates when no corpusId", async () => {
    vi.mocked(apiFetch).mockResolvedValue(corpus);
    const { result } = renderHook(() => useEvaluationCorpus(null), { wrapper });

    const created = await result.current.ensureCorpus();
    expect(created).toEqual(corpus);
    expect(apiFetch).toHaveBeenCalledWith("/lab/evaluation-corpora", expect.objectContaining({ method: "POST" }));
  });

  it("ensureCorpus refreshes when corpusId already set", async () => {
    mockCorpusFetch();
    const { result } = renderHook(() => useEvaluationCorpus("corpus-1"), { wrapper });
    await waitFor(() => expect(result.current.summary).toEqual(corpus));

    await result.current.ensureCorpus();
    expect(apiFetch).toHaveBeenCalledWith("/lab/evaluation-corpora/corpus-1");
  });

  it("uploadDocuments posts multipart and merges processing row into cache", async () => {
    mockCorpusFetch();
    const { result } = renderHook(() => useEvaluationCorpus("corpus-1"), { wrapper });
    await waitFor(() => expect(result.current.summary).toEqual(corpus));

    const uploadResponse: EvaluationCorpusDocumentsUploadResponseDto = {
      corpus: { ...corpus, documentCount: 0, documents: [] },
      uploads: [{ documentId: "d1", fileName: "doc.txt", status: "PROCESSING", error: null }],
    };
    const readyCorpus = {
      ...corpus,
      documentCount: 1,
      readyCount: 1,
      documents: [
        {
          id: "d1",
          fileName: "doc.txt",
          status: "READY" as const,
          chunkCount: 1,
          errorMessage: null,
          uploadedAt: corpus.createdAt,
          reindexedAt: null,
          corpusScope: "PROJECT_SHARED" as const,
          conversationId: null,
          currentIndexSnapshotId: null,
          indexSignatureHash: null,
          storagePresent: true,
        },
      ],
    };
    vi.mocked(apiFetch).mockImplementation(async (url, init) => {
      if (init?.method === "POST" && String(url).includes("/documents")) {
        return uploadResponse;
      }
      return readyCorpus;
    });

    const file = new File(["x"], "doc.txt", { type: "text/plain" });
    const out = await result.current.uploadDocuments("corpus-1", [file]);
    expect(out.response.uploads).toHaveLength(1);
    expect(out.corpus.documents).toHaveLength(1);
    expect(out.corpus.documents[0]?.status).toBe("READY");
    expect(apiFetch).toHaveBeenCalledWith(
      "/lab/evaluation-corpora/corpus-1/documents",
      expect.objectContaining({ method: "POST" }),
    );
  });

  it("exposes effectiveCorpusId after ensureCorpus when prop is null", async () => {
    mockCorpusFetch();
    const { result } = renderHook(() => useEvaluationCorpus(null), { wrapper });

    await result.current.ensureCorpus();
    await waitFor(() => expect(result.current.effectiveCorpusId).toBe("corpus-1"));
    await waitFor(() => expect(result.current.summary).toEqual(corpus));
  });

  it("attachFromProject updates cache", async () => {
    mockCorpusFetch();
    const { result } = renderHook(() => useEvaluationCorpus("corpus-1"), { wrapper });
    await waitFor(() => expect(result.current.summary).toEqual(corpus));

    const attached = { ...corpus, readyCount: 2 };
    vi.mocked(apiFetch).mockResolvedValue(attached);
    await result.current.attachFromProject("corpus-1", "proj-1", ["d1"]);
    await waitFor(() => expect(result.current.summary?.readyCount).toBe(2));
  });
});
