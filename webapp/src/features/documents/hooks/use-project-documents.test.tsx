import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import type { ProjectDocumentDto } from "@/types/api";
import { apiFetch } from "@/lib/api-client";
import {
  useDeleteAllProjectDocuments,
  useDeleteProjectDocument,
  useProjectDocuments,
  useProjectDocumentsForConversation,
  useUploadConversationOverlayDocument,
  useUploadProjectDocument,
} from "./use-project-documents";

vi.mock("@/lib/api-client", async () => {
  const actual = await vi.importActual<typeof import("@/lib/api-client")>("@/lib/api-client");
  return {
    ...actual,
    apiFetch: vi.fn(),
    apiProductPath: (p: string) => p,
  };
});

const doc = (id: string, fileName: string): ProjectDocumentDto => ({
  id,
  fileName,
  status: "READY",
  chunkCount: 1,
  errorMessage: null,
  uploadedAt: "2026-01-01T00:00:00Z",
  reindexedAt: null,
  corpusScope: "PROJECT_SHARED",
  conversationId: null,
  currentIndexSnapshotId: null,
  indexSignatureHash: null,
  storagePresent: true,
});

describe("useProjectDocuments", () => {
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

  it("does not fetch documents until projectId is defined", () => {
    const { result } = renderHook(() => useProjectDocuments(undefined), { wrapper });
    expect(result.current.isPending).toBe(true);
    expect(result.current.isFetched).toBe(false);
    expect(apiFetch).not.toHaveBeenCalled();
  });

  it("loads documents for a project id", async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce([]);
    const { result } = renderHook(() => useProjectDocuments("p1"), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(apiFetch).toHaveBeenCalledWith(expect.stringContaining("/projects/p1/documents"));
  });
});

describe("useProjectDocumentsForConversation", () => {
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

  it("does not fetch until both projectId and conversationId are defined", () => {
    const { result } = renderHook(() => useProjectDocumentsForConversation("p1", null), { wrapper });
    expect(result.current.isPending).toBe(true);
    expect(apiFetch).not.toHaveBeenCalled();
  });

  it("loads documents for a conversation including project shared docs", async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce([]);
    const { result } = renderHook(() => useProjectDocumentsForConversation("p1", "c1"), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    const url = String(vi.mocked(apiFetch).mock.calls[0]?.[0]);
    expect(url).toContain("/projects/p1/documents?");
    expect(url).toContain("conversationId=c1");
    expect(url).toContain("includeProjectShared=true");
  });
});

describe("useUploadProjectDocument", () => {
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

  it("rejects upload without project id", async () => {
    const { result } = renderHook(() => useUploadProjectDocument(undefined), { wrapper });
    const file = new File([], "a.txt");
    await expect(result.current.mutateAsync(file)).rejects.toThrow(/no_project/);
  });

  it("POSTs file and invalidates document and project lists", async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce(doc("u1", "up.txt"));
    const invalidateSpy = vi.spyOn(qc, "invalidateQueries");
    const { result } = renderHook(() => useUploadProjectDocument("p-up"), { wrapper });
    const file = new File([], "up.txt");
    await result.current.mutateAsync(file);
    expect(apiFetch).toHaveBeenCalledWith(expect.stringContaining("/projects/p-up/documents"), expect.any(Object));
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["project-documents", "p-up"] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["projects"] });
  });
});

describe("useUploadConversationOverlayDocument", () => {
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

  it("rejects upload without conversation id", async () => {
    const { result } = renderHook(() => useUploadConversationOverlayDocument("p1", null), { wrapper });
    const file = new File([], "a.txt");
    await expect(result.current.mutateAsync(file)).rejects.toThrow(/no_conversation/);
  });

  it("POSTs overlay doc and invalidates both caches", async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce(doc("u1", "up.txt"));
    const invalidateSpy = vi.spyOn(qc, "invalidateQueries");
    const { result } = renderHook(() => useUploadConversationOverlayDocument("p-up", "c-up"), { wrapper });
    const file = new File([], "up.txt");
    await result.current.mutateAsync(file);
    expect(apiFetch).toHaveBeenCalledWith(
      expect.stringContaining("/projects/p-up/conversations/c-up/documents"),
      expect.any(Object),
    );
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["project-documents", "p-up"] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["project-documents", "p-up", "conversation", "c-up"] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["projects"] });
  });
});

describe("useDeleteProjectDocument", () => {
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

  it("rejects delete without project id", async () => {
    const { result } = renderHook(() => useDeleteProjectDocument(undefined), { wrapper });
    await expect(result.current.mutateAsync("doc-x")).rejects.toThrow(/no_project/);
  });

  it("DELETEs document and invalidates caches", async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce(undefined);
    const invalidateSpy = vi.spyOn(qc, "invalidateQueries");
    const { result } = renderHook(() => useDeleteProjectDocument("p-del"), { wrapper });
    await result.current.mutateAsync("doc-1");
    expect(apiFetch).toHaveBeenCalledWith(expect.stringContaining("/projects/p-del/documents/doc-1"), {
      method: "DELETE",
    });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["project-documents", "p-del"] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["projects"] });
  });
});

describe("useDeleteAllProjectDocuments", () => {
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

  it("rejects when project id missing", async () => {
    const { result } = renderHook(() => useDeleteAllProjectDocuments(undefined), { wrapper });
    await expect(result.current.mutateAsync()).rejects.toThrow(/no_project/);
  });

  it("delete all with empty document list still refreshes caches", async () => {
    vi.mocked(apiFetch).mockResolvedValueOnce([]);
    const invalidateSpy = vi.spyOn(qc, "invalidateQueries");
    const { result } = renderHook(() => useDeleteAllProjectDocuments("p-empty"), { wrapper });
    await result.current.mutateAsync();
    expect(apiFetch).toHaveBeenCalledTimes(1);
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["project-documents", "p-empty"] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["projects"] });
  });

  it("lists documents then issues one DELETE per id for the same project only", async () => {
    vi.mocked(apiFetch)
      .mockResolvedValueOnce([doc("d1", "a.pdf"), doc("d2", "b.pdf")])
      .mockResolvedValueOnce(undefined)
      .mockResolvedValueOnce(undefined);

    const invalidateSpy = vi.spyOn(qc, "invalidateQueries");

    const { result } = renderHook(() => useDeleteAllProjectDocuments("p-scope"), { wrapper });
    await result.current.mutateAsync();

    expect(apiFetch).toHaveBeenCalledTimes(3);
    expect(String(vi.mocked(apiFetch).mock.calls[1]?.[0])).toContain("/projects/p-scope/documents/d1");
    expect(String(vi.mocked(apiFetch).mock.calls[2]?.[0])).toContain("/projects/p-scope/documents/d2");
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["project-documents", "p-scope"] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["projects"] });
  });
});
